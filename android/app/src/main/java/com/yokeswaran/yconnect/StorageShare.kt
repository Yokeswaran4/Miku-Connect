// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Yokeswaran N
package com.yokeswaran.yconnect

import android.os.Build
import android.os.Environment
import android.util.Base64
import org.java_websocket.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Lets a paired computer mount this phone's storage (see desktop/storage.js).
 *
 * Requests arrive as `fs-*` messages on the same encrypted link as everything else and only from a connected computer.
 * Two switches on this phone decide what is possible, both OFF until the user turns them on in Settings:
 *   - Share phone storage  -> the computer may browse and read files
 *   - Allow changes        -> the computer may also write, create folders, rename and delete
 * Android additionally needs the "All files access" permission. Nothing outside the phone's shared storage is reachable,
 * and every path is checked so it cannot leave that folder (no "..", no symbolic-link tricks).
 *
 *   fs-info | fs-stat {path} | fs-list {path} | fs-read {path, off, len}
 *   fs-write {path, off, data(base64), trunc} | fs-mkdir {path} | fs-del {path} | fs-move {from, to}
 *   answer: fs-res {rid, ok, ...} or fs-res {rid, ok:false, err, why}   (err: disabled readonly denied notfound exists badpath io)
 */
object StorageShare {
    private const val MAX_READ = 512 * 1024
    private const val MAX_WRITE = 1024 * 1024
    private const val MAX_LIST = 5000
    private val pool = Executors.newFixedThreadPool(4) { r -> Thread(r, "yconnect-fs").apply { isDaemon = true } }
    private val announced: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** "All files access" (Android 11+). */
    fun hasAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 30

    private fun root(): File = Environment.getExternalStorageDirectory()

    private fun ok() = JSONObject().put("ok", true)
    private fun err(code: String, why: String) = JSONObject().put("ok", false).put("err", code).put("why", why)

    /** Maps a path sent by the computer to a file inside the shared storage, or null when it would leave it. */
    private fun resolve(rel: String): File? {
        val parts = rel.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == ".." || it == "." || it.contains('\u0000') || it.contains('\\') }) return null
        return try {
            val base = root().canonicalFile
            val f = File(base, parts.joinToString("/")).canonicalFile
            if (f.path == base.path || f.path.startsWith(base.path + File.separator)) f else null
        } catch (_: Exception) { null }
    }

    fun onDisconnected(peerId: String) { announced.remove(peerId) }

    fun onMessage(conn: WebSocket, j: JSONObject) {
        val peer = Hub.connInfo[conn] ?: return
        if (peer.platform.equals("android", ignoreCase = true)) return // only computers mount a phone
        val rid = j.optString("rid")
        if (rid.isEmpty()) return
        pool.execute {
            val r = try { handle(j, peer) } catch (e: SecurityException) { err("denied", "Android refused access to that file.") }
            catch (e: Exception) { err("io", e.message ?: e.javaClass.simpleName) }
            Hub.sendJson(conn, r.put("type", "fs-res").put("rid", rid))
        }
    }

    private fun handle(j: JSONObject, peer: Peer): JSONObject {
        val s = Hub.settings.value
        if (!s.shareStorage) return err("disabled", "Storage sharing is off on the phone.")
        if (!hasAccess()) return err("disabled", "Y Connect has no “All files access” on the phone yet.")
        val op = j.optString("type")
        val writing = op == "fs-write" || op == "fs-mkdir" || op == "fs-del" || op == "fs-move"
        if (writing && !s.storageWrite) return err("readonly", "Changes are not allowed. Switch on “Allow changes” on the phone.")
        return when (op) {
            "fs-info" -> {
                if (announced.add(peer.id)) Hub.addLog("link", "Storage opened by", peer.name)
                val r = root()
                ok().put("free", r.usableSpace).put("total", r.totalSpace)
            }
            "fs-stat" -> {
                val f = resolve(j.optString("path")) ?: return err("badpath", "Bad path")
                if (!f.exists()) return err("notfound", "Not found")
                ok().put("st", JSONObject().put("d", f.isDirectory).put("s", if (f.isDirectory) 0L else f.length()).put("m", f.lastModified()))
            }
            "fs-list" -> {
                val f = resolve(j.optString("path")) ?: return err("badpath", "Bad path")
                if (!f.exists()) return err("notfound", "Not found")
                if (!f.isDirectory) return err("io", "Not a folder")
                val kids = f.listFiles() ?: return err("denied", "Android does not allow listing this folder.")
                val arr = JSONArray()
                for (k in kids.take(MAX_LIST)) arr.put(JSONObject().put("n", k.name).put("d", k.isDirectory).put("s", if (k.isDirectory) 0L else k.length()).put("m", k.lastModified()))
                ok().put("entries", arr)
            }
            "fs-read" -> {
                val f = resolve(j.optString("path")) ?: return err("badpath", "Bad path")
                if (!f.isFile) return err("notfound", "Not found")
                val off = j.optLong("off", 0L).coerceAtLeast(0L)
                val len = j.optInt("len", MAX_READ).coerceIn(0, MAX_READ)
                val buf = ByteArray(len)
                var n = 0
                RandomAccessFile(f, "r").use { raf ->
                    raf.seek(off)
                    while (n < len) { val r = raf.read(buf, n, len - n); if (r < 0) break; n += r }
                }
                ok().put("data", Base64.encodeToString(buf, 0, n, Base64.NO_WRAP))
            }
            "fs-write" -> {
                val f = resolve(j.optString("path")) ?: return err("badpath", "Bad path")
                if (f.isDirectory) return err("io", "That is a folder")
                if (f.parentFile?.isDirectory != true) return err("notfound", "The folder does not exist")
                val data = Base64.decode(j.optString("data"), Base64.DEFAULT)
                if (data.size > MAX_WRITE) return err("io", "Chunk too large")
                RandomAccessFile(f, "rw").use { raf ->
                    if (j.optBoolean("trunc")) raf.setLength(0)
                    raf.seek(j.optLong("off", 0L).coerceAtLeast(0L))
                    raf.write(data)
                }
                ok()
            }
            "fs-mkdir" -> {
                val f = resolve(j.optString("path")) ?: return err("badpath", "Bad path")
                if (f.exists()) return err("exists", "Already exists")
                if (f.parentFile?.isDirectory != true) return err("notfound", "The folder does not exist")
                if (f.mkdir()) ok() else err("io", "Could not create the folder")
            }
            "fs-del" -> {
                val f = resolve(j.optString("path")) ?: return err("badpath", "Bad path")
                if (f.path == root().canonicalPath) return err("badpath", "The whole storage cannot be deleted")
                if (!f.exists()) return err("notfound", "Not found")
                if (f.deleteRecursively()) ok() else err("io", "Could not delete")
            }
            "fs-move" -> {
                val a = resolve(j.optString("from")) ?: return err("badpath", "Bad path")
                val b = resolve(j.optString("to")) ?: return err("badpath", "Bad path")
                if (a.path == root().canonicalPath || b.path == root().canonicalPath) return err("badpath", "Bad path")
                if (!a.exists()) return err("notfound", "Not found")
                if (b.exists()) return err("exists", "Already exists")
                if (a.renameTo(b)) ok() else err("io", "Could not move")
            }
            else -> err("io", "Unknown request")
        }
    }
}
