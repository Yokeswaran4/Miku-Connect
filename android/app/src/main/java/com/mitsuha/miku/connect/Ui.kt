// SPDX-License-Identifier: MIT OR GPL-3.0-or-later
// Copyright (c) 2026 Miku
package com.mitsuha.miku.connect

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DISCORD_URL = "https://discord.com/users/895560596064464956"
private const val INSTAGRAM_URL = "https://instagram.com/miku_biggest_glazer"

private fun icon(name: String, strokes: List<String>, fills: List<String> = emptyList()): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    strokes.forEach {
        b.addPath(
            PathParser().parsePathString(it).toNodes(), stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }
    fills.forEach { b.addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
    return b.build()
}

private val DiscordIcon by lazy {
    icon(
        "discord",
        listOf("M5.5 6.2C7.3 5 9.2 4.5 9.2 4.5l.6 1.2a12 12 0 0 1 4.4 0l.6-1.2s1.9.5 3.7 1.7C20.2 9 21 12.2 20.8 16c-1.5 1.2-3 1.8-4.4 2.2l-1-1.6c.5-.2 1-.4 1.4-.7a9 9 0 0 1-9.6 0c.4.3.9.5 1.4.7l-1 1.6C5.8 17.8 4.3 17.2 2.8 16 2.6 12.2 3.5 9 5.5 6.2z"),
        listOf("M9 10.7a1.3 1.3 0 1 0 0 2.6a1.3 1.3 0 1 0 0-2.6z", "M15 10.7a1.3 1.3 0 1 0 0 2.6a1.3 1.3 0 1 0 0-2.6z")
    )
}
private val InstagramIcon by lazy {
    icon(
        "instagram",
        listOf("M8.5 3.5h7a5 5 0 0 1 5 5v7a5 5 0 0 1-5 5h-7a5 5 0 0 1-5-5v-7a5 5 0 0 1 5-5z", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z"),
        listOf("M17 6.2a.8.8 0 1 0 0 1.6a.8.8 0 1 0 0-1.6z")
    )
}

// ---------- building blocks ----------
@Composable
private fun Appear(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = fadeIn(tween(350)) + slideInVertically(tween(400)) { it / 6 }) { content() }
}

@Composable
internal fun Card(title: String? = null, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Appear {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer)
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(18.dp)).animateContentSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (title != null) Text(title.uppercase(), color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            content()
        }
    }
}

@Composable
internal fun Dim(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) = Text(text, color = color, fontSize = 12.sp)

