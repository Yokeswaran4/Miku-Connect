<div align="center">

# Miku Connect

**Notifications, clipboard, files and battery status shared between your Android phone, Linux PC and Windows PC, over your own Wi-Fi. No cloud. No account.**

`Version: Mitsuha_Miku_v1` · `Desktop 1.0.0` · `Android versionCode 2` · `License: MIT OR GPL-3.0-or-later`

</div>

---

## Table of contents

1. [Overview](#1-overview)
2. [Features](#2-features)
3. [Project structure](#3-project-structure)
4. [Requirements](#4-requirements)
5. [Building the desktop app](#5-building-the-desktop-app-linux--windows)
6. [Building the Android app](#6-building-the-android-app)
7. [Pairing your devices](#7-pairing-your-devices)
8. [Android one-time setup](#8-android-one-time-setup)
9. [Using the app](#9-using-the-app)
10. [Remote commands (phone to PC)](#10-remote-commands-phone--linux--windows-pc)
11. [Security model](#11-security-model)
12. [Network and firewall](#12-network-and-firewall)
13. [Protocol reference](#13-protocol-reference)
14. [Settings and configuration](#14-settings-and-configuration)
15. [Troubleshooting](#15-troubleshooting)
16. [Tech stack and credits](#16-tech-stack-and-credits)
17. [License](#17-license)
18. [Contact](#18-contact)

---

## 1. Overview

Miku Connect links the devices you own so they behave like one system. Copy text on your PC and paste it on your phone. See your phone's notifications on your desktop. Drag a file onto a device and it arrives in seconds. Everything stays on your local network and is encrypted with a key derived from a pairing code that only you know.

It is made of two apps that speak the same protocol:

| App | Platforms | Built with |
|---|---|---|
| **Desktop** | Linux (AppImage), Windows (installer and portable `.exe`) | Electron, Node.js, `ws` |
| **Android** | Android 10+ (API 29) | Kotlin, Jetpack Compose, Material 3, Java-WebSocket |

## 2. Features

### Sync

- **Clipboard sync**: text, up to 1 MB. PC to phone is fully automatic. Phone to PC can also be automatic with the optional **Send clipboard automatically** setting (needs the "Display over other apps" permission); without it, it is one tap. See [section 9](#clipboard).
- **Notification mirroring**:
  - Android to PC: all normal notifications are mirrored, with app name, title, text and icon.
  - Linux to other devices: desktop notifications are forwarded too (needs `dbus-monitor`).
  - Windows: displays other devices' notifications; it does not forward its own.
- **Battery level**: every device shares its charge level and charging state, shown on each device card (`🔋 85%`, `⚡ 40%`).
- **Connection latency**: live ping, average, min, max, jitter and packet loss to each paired device, measured over the same encrypted link your data uses.

### File transfer

- Any file type, any size, in every direction (phone ⇄ PC, PC ⇄ PC).
- **Folders**: send a whole folder with all its sub folders (**Send folder**, or drag a folder onto a device on desktop). The structure is recreated on the other side.
- **Resume**: if the connection drops, what already arrived is kept and the transfer continues from that point (automatically when the device is back, or with the **Resume** button). Only the missing part is sent again.
- Progress bar and Cancel button.
- Drag and drop on desktop, **Share → Miku Connect** on Android.
- Never overwrites existing files (`photo.jpg`, `photo (1).jpg`, ...).
- Optional **Accept incoming files** switch.

### Remote commands

- Run shell commands on a connected Linux or Windows PC from the phone's **Run** tab, with output, saved commands and recent history.
- **Off by default.** Never elevated, and deleting files is blocked. See [section 10](#10-remote-commands-phone--linux--windows-pc).

### Convenience

- **Zero-config discovery** through UDP broadcast, with **Connect by IP** as a fallback.
- **Code-based pairing**: tap a nearby device, type the code it shows.
- **Unpair** a device cleanly, without changing the shared code.
- **Launch on startup** for Windows and Linux, either hidden in the tray or with the window shown.
- **System tray** on desktop; closing the window keeps sync running.
- **Quick-settings tile** and notification action on Android to send the clipboard.
- **About this device** page plus a **debug report** (copy, save or share) that leaves out clipboard text, notification text, IP addresses, serial numbers and the pairing code.
- **Theming**: light, dark or system; Windows accent colour, Android Material You (12+) or a Miku-blue fallback.

## 3. Project structure

```
MikuConnect/
├── README.md / Readme_project.md   Documentation
├── LICENSE                         Dual-license notice
├── LICENSE-MIT
├── LICENSE-GPL-3.0
├── THIRD-PARTY-NOTICES.txt
│
├── desktop/                        Electron app (Linux + Windows)
│   ├── main.js                     App core: config, discovery, pairing, WebSocket server/client, tray, IPC
│   ├── preload.js                  Safe bridge between main process and UI
│   ├── transfer.js                 Encrypted chunked file transfer, folders, resume
│   ├── remote.js                   Remote command execution + safety checks
│   ├── battery.js                  Battery reading (Linux sysfs / Windows Win32_Battery)
│   ├── latency.js                  Ping/pong latency tracker
│   ├── notifwatch.js               Linux: watches desktop notifications via dbus-monitor
│   ├── autostart.js                Launch on startup (Windows login item / Linux XDG autostart)
│   ├── deviceinfo.js               "About this device" and debug report
│   ├── renderer/                   UI (index.html, renderer.js, styles.css, logo.png)
│   ├── test/transfer.test.js       Loopback tests: folders, resume, cancel, hostile paths
│   ├── licenses/                   License texts shown inside the app
│   ├── build/icon.png              App icon
│   └── package.json                Dependencies and electron-builder config
│
└── android/                        Android Studio project
    ├── build.gradle.kts, settings.gradle.kts, gradle/wrapper/
    └── app/src/main/
        ├── AndroidManifest.xml
        ├── assets/licenses/        License texts shown inside the app
        ├── res/                    Icons, themes, strings
        └── java/com/mitsuha/miku/connect/
            ├── MainActivity.kt             Entry point
            ├── Ui.kt                       Main Compose UI (Devices, Files, Activity, Settings, About)
            ├── RunUi.kt / RemoteCommands.kt  "Run" tab and command protocol
            ├── LatencyUi.kt / Latency.kt   Latency graph and tracker
            ├── Hub.kt                      Core: discovery, pairing, WebSocket, state
            ├── Crypto.kt                   PBKDF2 + AES-256-GCM
            ├── Transfers.kt                File transfer, folders, resume
            ├── SyncService.kt              Foreground service that keeps the link alive
            ├── MikuNotificationListener.kt Mirrors phone notifications
            ├── ClipboardWatcher.kt         Optional automatic phone → PC clipboard sending
            ├── ClipboardTileService.kt     Quick-settings "Send clipboard" tile
            ├── ClipboardSendActivity.kt    Reads clipboard in foreground / handles Share menu
            └── DeviceInfo.kt               About this device + debug report
```

## 4. Requirements

| Component | Requirement |
|---|---|
| Desktop (build) | Node.js **18+** (`package.json` declares `>=20`; use 20 or newer to be safe), npm |
| Desktop (run) | Linux (x64 AppImage) or Windows |
| Android (build) | Android Studio Ladybug or newer, JDK 17, Gradle 8.9 (wrapper config included) |
| Android (run) | Android **10 or newer** (API 29); compiled against API 35 |
| Network | All devices on the same LAN / Wi-Fi |
| Optional (Linux) | `dbus-monitor` (package `dbus` / `dbus-bin`) to forward this PC's notifications |

## 5. Building the desktop app (Linux / Windows)

```bash
cd desktop
npm install
npm start                 # run directly to test
```

Package it:

```bash
npm run dist:linux        # build on Linux   -> dist/Miku-Connect-Mitsuha_Miku_v1.AppImage
npm run dist:win          # build on Windows -> dist/Miku-Connect-Setup-Mitsuha_Miku_v1.exe
                          #                     dist/Miku-Connect-Portable-Mitsuha_Miku_v1.exe
```

Run the AppImage:

```bash
chmod +x Miku-Connect-Mitsuha_Miku_v1.AppImage
./Miku-Connect-Mitsuha_Miku_v1.AppImage
```

Run the transfer tests (no Electron or network needed, they use in-memory sockets):

```bash
cd desktop
npm test
```

Notes:

- Build each platform on its own OS.
- Closing the window **hides it to the tray**. Quit from the tray menu.
- Only one instance can run at a time.
- Linux builds disable GPU acceleration and the Chromium sandbox to avoid blank windows and `/dev/shm` crashes on varied setups.

## 6. Building the Android app

1. Open the `android/` folder in **Android Studio** (Ladybug or newer) and let Gradle sync.
2. Connect your phone and press **Run**, or use **Build → Build APK(s)**.
3. To build from a terminal, run `gradle wrapper` once inside `android/` first (the wrapper jar is not bundled), then:
   ```bash
   ./gradlew assembleDebug
   ```

Details: `applicationId` / namespace `com.mitsuha.miku.connect`, `minSdk 29`, `targetSdk 35`, `compileSdk 35`, Java/Kotlin target 17, Compose BOM `2024.10.01`.

> The manifest requests `QUERY_ALL_PACKAGES` so notifications show real app names. This is intended for sideloaded builds and is **not suitable for Google Play** as-is.

## 7. Pairing your devices

1. Put all devices on the **same Wi-Fi/LAN** and open Miku Connect on each.
2. Go to **Devices**. Every device running the app is listed under **Nearby devices**.
3. Click (or tap) the device you want to pair with. **This** device shows a pairing code, for example `K7QM-2XPD`. The other device pops up and asks for it (Android also posts a notification).
4. Enter the code on the other device and press **Pair**. They connect automatically.

Good to know:

- Missed the prompt? Use **I have a code** under Nearby devices, or type the code in **Settings**.
- The code is **never sent over the network**. Only a "please ask for the code" request is.
- Entering a code replaces that device's current code, so pair every device to the **same** code.
- **Unpair**: press **Unpair** on a connected device (desktop asks for a second click, Android asks for confirmation). Both sides disconnect and stop auto-reconnecting. The device reappears under Nearby devices and can be paired again the same way. Setting a new pairing code clears all unpaired entries.
- Discovery failed (guest Wi-Fi, AP isolation, some routers)? Use **Connect by IP**.

## 8. Android one-time setup

| Step | Why |
|---|---|
| **Grant notification access** (button in the app) | Lets Miku Connect read and mirror your notifications. On Android 13+ with sideloaded apps, you may first need *Settings → Apps → Miku Connect → ⋮ → Allow restricted settings*. |
| **Battery: Unrestricted** (button in the app) | Stops Android killing the background connection. |
| **Allow notifications** when asked | Needed for the background service notification and for showing other devices' notifications. |
| Add the **Send clipboard** tile (optional) | Quick-settings edit menu → drag "Send clipboard" in. |
| **Display over other apps** (optional) | Only for **Send clipboard automatically**: Settings → Sync → turn the option on → tap **Allow display over other apps**. |

## 9. Using the app

### Desktop tabs

| Tab | What it does |
|---|---|
| **Devices** | Connected devices (with battery and latency), nearby devices, Connect by IP. Drop files on a device row to send. |
| **Transfers** | Live and finished file transfers. |
| **Sync** | Toggles for clipboard, notifications, battery, incoming files, and remote control. |
| **Activity** | Recent events (connections, notifications, clipboard, errors). |
| **Settings** | Theme, system colours, launch on startup, device name, pairing code, About this device, network addresses. |
| **Contact** | Links to the author. |
| **Licenses** | Project license, MIT, GPL-3.0, third-party notices. |

### Android tabs

**Devices · Files · Run · Activity · Settings · About**

### Clipboard

| Direction | Behaviour |
|---|---|
| PC → phone | Automatic. The phone's clipboard is set as soon as you copy on the PC. |
| Phone → PC | **Automatic if you turn on Settings → Sync → Send clipboard automatically** (see below). Otherwise one tap, because since Android 10 apps can only *read* the clipboard while they are in the foreground. |

**Manual ways to send from the phone** (always available):

- Tap the **Send clipboard** quick-settings tile.
- Tap **Send clipboard** in the persistent notification.
- Use **Share → Miku Connect** on any text.
- Tap **Send clipboard now** in Settings.

**Automatic sending (optional).** Android still *tells* an app when the clipboard changes; it only blocks *reading* it in the background. Miku Connect uses that:

1. The clipboard changes.
2. A 1×1 pixel, invisible overlay is shown. This is what the **Display over other apps** permission allows, and it lets Android accept an activity start from the background.
3. A transparent activity opens for a split second, reads the clipboard while it has focus, sends it, and closes. There is no toast and no visible flash.

Safeguards: text that just arrived from another device is not sent back, the same text is not sent twice in a row, and nothing is sent when no device is connected or **Clipboard sync** is off.

Be aware that this is a workaround for a deliberate Android restriction. It needs the permission, and some phone makers restrict background activity starts further. If it does not work on your phone, turn it off and use the tile, the notification button or the Share menu.

### Notifications

| | Behaviour |
|---|---|
| Android → PC | All normal notifications are mirrored. |
| Linux PC → others | Forwarded via `dbus-monitor`, with de-duplication and flood protection (max 8 per 10 s). Miku Connect's own notifications are never forwarded. |
| Windows PC → others | Not available (Windows does not expose other apps' notifications simply). |
| Test | The desktop can send a test notification to check the link. |

### Sending files

| From | How |
|---|---|
| Desktop | **Send file** or **Send folder** next to a connected device, or drag and drop files and folders onto the device's row. |
| Android | **Send file** or **Send folder** under a connected device, or **Share → Miku Connect** from any app (Share sends to *every* connected device). |

Received files land in:

- **Desktop:** `Downloads/MikuConnect/` (a notification appears; click it to reveal the file)
- **Android:** `Downloads/MikuConnect/` (visible in the Files app)

**Folders** arrive as `Downloads/MikuConnect/<FolderName>/...` with the same sub folders. If a folder with that name already exists, files are added to it and existing files are never overwritten (`a (1).jpg`). Symbolic links are skipped. Empty folders are not created. Folders are sent file by file, in order.

**Resume.** While a file is arriving, the received part is kept in a hidden holding place (`Downloads/MikuConnect/.partial` on desktop, app storage on Android) and only moves to its real name when complete. If the connection drops:

- the sender keeps the file (and the rest of the folder) in a waiting list and continues **automatically when the device reconnects**, sending only the missing part;
- or press **Resume** on the failed transfer;
- or simply send the same unchanged file again later: it is recognised and continued.

Half-received files are removed when you cancel, when the sender cancels, or after 7 days. A file is only recognised if its path, size and modification time are unchanged. On Android, files that came from the Share menu can only be continued while Android still allows access to them; if not, the log says so and you send the file again.

### Battery and latency

- Phones report battery on every change; laptops are checked once a minute (Linux `/sys/class/power_supply`, Windows `Win32_Battery`). Desktops without a battery send nothing.
- Latency pings run every 2 s while the window is visible and every 20 s while hidden. A reply after 5 s counts as lost. Up to 60 samples are kept per device.

## 10. Remote commands (phone → Linux / Windows PC)

The Android **Run** tab runs a command on a connected computer and shows the output.

**It is off by default.** On the computer open **Sync → Remote control from your phone** and enable **Allow commands from phone**.

| Rule | Detail |
|---|---|
| Who can ask | Only a paired **Android** device, encrypted with the pairing code |
| Shell | Linux `/bin/sh`; Windows Command Prompt or PowerShell |
| User | Normal user, in the home folder, **never elevated** |
| Blocked | `sudo`, `su`, `doas`, `pkexec`, `runas`, `gsudo`, `-Verb RunAs` |
| Deleting files | **Refused by both phone and computer**, with no setting to enable it: `rm`, `rmdir`, `del`, `erase`, `rd`, `Remove-Item`, `find -delete`, `shred`, ... |
| stdin | Closed, so nothing can wait for a password |
| Timeout | 30, 60 or 120 s, then the command is stopped |
| Output | Capped at 48 KB |
| Audit | Every request appears in **Activity** on the computer |

**Saved and recent:** tap **Save** under a command to name and keep it; run, edit or delete it from **Saved**. The last 8 commands appear under **Recent**. Both are stored on the phone only.

**When something fails**, the result explains *Why this happened* and *How to fix it* (permission denied, command not found, wrong path, timeout, feature switched off, blocked delete command, computer unreachable, older desktop version, and so on).

> The delete/elevation check on free-form commands is a **guard rail against mistakes, not a sandbox**. A determined command can still change files in other ways. Keep the switch off when you do not need it.

## 11. Security model

- **Encryption:** every message is AES-256-GCM encrypted. Key = `PBKDF2-SHA256(UPPERCASE(code without symbols), salt "miku-connect-v1", 100,000 iterations, 32 bytes)`.
- **Authentication:** a device with a different code cannot produce valid frames and is rejected.
- **Pairing code** is generated randomly (8 characters from an unambiguous alphabet, formatted `XXXX-XXXX`) and is never broadcast.
- **LAN only:** there is no server, relay, telemetry or account.
- **File safety:** receivers clean every part of a file or folder path (no `..`, no absolute paths, no drive letters, no illegal characters), so nothing can be written outside `Downloads/MikuConnect`. They never overwrite files, and reject data beyond the announced size.
- **Privacy in reports:** debug reports omit clipboard and notification text, IPs, serial numbers, computer name and the pairing code.
- Pick a code that is not trivially guessable if you set your own. Anyone on your network who knows it can talk to your devices.

## 12. Network and firewall

| Port | Protocol | Purpose |
|---|---|---|
| **48998** | TCP | Encrypted WebSocket transport |
| **48999** | UDP | Device discovery (broadcast every 3 s) and pairing requests |

- **Windows:** accept the firewall prompt on first start (allow **Private** networks).
- **Linux (ufw):**
  ```bash
  sudo ufw allow 48998/tcp
  sudo ufw allow 48999/udp
  ```
- Routers with **AP/client isolation** or guest networks block device-to-device traffic. Use a normal LAN or Connect by IP on a network that permits it.

## 13. Protocol reference

For anyone who wants to extend or reimplement it.

**Discovery:** UDP broadcast to port 48999 every 3 s:
`{"app":"mikuconnect","id","name","platform","port"}`
Beacons heard in the last 10 s appear as nearby devices. The device with the smaller id initiates the connection.

**Pairing:** unicast UDP to port 48999 of the other device:
`{"app":"mikuconnect","type":"pair-request","id","name","platform"}` (and `"type":"pair-cancel"`). It carries no code.

**Transport:** WebSocket on TCP 48998. Each frame is `base64(iv[12] + ciphertext + tag[16])` of a JSON message.

**Messages**

| Message | Fields |
|---|---|
| `hello` | `id, name, platform` |
| `clipboard` | `text` |
| `notification` | `app, title, text` (desktop also sends `icon`, `ts`) |
| `battery` | `level, charging` |
| `unpair` | none; both sides remember the other's id and skip it until a new `pair-request` |
| `ping` / `pong` | `n`; used for latency; older versions ignore them |
| `cmd-run` | `rid, command, shell, timeout` |
| `cmd-cancel` | `rid` (the legacy `cmd-rm` is always refused) |
| `cmd-result` | `rid, ok, code, stdout, stderr, truncated, ms, error{kind,why,fix}` |

**File transfer**

```
file-offer {tid,name,size, key?, rel?}
   → file-accept {tid, offset?}  |  file-reject {tid,reason}
   → binary WebSocket frames, each:  iv[12] + AES-GCM( uint32-BE tid + up to 64 KiB data ) + tag[16]
   → file-end {tid}
   → file-ack {tid,ok}
Either side may send file-cancel {tid}.
```

- `key` (optional): 16 to 64 hex characters that identify this exact file (a hash of sender id, path/uri, size, modified time and folder path). It lets the receiver recognise a half-received file.
- `rel` (optional): `Folder/sub/name.ext` with `/` separators, for folder transfers. The receiver sanitises each part.
- `offset` (optional, in `file-accept`): the receiver already holds this many bytes of that `key`; the sender skips them and sends the rest. Frames then carry the data from `offset` on.
- Older versions ignore `key`, `rel` and `offset`, so they still interoperate: they just send or receive single files from the start, without resume.

## 14. Settings and configuration

Desktop settings are stored in `config.json` in the Electron user-data folder.

| Key | Default | Meaning |
|---|---|---|
| `id` | random UUID | This device's identity |
| `name` | OS hostname | Display name (max 40 chars) |
| `passphrase` | random `XXXX-XXXX` | Shared pairing code |
| `clipboardSync` | `true` | Sync clipboard |
| `notifySync` | `true` | Send and show notifications |
| `batterySync` | `true` | Share battery level |
| `acceptFiles` | `true` | Accept incoming files |
| `allowCommands` | `false` | Allow phone to run commands |
| `launchOnStartup` | `false` | Start at login |
| `startHidden` | `true` | Start in tray when launched at login |
| `theme` | `system` | `system`, `light` or `dark` |
| `systemColors` | `true` | Use OS accent colour (off = Miku blue) |
| `ignored` | `[]` | Unpaired device ids that will not auto-connect |

Startup entries: Windows uses the login-item registry key (portable builds are supported); Linux writes `~/.config/autostart/miku-connect.desktop`.

## 15. Troubleshooting

| Problem | Try this |
|---|---|
| Devices do not see each other | Same Wi-Fi? Firewall ports open? AP isolation / guest network? Use **Connect by IP**. |
| Pairing prompt never appears | Use **I have a code** and type the code shown on the other device. |
| Connected, but nothing syncs | Make sure every device uses the **same** pairing code (re-enter it to be sure). |
| Android notifications not mirrored | Grant notification access; on Android 13+ allow restricted settings; set battery to Unrestricted. |
| Android drops the connection in background | Set battery to **Unrestricted**; keep the persistent notification enabled. |
| Linux PC notifications not forwarded | Install `dbus` / `dbus-bin` so `dbus-monitor` exists. The Activity log says "Notification forwarding off" when it is missing. |
| Linux window is blank | GPU is already disabled by the app; check your graphics drivers and try launching from a terminal to see errors. |
| Launch on startup will not turn on | Windows: check Task Manager → Startup apps and security software. Linux: make `~/.config/autostart` writable. The app shows the exact reason and fix. |
| Automatic phone → PC clipboard does nothing | Check Settings → Sync shows "Automatic clipboard sending: on" (permission granted), a device is connected, and Clipboard sync is on. Some phones block background activity starts; use the tile instead. |
| A transfer says "Connection lost" | It continues by itself when the device reconnects; or press **Resume**. |
| "Can't be opened any more. Send it again." (Android) | The file came from the Share menu and Android withdrew access. Send it again, preferably with **Send file**. |
| Run tab says "feature switched off" | Enable **Allow commands from phone** on the computer. |
| Latency shows "No reply yet" | The other device is running an older version that ignores `ping`. |
| Need to report a bug | Settings → **About this device** → copy or save the debug report. |

## 16. Tech stack and credits

**Desktop:** Electron (Chromium + Node.js), `ws`, `electron-builder` (build only), D-Bus `dbus-monitor` (optional, not bundled).

**Android:** Kotlin, kotlinx.coroutines, Jetpack Compose, Material 3, Material Icons, AndroidX (Core KTX, Activity Compose, Lifecycle), Java-WebSocket, Android Gradle Plugin and Gradle (build only).

Full copyright holders and license types are in [`THIRD-PARTY-NOTICES.txt`](THIRD-PARTY-NOTICES.txt) and inside each app under **Licenses / About**.

## 17. License

Copyright © 2026 Miku.

Miku Connect is **dual-licensed**. Use it under either license, at your option:

- **MIT**: see [`LICENSE-MIT`](LICENSE-MIT)
- **GPL-3.0-or-later**: see [`LICENSE-GPL-3.0`](LICENSE-GPL-3.0)

`SPDX-License-Identifier: MIT OR GPL-3.0-or-later`. Third-party components keep their own licenses.

## 18. Contact

Questions, bugs or ideas?

- **Discord:** https://discord.com/users/895560596064464956
- **Instagram:** [@miku_biggest_glazer](https://instagram.com/miku_biggest_glazer)
