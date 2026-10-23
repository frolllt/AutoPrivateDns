# Auto Private DNS (Android)

Automatically turns **ON** a specific Android Private DNS hostname whenever you open any of your chosen apps, and turns Private DNS **OFF** as soon as you close/leave the app.

## Features
- **Choose Installed Apps**: Searchable list of all installed apps (with toggle for system apps).
- **Automatic Private DNS Switching**: Uses `Settings.Global` (`private_dns_mode` and `private_dns_specifier`) to toggle Android's native Private DNS without occupying your VPN slot.
- **Zero-Polling Foreground Detection**: Uses an `AccessibilityService` (`TYPE_WINDOW_STATE_CHANGED`) that ignores keyboards (IMEs) and system overlays (`com.android.systemui`) so Private DNS stays active while you type or adjust volume inside a selected app, and turns off when you exit to the launcher, switch to another app, or lock the screen.

## One-Time Setup
1. Build and install the app in Android Studio (or via `./gradlew installDebug`).
2. Grant `WRITE_SECURE_SETTINGS` once via ADB (or click **Grant via Root** inside the app if rooted):
   ```bash
   adb shell pm grant com.autoprivatedns android.permission.WRITE_SECURE_SETTINGS
   ```
3. Tap **Open Accessibility Settings** inside the app and enable **Auto Private DNS App Detector**.
4. Enter your desired Private DNS hostname (e.g. `dns.adguard-dns.com`, `one.one.one.one`, or your custom NextDNS endpoint) and check the apps you want!