@Composable
internal fun Page(title: String, subtitle: String, pad: PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Dim(subtitle)
        }
        content()
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
internal fun ToggleRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label)
            if (hint != null) Dim(hint)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun Avatar(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PulseDot(active: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Spacer(
        Modifier.size(10.dp).alpha(if (active) a else 1f).clip(CircleShape)
            .background(if (active) OkGreen else MaterialTheme.colorScheme.outline)
    )
}

internal fun copyText(ctx: Context, text: String) {
    try {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Miku Connect", text))
        toast(ctx, "Copied")
    } catch (_: Exception) { toast(ctx, "Could not copy") }
}
internal fun toast(ctx: Context, m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
private fun openLink(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Exception) { toast(ctx, "No app can open this link") }
}

/** Uses a pairing code typed by the user. Returns true when it was accepted. */
private fun usePairingCode(ctx: Context, code: String): Boolean {
    val c = code.trim()
    if (Crypto.normalize(c).length < 4) { toast(ctx, "Enter the full pairing code"); return false }
    Hub.updateSettings(Hub.settings.value.copy(passphrase = c))
    toast(ctx, "Pairing code set — connecting…")
    return true
}

@Composable
private fun EnterCodeDialog(title: String, hint: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(hint)
                OutlinedTextField(
                    code, { code = it.uppercase() }, singleLine = true, placeholder = { Text("K7QM-2XPD") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(code) }),
                    modifier = Modifier.fillMaxWidth()
                )
                Dim("This replaces this device's pairing code.")
            }
        },
        confirmButton = { Button(onClick = { onConfirm(code) }) { Text("Pair") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- app shell ----------
@Composable
fun MikuApp(notifAccess: Boolean, onSendFiles: (String) -> Unit, onSendFolder: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val peers by Hub.peers.collectAsState()
    val transfers by Transfers.transfers.collectAsState()
    val ctx = LocalContext.current
    val pairReq by Hub.pairRequest.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val live = transfers.count { it.status == "waiting" || it.status == "active" }
    val items = listOf(
        Triple("Devices", Icons.Default.Home, peers.size),
        Triple("Files", Icons.AutoMirrored.Filled.Send, live),
        Triple("Run", Icons.Default.Build, 0),
        Triple("Activity", Icons.AutoMirrored.Filled.List, 0),
        Triple("Settings", Icons.Default.Settings, 0),
        Triple("About", Icons.Default.Info, 0),
    )
    // another device tapped this one in its Nearby list: ask for the code it is showing
    pairReq?.let { r ->
        EnterCodeDialog(
            title = "${r.name} wants to pair",
            hint = "Enter the pairing code shown on ${r.name}.",
            onDismiss = { Hub.dismissPairRequest() },
            onConfirm = { if (usePairingCode(ctx, it)) Hub.dismissPairRequest() }
        )
    }
    Surface(Modifier.fillMaxSize(), color = cs.background, contentColor = cs.onBackground) {
        Scaffold(
            containerColor = cs.background,
            contentColor = cs.onBackground,
            bottomBar = {
                NavigationBar(containerColor = cs.surfaceContainer) {
                    items.forEachIndexed { i, (label, ic, _) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(ic, label) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        ) { pad ->
            val haptic = LocalHapticFeedback.current
            val drag = remember { floatArrayOf(0f) }
            // swipe left / right anywhere to move to the next / previous tab
            Box(
                Modifier.fillMaxSize().pointerInput(tab) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag[0] = 0f },
                        onDragEnd = {
                            val next = if (drag[0] < -140f) tab + 1 else if (drag[0] > 140f) tab - 1 else tab
                            drag[0] = 0f
                            if (next != tab && next in items.indices) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                tab = next
                            }
                        },
                        onDragCancel = { drag[0] = 0f },
                        onHorizontalDrag = { _, amount -> drag[0] += amount }
                    )
                }
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        // new page slides in from the side it sits on
                        val dir = if (targetState > initialState) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                        (slideIntoContainer(dir, tween(320)) { it / 4 } + fadeIn(tween(320))) togetherWith
                            (slideOutOfContainer(dir, tween(320)) { it / 4 } + fadeOut(tween(200)))
                    },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        0 -> DevicesPage(pad, onSendFiles, onSendFolder)
                        1 -> FilesPage(pad)
                        2 -> RunPage(pad)
                        3 -> ActivityPage(pad)
                        4 -> SettingsPage(pad, notifAccess)
                        else -> AboutPage(pad)
                    }
                }
            }
        }
    }
}

