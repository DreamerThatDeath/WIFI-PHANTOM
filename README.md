# WIFI PHANTOM

WIFI PHANTOM is a mobile-friendly Wi-Fi awareness website plus an Android WebView companion app with native Wi-Fi scanning.

Website: https://dreamerthatdeath.github.io/WIFI-PHANTOM/

## Android APK

The Android project is in `android/`. It loads the hosted website and exposes a small native bridge for live Wi-Fi scan results and Android settings shortcuts.

### Build an APK with GitHub Actions
1. Open the repository's **Actions** tab.
2. Select **Build WIFI PHANTOM APK**.
3. Choose **Run workflow** (or wait for a build after an Android project change).
4. Open the completed run and download the `WIFI-PHANTOM-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk` on your Android phone. Allow installation from the browser/file manager when Android asks.

The debug APK is for testing. A release APK for public distribution should be signed with a private signing key.

## Native Wi-Fi scanning
- Requests Android Location and Nearby Wi-Fi permissions when the user starts a scan.
- Requires Wi-Fi to be enabled and Android Location services to be on for scan results.
- Shows nearby SSID names, signal strength, frequency, and security type.
- Android may throttle scans; when that happens, the app displays the latest cached scan results.
- Android versions and device manufacturers can restrict scan frequency or hide some network names.

## Connection and router controls
- The app opens Android's own Wi-Fi settings so the user can select a network and enter credentials.
- It can open a private local router address in the browser. Router administration still requires the router's valid administrator login.
- It does not bypass Wi-Fi passwords, router logins, or other access controls.

## Website-only behavior
A normal website cannot scan nearby Wi-Fi radios. Outside the Android companion app, the radar remains clearly labelled demo data. The terminal is a harmless visual simulation and sends no packets.

## Build details
- Android application ID: `com.dreamerthatdeath.wifiphantom`
- Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9
- Minimum Android version: Android 6.0 (API 23)
