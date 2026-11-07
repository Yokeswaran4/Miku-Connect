// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Yokeswaran N
package com.yokeswaran.yconnect

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.java_websocket.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.concurrent.thread

/** A command the user keeps for later. */
data class SavedCommand(
    val id: String,
    val label: String,
    val text: String,
    val shell: String = "default",   // "default" | "powershell" (Windows only)
)

/** Plain-language reason and remedy that go with a failure. */
data class RunError(val kind: String, val why: String, val fix: String)

/** status: running | done | failed */
data class RunEntry(
    val rid: String,
    val peerId: String,
    val peer: String,
    val text: String,
    val status: String,
    val code: Int = 0,
    val stdout: String = "",
    val stderr: String = "",
    val truncated: Boolean = false,
    val ms: Long = 0,
    val error: RunError? = null,
)

/** An open (or just closed) terminal on a computer. status: opening | open | closed. [text] is everything shown so far. */
data class TermState(
    val sid: String,
    val peerId: String,
    val peer: String,
    val shell: String,
    val status: String,
    val text: String = "",
    val error: RunError? = null,
)

/**
 * Sends commands to a connected computer and shows what came back. Removing files is not supported:
 * delete commands are refused here before anything is sent, and the computer refuses them as well.
 * The computer does the work (see desktop/remote.js) and answers with `cmd-result`;
 * when something fails the answer carries the reason and how to fix it. Problems that can only be
 * noticed on this side (not connected, no answer, connection lost) are explained here.
 *
 * Protocol: cmd-run {rid,command,shell,timeout} | cmd-cancel {rid}  ->  cmd-result {rid,ok,code,stdout,stderr,truncated,ms,error{kind,why,fix}}
 */
object RemoteCommands {
    val term = MutableStateFlow<TermState?>(null)
    val termHistory = MutableStateFlow<List<String>>(emptyList())
    private const val MAX_TERM_CHARS = 300_000
    private const val MAX_LINE = 16_000
    private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B[()][A-Z0-9]|\u001B[=>]")

    val saved = MutableStateFlow<List<SavedCommand>>(emptyList())
    val recent = MutableStateFlow<List<SavedCommand>>(emptyList())
    val runs = MutableStateFlow<List<RunEntry>>(emptyList())

    private const val MAX_RUNS = 20
    private const val MAX_RECENT = 8
    private var prefs: android.content.SharedPreferences? = null

    fun load(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("yconnect", Context.MODE_PRIVATE)
        prefs = p
        saved.value = decode(p.getString("saved_cmds", null))
        recent.value = decode(p.getString("recent_cmds", null))
    }