// ---------- pages ----------
@Composable
private fun DevicesPage(pad: PaddingValues, onSendFiles: (String) -> Unit, onSendFolder: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val peers by Hub.peers.collectAsState()
    val ips by Hub.localIps.collectAsState()
    val nearby by Hub.nearby.collectAsState()
    val settings by Hub.settings.collectAsState()
    val ctx = LocalContext.current
    var ip by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf<Nearby?>(null) }
    var enterManual by remember { mutableStateOf(false) }
    var unpairTarget by remember { mutableStateOf<Peer?>(null) }
    // close the code dialog as soon as the other device has connected
    LaunchedEffect(peers, pairing) {
        val p = pairing
        if (p != null && peers.any { it.id == p.id }) { toast(ctx, "Paired with ${p.name}"); pairing = null }
    }
    val startPair = { n: Nearby ->
        if (Hub.sendPairRequest(n.id) != null) pairing = n else toast(ctx, "That device is no longer visible")
    }
    Page("Devices", if (ips.isEmpty()) "No Wi-Fi connection detected" else "This phone: " + ips.joinToString(", "), pad) {
        if (peers.isEmpty()) Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(true)
                Spacer(Modifier.width(10.dp))
                Text("Looking for devices…", fontWeight = FontWeight.Bold)
            }
            Dim("Open Miku Connect on your other device. It will appear under Nearby devices.")
            Dim("Tip: swipe left or right to switch between tabs.")
        }
        peers.forEach { p ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar { Text(p.name.take(1).uppercase(), color = cs.primary, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Dim("${p.platform} · ${p.address}" + if (settings.batterySync && p.battery >= 0) " · ${if (p.charging) "⚡" else "🔋"} ${p.battery}%" else "")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSendFiles(p.id) }) { Text("Send file") }
                    OutlinedButton(onClick = { onSendFolder(p.id) }) { Text("Send folder") }
                }
                LatencyPanel(p.id)
                TextButton(onClick = { unpairTarget = p }) { Text("Unpair", color = cs.error) }
            }
        }
        Card("Nearby devices") {
            Dim("Miku Connect is open on these devices. Tap one to get a pairing code, then enter that code on the other device.")
            if (nearby.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(true)
                Spacer(Modifier.width(10.dp))
                Dim("No other devices found yet")
            }
            nearby.forEach { n ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { startPair(n) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar { Text(n.name.take(1).uppercase(), color = cs.primary, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.name, fontWeight = FontWeight.Bold)
                        Dim("${n.platform} · ${n.address}")
                    }
                    Button(onClick = { startPair(n) }) { Text("Pair") }
                }
            }
            OutlinedButton(onClick = { enterManual = true }) { Text("I have a code") }
        }
        Card("Connect by IP") {
            Dim("Use this if a device doesn't show up on its own (guest Wi-Fi, router isolation).")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(ip, { ip = it }, placeholder = { Text("192.168.1.23") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { if (ip.isNotBlank()) { Hub.connectTo(ip.trim()); ip = "" } }) { Text("Connect") }
            }
        }
    }
    unpairTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { unpairTarget = null },
            title = { Text("Unpair ${p.name}?") },
            text = { Text("This device will disconnect and won't reconnect on its own. To pair again, tap it under Nearby devices.") },
            confirmButton = { Button(onClick = { Hub.unpair(p.id); unpairTarget = null; toast(ctx, "Unpaired ${p.name}") }) { Text("Unpair") } },
            dismissButton = { TextButton(onClick = { unpairTarget = null }) { Text("Cancel") } }
        )
    }
    pairing?.let { n ->
        val cancel = { Hub.cancelPairRequest(n.id); pairing = null }
        AlertDialog(
            onDismissRequest = cancel,
            title = { Text("Pair with ${n.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter this pairing code on ${n.name}:")
                    Text(
                        settings.passphrase, fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp, color = cs.primary, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = 0.10f)).clickable { copyText(ctx, settings.passphrase) }.padding(vertical = 16.dp)
                    )
                    Dim("Tap the code to copy it.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(true)
                        Spacer(Modifier.width(10.dp))
                        Dim("Waiting for ${n.name}…")
                    }
                }
            },
            confirmButton = { TextButton(onClick = cancel) { Text("Cancel") } }
        )
    }
    if (enterManual) EnterCodeDialog(
        title = "Enter pairing code",
        hint = "Enter the pairing code shown on your other device.",
        onDismiss = { enterManual = false },
        onConfirm = { if (usePairingCode(ctx, it)) enterManual = false }
    )
}

@Composable
private fun TransferRow(t: Transfer) {
    val cs = MaterialTheme.colorScheme
    val live = t.status == "waiting" || t.status == "active"
    val target = if (t.size > 0) (t.done.toFloat() / t.size).coerceIn(0f, 1f) else if (t.status == "done") 1f else 0f
    val p by animateFloatAsState(target, tween(300), label = "progress")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (t.outgoing) "↑" else "↓", color = cs.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(t.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Dim(t.peer)
        }
        if (t.status == "waiting") LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (live) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Dim(
                    when (t.status) {
                        "waiting" -> if (t.outgoing) "Waiting for ${t.peer} to accept…" else "Starting…"
                        "active" -> "${fmtSize(t.done)} / ${fmtSize(t.size)}"
                        "done" -> "Done · ${fmtSize(t.size)}"
                        "rejected" -> "Declined by ${t.peer}"
                        "cancelled" -> "Cancelled"
                        else -> if (t.canResume) "Connection lost · continues automatically, or press Resume" else "Failed"
                    }
                )
            }
            if (live) TextButton(onClick = { Transfers.cancel(t.id) }) { Text("Cancel") }
            else if (t.canResume) TextButton(onClick = { Transfers.resume(t.id) }) { Text("Resume") }
        }
    }
}

