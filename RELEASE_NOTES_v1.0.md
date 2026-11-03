# Miku Connect v1.0

Miku Connect keeps your Android phone and Linux or Windows computer connected over your local Wi-Fi or LAN. Share clipboard text, mirror notifications, transfer files, and check connected-device battery and network latency without a cloud account or relay.

This release includes Android and desktop builds. The project is inspired by [KDE Connect](https://invent.kde.org/network/kdeconnect-kde).

## Downloads

Download the build for your device from the release assets:

| File | Platform | Description |
|---|---|---|
| `Miku-Connect v1.apk` | Android | Installable Android application package. Requires Android 10 (API 29) or newer. |
| `Miku-Connect v1.AppImage` | Linux | Linux desktop application. Make the file executable before launching it. |
| `Miku-Connect-Setup v1.exe` | Windows | Windows setup executable. |
| `Miku-Connect v1.exe` | Windows | Additional Windows executable build. |

### SHA-256 checksums

Use these checksums to verify the downloaded files:

```text
a742c20ee4a1a0f86f746c96f733c88a970640d4fcd3371f3ded82821389c387  Miku-Connect-Setup v1.exe
d5276128ced2466029f75d98b292265504857e86fd455204ee2852bec99ec7aa  Miku-Connect v1.apk
f6b83fda107d33d3694505c1be471853f6ee60f2e8b1fed1497ff1e77ba32a4c  Miku-Connect v1.AppImage
3eecaaa4c2e4254fe00e840045ae4418a9e70a371a1541388e9b967c0af9cc39  Miku-Connect v1.exe
```

## Highlights

- **Clipboard sync:** Send text between desktop and Android. Desktop-to-phone sync is automatic; Android can send from the Quick Settings tile, persistent notification, Share menu, or optional automatic clipboard sending.
- **Notification mirroring:** Forward Android notifications to connected devices. Linux desktop notification forwarding is available when `dbus-monitor` is installed.
- **File and folder transfer:** Send files and folders in either direction, monitor progress, cancel transfers, and resume interrupted transfers.
- **Nearby-device discovery and pairing:** Discover devices on the local network and pair them using a code that is not transmitted over the network. Connect by IP is available when network discovery is blocked.
- **Battery and latency:** See connected devices' battery status and encrypted-link latency.
- **Optional remote commands:** Run commands from a paired Android device on Linux or Windows. This capability is off by default and has guard rails; it is not a security sandbox.
- **Local and encrypted:** Device communication stays on your LAN and uses AES-256-GCM encryption with a key derived from the pairing code.

## Getting started

1. Install the Android APK or run the desktop build for your operating system.
2. Connect the phone and computer to the same Wi-Fi/LAN and open Miku Connect on both.
3. Open **Devices**, select the nearby device, and enter the displayed pairing code on the other device.
4. Allow the Android notification and background-service permissions you want to use.

If devices cannot discover each other, check firewall settings and allow TCP port **48998** and UDP port **48999**. On a network that permits local device connections, you can also use **Connect by IP**.

For Linux AppImage, mark the downloaded file executable, then launch it:

```bash
chmod +x "Miku-Connect v1.AppImage"
./"Miku-Connect v1.AppImage"
```

## Testing

The author has tested the app on Android and Arch Linux, using GNOME and niri on Wayland. Testing on Arch Linux rolling, Windows 11, Android 16, and Debian 13 is planned for future releases; those environments are not claimed as tested for this release.

## Notes

- Android-to-desktop clipboard sending is manual unless optional automatic sending is enabled. Android restricts background clipboard reads.
- Linux desktop notification forwarding requires the system-provided `dbus-monitor` utility.
- Remote commands are disabled by default. Enable them only on a trusted network and when needed.
- Review the project [README](README.md) for setup details, permissions, troubleshooting, and protocol information.

## Credits and license

Miku Connect is inspired by [KDE Connect](https://invent.kde.org/network/kdeconnect-kde). The project is dual-licensed under [MIT](LICENSE-MIT) or [GPL-3.0-or-later](LICENSE-GPL-3.0). Third-party components retain their own licenses; see [`THIRD-PARTY-NOTICES.txt`](THIRD-PARTY-NOTICES.txt).