    /* ---------- saved & recent ---------- */
    private fun decode(s: String?): List<SavedCommand> {
        if (s.isNullOrEmpty()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString("text")
                if (text.isEmpty() || o.optString("kind") == "rm") return@mapNotNull null // removals saved by an older version are dropped
                SavedCommand(
                    o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    o.optString("label").ifEmpty { text.take(24) },
                    text, o.optString("shell", "default")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun encode(l: List<SavedCommand>): String {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("label", it.label)
                .put("text", it.text).put("shell", it.shell))
        }
        return a.toString()
    }

    private fun persist() {
        prefs?.edit()?.putString("saved_cmds", encode(saved.value))?.putString("recent_cmds", encode(recent.value))?.apply()
    }

    @Synchronized
    fun save(label: String, text: String, shell: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val name = label.trim().ifEmpty { t.take(24) }
        // saving the same thing again just renames it
        val same = saved.value.firstOrNull { it.text == t && it.shell == shell }
        saved.value = if (same != null) saved.value.map { if (it.id == same.id) it.copy(label = name) else it }
        else saved.value + SavedCommand(UUID.randomUUID().toString(), name, t, shell)
        persist()
    }

    @Synchronized
    fun delete(id: String) {
        saved.value = saved.value.filter { it.id != id }
        persist()
    }

    @Synchronized
    private fun addRecent(text: String, shell: String) {
        val c = SavedCommand(UUID.randomUUID().toString(), text.take(24), text, shell)
        recent.value = (listOf(c) + recent.value.filterNot { it.text == text }).take(MAX_RECENT)
        persist()
    }

    @Synchronized
    fun clearRecent() { recent.value = emptyList(); persist() }

    /* ---------- running ---------- */
    private fun short() = UUID.randomUUID().toString().replace("-", "").take(12)

    @Synchronized
    private fun put(e: RunEntry) { runs.value = (listOf(e) + runs.value).take(MAX_RUNS) }

    @Synchronized
    private fun update(rid: String, f: (RunEntry) -> RunEntry) {
        runs.value = runs.value.map { if (it.rid == rid) f(it) else it }
    }

    @Synchronized
    fun clearRuns() { runs.value = runs.value.filter { it.status == "running" } }

    private fun failNow(peer: Peer, text: String, e: RunError) =
        put(RunEntry(short(), peer.id, peer.name, text, "failed", -1, error = e))

    /* ---------- guard rails (the computer checks the same things, so this is a second lock) ---------- */
    private const val SEP = "(?:^|[\\s;&|(`'\"])"
    private const val END = "(?=$|[\\s;&|)`'\"])"
    private val ELEVATE = Regex(SEP + "(sudo|su|doas|pkexec|runas|gsudo)(?:\\.exe)?" + END, RegexOption.IGNORE_CASE)
    private val DELETE = Regex(SEP + "(rm|rmdir|unlink|shred|srm|rimraf|trash|trash-put|del|erase|rd|remove-item|ri|clear-recyclebin)(?:\\.exe)?" + END, RegexOption.IGNORE_CASE)
    private val DELETE_FIND = Regex("\\bfind\\b[^\\n]*\\s-delete\\b|\\s-exec\\s+(?:rm|shred)\\b", RegexOption.IGNORE_CASE)

    /** Why a command must not be sent, or null when it may go. Best-effort guard rail, not a sandbox. */
    fun blockReason(cmd: String): RunError? {
        DELETE.find(cmd)?.let { return deleteError(it.groupValues[1]) }
        if (DELETE_FIND.containsMatchIn(cmd)) return deleteError("find -delete")
        ELEVATE.find(cmd)?.let {
            return RunError("elevation", "\"${it.groupValues[1]}\" asks for administrator / root rights. Commands only run as a normal user, because nobody can type a password remotely.",
                "Remove the elevation part, or run this command directly on the computer.")
        }
        return null
    }

    private fun deleteError(what: String) = RunError("removal-blocked",
        "The command contains \"$what\", which deletes files. Deleting files from the phone is not allowed.",
        "Delete files directly on the computer, or use a command that does not remove anything.")

    /** Sends the request. The result arrives later in [runs]. */
    fun run(peer: Peer, text: String, shell: String = "default", timeoutSec: Int = 30) {
        val t = text.trim()
        if (t.isEmpty()) return
        addRecent(t, shell)
        blockReason(t)?.let { failNow(peer, t, it); Hub.addLog("warn", "Command not sent", t.replace(Regex("\\s+"), " ").take(60)); return }
        val conn = Hub.byId[peer.id]
        if (conn == null || !conn.isOpen) {
            failNow(peer, t, RunError("not-connected",
                "${peer.name} is not connected right now, so the request could not be sent.",
                "Open Y Connect on ${peer.name}, make sure both devices are on the same Wi-Fi, and wait until it shows as connected on the Devices tab."))
            return
        }
        val rid = short()
        put(RunEntry(rid, peer.id, peer.name, t, "running"))
        val msg = JSONObject().put("rid", rid).put("type", "cmd-run").put("command", t).put("shell", shell).put("timeout", timeoutSec)
        Hub.sendJson(conn, msg)
        Hub.addLog("cmd", "Command sent to ${peer.name}", t.replace(Regex("\\s+"), " ").take(60))
        // if nothing comes back the computer is probably an older version, asleep, or the link dropped
        thread(isDaemon = true, name = "yconnect-cmd-watch") {
            try { Thread.sleep((timeoutSec + 15) * 1000L) } catch (_: InterruptedException) { return@thread }
            if (runs.value.any { it.rid == rid && it.status == "running" }) {
                update(rid) {
                    it.copy(status = "failed", code = -1, error = RunError("no-reply",
                        "${peer.name} did not answer in time. Usual causes: it is an older Y Connect version without remote commands (old versions silently ignore the request), the computer went to sleep, or the Wi-Fi dropped.",
                        "Update Y Connect on ${peer.name} to this version, wake it up, check the Wi-Fi, then try again."))
                }
            }
        }
    }

    fun cancel(rid: String) {
        val e = runs.value.firstOrNull { it.rid == rid && it.status == "running" } ?: return
        val conn = Hub.byId[e.peerId] ?: return
        Hub.sendJson(conn, JSONObject().put("type", "cmd-cancel").put("rid", rid))
    }

    /* ---------- interactive terminal ---------- */
    private fun clean(s: String) = ANSI.replace(s, "").replace("\r\n", "\n").replace("\r", "")

    @Synchronized
    private fun termAppend(sid: String, text: String) {
        val t = term.value ?: return
        if (t.sid != sid || text.isEmpty()) return
        var n = t.text + text
        if (n.length > MAX_TERM_CHARS) n = n.takeLast(MAX_TERM_CHARS * 3 / 4)
        term.value = t.copy(text = n)
    }

    @Synchronized
    private fun termUpdate(sid: String, f: (TermState) -> TermState) {
        val t = term.value ?: return
        if (t.sid == sid) term.value = f(t)
    }

    /** Opens a terminal: one shell on the computer that stays open, so cd, variables and running programs carry over. */
    fun termOpen(peer: Peer, shell: String) {
        termClose()
        val conn = Hub.byId[peer.id]
        val sid = short()
        if (conn == null || !conn.isOpen) {
            term.value = TermState(sid, peer.id, peer.name, "", "closed", error = RunError("not-connected",
                "${peer.name} is not connected right now.", "Wait until it shows as connected on the Devices tab, then open the terminal again."))
            return
        }
        term.value = TermState(sid, peer.id, peer.name, "", "opening")
        Hub.sendJson(conn, JSONObject().put("type", "cmd-term-open").put("sid", sid).put("shell", shell))
        Hub.addLog("cmd", "Terminal opened on ${peer.name}")
        thread(isDaemon = true, name = "yconnect-term-watch") {
            try { Thread.sleep(15_000) } catch (_: InterruptedException) { return@thread }
            termUpdate(sid) {
                if (it.status != "opening") it else it.copy(status = "closed", error = RunError("no-reply",
                    "${peer.name} did not answer. It is probably an older Y Connect version without the terminal, asleep, or off the Wi-Fi.",
                    "Update Y Connect on ${peer.name} to this version, wake it up, check the Wi-Fi, then try again."))
            }
        }
    }

    /** Sends one typed line. The shell does not echo it, so it is shown here right after the prompt. */
    fun termSend(line: String) {
        val t = term.value ?: return
        if (t.status != "open") return
        val l = line.trimEnd('\n', '\r')
        if (l.length > MAX_LINE) { termAppend(t.sid, "[refused: the line is longer than $MAX_LINE characters]\n"); return }
        if (l.isNotBlank()) termHistory.value = (listOf(l) + termHistory.value.filterNot { it == l }).take(50)
        termAppend(t.sid, l + "\n")
        blockReason(l)?.let { termAppend(t.sid, "[refused: ${it.why}]\n"); return }
        val conn = Hub.byId[t.peerId] ?: return
        Hub.sendJson(conn, JSONObject().put("type", "cmd-term-input").put("sid", t.sid).put("d", l + "\n"))
    }

    /** Ctrl+C: stops the program that is running in the terminal, the shell itself stays. */
    fun termInterrupt() {
        val t = term.value ?: return
        val conn = Hub.byId[t.peerId] ?: return
        termAppend(t.sid, "^C\n")
        Hub.sendJson(conn, JSONObject().put("type", "cmd-term-int").put("sid", t.sid))
    }

    fun termClear() { term.value?.let { term.value = it.copy(text = "") } }

    fun termClose() {
        val t = term.value ?: return
        if (t.status != "closed") Hub.byId[t.peerId]?.let { Hub.sendJson(it, JSONObject().put("type", "cmd-term-close").put("sid", t.sid)) }
        term.value = null
    }

    /** A result (or a request, which phones never run) came in from [conn]. */
    fun onMessage(conn: WebSocket, j: JSONObject) {
        val rid = j.optString("rid")
        when (j.optString("type")) {
            "cmd-term-ready" -> {
                val from = Hub.connInfo[conn]?.id ?: return
                val sid = j.optString("sid")
                if (term.value?.peerId == from) termUpdate(sid) { it.copy(status = "open", shell = j.optString("shell"), text = "Connected to ${it.peer} (${j.optString("shell")}). Ctrl+C stops a running program. Nothing here can use sudo or delete files.\n") }
            }
            "cmd-term-data" -> {
                val from = Hub.connInfo[conn]?.id ?: return
                if (term.value?.peerId == from) termAppend(j.optString("sid"), clean(j.optString("d")))
            }
            "cmd-term-exit" -> {
                val from = Hub.connInfo[conn]?.id ?: return
                val err = j.optJSONObject("error")?.let { RunError(it.optString("kind"), it.optString("why"), it.optString("fix")) }
                if (term.value?.peerId == from) {
                    termAppend(j.optString("sid"), if (err == null) "\n[Terminal closed, exit code ${j.optInt("code")}]\n" else "")
                    termUpdate(j.optString("sid")) { it.copy(status = "closed", error = err) }
                }
            }
            "cmd-result" -> {
                val from = Hub.connInfo[conn]?.id ?: return
                val e = runs.value.firstOrNull { it.rid == rid && it.peerId == from } ?: return
                val err = j.optJSONObject("error")?.let {
                    RunError(it.optString("kind"), it.optString("why"), it.optString("fix"))
                }
                val ok = j.optBoolean("ok") && err == null
                update(e.rid) {
                    it.copy(
                        status = if (ok) "done" else "failed", code = j.optInt("code", 0),
                        stdout = j.optString("stdout"), stderr = j.optString("stderr"),
                        truncated = j.optBoolean("truncated"), ms = j.optLong("ms"),
                        error = if (ok) null else (err ?: RunError("exit-code", "The command finished with exit code ${j.optInt("code")}.", "Read the error output above and adjust the command."))
                    )
                }
            }
            "cmd-term-open" -> {
                Hub.sendJson(conn, JSONObject().put("type", "cmd-term-exit").put("sid", j.optString("sid")).put("code", -1)
                    .put("error", JSONObject().put("kind", "sender").put("why", "Android phones do not open terminals for other devices.").put("fix", "Open the terminal from the phone to a Linux or Windows computer instead.")))
            }
            "cmd-run", "cmd-rm" -> if (rid.isNotEmpty()) {
                // phones do not run commands or remove files
                Hub.sendJson(conn, JSONObject().put("type", "cmd-result").put("rid", rid).put("ok", false).put("code", -1)
                    .put("error", JSONObject().put("kind", "sender")
                        .put("why", "Android phones do not run commands or remove files for other devices.")
                        .put("fix", "Send commands from the phone to a Linux or Windows computer instead.")))
            }
        }
    }

    /** The connection to a computer ended while requests were still waiting. */
    fun onDisconnected(conn: WebSocket) {
        val id = Hub.connInfo[conn]?.id ?: return
        term.value?.let { t ->
            if (t.peerId == id && t.status != "closed") {
                termAppend(t.sid, "\n[Connection lost]\n")
                termUpdate(t.sid) { it.copy(status = "closed", error = RunError("disconnected",
                    "The connection to ${it.peer} was lost, so the terminal was closed there too.",
                    "Wait until ${it.peer} shows as connected again, then open the terminal again.")) }
            }
        }
        runs.value.filter { it.peerId == id && it.status == "running" }.forEach { e ->
            update(e.rid) {
                it.copy(status = "failed", code = -1, error = RunError("disconnected",
                    "The connection to ${it.peer} was lost before it answered. The command may or may not have run.",
                    "Wait until ${it.peer} shows as connected again (same Wi-Fi, Y Connect open), check the output on the computer if it matters, then run it again."))
            }
        }
    }
}