@Composable
private fun FilesPage(pad: PaddingValues) {
    val ctx = LocalContext.current
    val s by Hub.settings.collectAsState()
    val transfers by Transfers.transfers.collectAsState()
    Page("Files", "Send with Share → Miku Connect, or “Send file” on a device.", pad) {
        Card {
            ToggleRow("Accept incoming files", "Saved to Downloads/MikuConnect", s.acceptFiles) { Hub.updateSettings(s.copy(acceptFiles = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open Downloads") }
                if (transfers.any { it.status != "waiting" && it.status != "active" }) OutlinedButton(onClick = { Transfers.clearFinished() }) { Text("Clear finished") }
            }
        }
        Card("Transfers") {
            if (transfers.isEmpty()) Dim("No transfers yet.")
            transfers.forEach { TransferRow(it) }
        }
    }
}

@Composable
private fun ActivityPage(pad: PaddingValues) {
    val cs = MaterialTheme.colorScheme
    val log by Hub.log.collectAsState()
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Page("Activity", "Recent events on this phone.", pad) {
        Card {
            if (log.isEmpty()) Dim("Nothing yet.")
            log.take(30).forEach { l ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.title, fontWeight = FontWeight.Bold, color = if (l.kind == "warn") cs.error else cs.onSurface, modifier = Modifier.weight(1f))
                        if (l.count > 1) Text("×${l.count}", color = cs.error, fontSize = 12.sp)
                    }
                    if (l.text.isNotBlank()) Dim(l.text)
                    Dim(fmt.format(Date(l.time)) + " · " + l.kind)
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(pad: PaddingValues, notifAccess: Boolean) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s by Hub.settings.collectAsState()
    var name by remember(s.name) { mutableStateOf(s.name) }
    var pass by remember(s.passphrase) { mutableStateOf(s.passphrase) }
    var show by remember { mutableStateOf(false) }
    Page("Settings", "Name this phone and set the shared pairing code.", pad) {
        Card("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (v, label) ->
                    FilterChip(selected = s.theme == v, onClick = { Hub.updateSettings(s.copy(theme = v)) }, label = { Text(label) })
                }
            }
            ToggleRow(
                "Use system colors",
                if (android.os.Build.VERSION.SDK_INT >= 31) "Colors from your wallpaper. Off uses Miku blue." else "Needs Android 12 or newer. Miku blue is used.",
                s.systemColors && android.os.Build.VERSION.SDK_INT >= 31
            ) { Hub.updateSettings(s.copy(systemColors = it)) }
        }
        Card("This device") {
            OutlinedTextField(name, { name = it }, label = { Text("Device name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Pairing code (same on every device)") }, singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (name.isNotBlank() && pass.isNotBlank()) { Hub.updateSettings(s.copy(name = name.trim(), passphrase = pass.trim())); toast(ctx, "Saved") }
                }) { Text("Save") }
                OutlinedButton(onClick = { show = !show }) { Text(if (show) "Hide code" else "Show code") }
                OutlinedButton(onClick = {
                    val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                    val r = java.security.SecureRandom()
                    val c = (1..8).map { a[r.nextInt(a.length)] }.joinToString("")
                    pass = c.take(4) + "-" + c.drop(4); show = true
                }) { Text("New") }
            }
        }
        Card("Sync") {
            ToggleRow("Clipboard sync", "Copy here, paste anywhere", s.clipboardSync) { Hub.updateSettings(s.copy(clipboardSync = it)) }
            ToggleRow("Notifications", "Send this phone's notifications and show your other devices'", s.notifySync) { Hub.updateSettings(s.copy(notifySync = it)) }
            ToggleRow("Battery level", "Share this phone's battery and show your other devices'", s.batterySync) { Hub.updateSettings(s.copy(batterySync = it)) }
            ToggleRow("Send clipboard automatically", "Phone → computer without tapping. Needs “Display over other apps”.", s.autoClipboard) { Hub.updateSettings(s.copy(autoClipboard = it)) }
            if (s.autoClipboard) {
                val overlayOk by ClipboardWatcher.overlayGranted.collectAsState()
                if (!overlayOk) {
                    Dim("Allow “Display over other apps” so Miku Connect can read the clipboard in the background.")
                    Button(onClick = {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName)))
                    }) { Text("Allow display over other apps") }
                } else Dim("Automatic clipboard sending: on", OkGreen)
            }
            if (!notifAccess) {
                Dim("Notification access is off, so this phone can't forward notifications yet.")
                Button(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Grant notification access") }
            } else Dim("Notification access: granted", OkGreen)
            Button(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
                toast(ctx, if (t.isEmpty()) "Clipboard is empty" else {
                    val n = Hub.sendClipboard(t)
                    if (n > 0) "Sent to $n device${if (n > 1) "s" else ""}" else "No connected devices"
                })
            }) { Text("Send clipboard now") }
            OutlinedButton(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery: set to unrestricted") }
            Dim("Tip: add the “Send clipboard” tile to quick settings. It also works if automatic sending is off or blocked by your phone.")
        }
    }
}

@Composable
private fun LinkCard(label: String, sub: String, ic: ImageVector, url: String) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Card {
        Row(Modifier.fillMaxWidth().clickable { openLink(ctx, url) }, verticalAlignment = Alignment.CenterVertically) {
            Avatar { Icon(ic, label, tint = cs.primary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Bold); Dim(sub) }
            Text("→", color = cs.primary, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LicenseBlock(title: String, file: String) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(open) {
        if (open && text == null) text = withContext(Dispatchers.IO) {
            try { ctx.assets.open("licenses/$file").bufferedReader().use { it.readText() } } catch (_: Exception) { "License file not found." }
        }
    }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(if (open) "▾" else "▸", color = cs.primary)
        }
        if (open) Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            Text(text ?: "Loading…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceInfoCard() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<List<DeviceInfo.Section>?>(null) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { DeviceInfo.collect(ctx) } }
    Card("About this device") {
        Dim("Model, CPU and system details for bug reports. The report leaves out your device name, IP addresses, pairing code and the text of clipboard and notification entries.")
        val sections = info
        if (sections == null) Dim("Loading…")
        else {
            sections.forEach { sec ->
                Text(sec.title.uppercase(), color = cs.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                sec.rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(k, color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(0.4f).padding(end = 8.dp))
                        Text(v, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copyText(ctx, DeviceInfo.report(sections, Hub.log.value)) }) { Text("Copy debug report") }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Miku Connect debug report")
                        .putExtra(Intent.EXTRA_TEXT, DeviceInfo.report(sections, Hub.log.value))
                    try { ctx.startActivity(Intent.createChooser(send, "Share debug report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { toast(ctx, "No app can share this") }
                }) { Text("Share") }
            }
        }
    }
}

@Composable
private fun AboutPage(pad: PaddingValues) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "Mitsuha_Miku_v1" } catch (_: Exception) { "Mitsuha_Miku_v1" } }
    Page("About", "Version $version", pad) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), "Miku Connect", Modifier.size(64.dp).clip(CircleShape).border(2.dp, cs.primary, CircleShape), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Miku Connect", color = cs.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Dim("Notifications, clipboard and files between your devices over Wi-Fi. No cloud, no account.")
                }
            }
            Dim("Everything is end-to-end encrypted with AES-256-GCM using a key derived from your pairing code.")
        }
        DeviceInfoCard()
        Text("CONTACT", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        LinkCard("Discord", "Message me on Discord", DiscordIcon, DISCORD_URL)
        LinkCard("Instagram", "@miku_biggest_glazer", InstagramIcon, INSTAGRAM_URL)
        Card("Licenses") {
            Dim("Dual-licensed: MIT or GPL-3.0-or-later, at your option.")
            LicenseBlock("Project license", "LICENSE.txt")
            LicenseBlock("MIT License", "LICENSE-MIT.txt")
            LicenseBlock("GNU GPL v3.0", "LICENSE-GPL-3.0.txt")
            LicenseBlock("Third-party notices", "THIRD-PARTY-NOTICES.txt")
            Dim("Built with open-source software, thank you to its authors:")
            Dim("• Java-WebSocket (MIT)\n• Kotlin and kotlinx.coroutines (Apache-2.0)\n• Jetpack Compose, Material 3, Material Icons (Apache-2.0)\n• AndroidX Core, Activity, Lifecycle (Apache-2.0)\n• Android Open Source Project (Apache-2.0)\n• Android Gradle Plugin and Gradle, build tools (Apache-2.0)")
        }
        Text("© 2026 Miku", color = cs.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
