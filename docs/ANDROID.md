# Android MVP

RelayBridge includes an early Android MVP under `android/`. It is a native Kotlin app that builds a debug APK for testing the Android VPN shell and certificate workflow.

This APK is not a production VPN yet. It does not currently forward packets to Google Apps Script. The packet data-plane is intentionally left disabled until the Android relay path is implemented and tested.

## What The MVP Includes

- Native Android app written in Kotlin.
- `VpnService` declaration and foreground service lifecycle.
- Inputs for `script_id` and `auth_key`.
- Local encrypted storage for `auth_key` using Android Keystore.
- Safe Proxy and MITM Preview mode selection.
- Local CA generation in app-private storage.
- CA export through Android sharing.
- Debug APK build in GitHub Actions.

## Current Limitations

- The APK is a buildable MVP, not a full traffic relay.
- Full-device routing is behind an internal disabled flag so the debug app cannot accidentally break device connectivity.
- UDP, QUIC, OpenVPN, WireGuard, SSH, games, and non-HTTP protocols are not relayed through Apps Script.
- Android does not allow a normal app to silently install a root CA. The user must install exported certificates manually from Android settings.
- Many Android apps do not trust user-installed CAs, and some use certificate pinning. MITM mode is therefore mostly useful for browsers or apps that explicitly trust user CAs.

## Build Locally

Install JDK 17 and Android SDK platform 35, then run:

```bash
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK is created at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

The `Android APK` workflow builds on:

- pushes to the `android` branch
- pull requests targeting `main`
- manual `workflow_dispatch`

The workflow uploads a temporary artifact named like:

```text
RelayBridge-android-debug-sha-<commit>
```

The artifact contains:

- `RelayBridge-android-debug-sha-<commit>.apk`
- `RelayBridge-android-debug-sha-<commit>.apk.sha256`

## GitHub Release Download

Version releases also publish the debug APK as a release asset:

```text
https://github.com/Hatef-Rostamkhani/relay-bridge/releases/download/vX.Y.Z/RelayBridge-android-vX.Y.Z-debug.apk
```

Verify it with the matching checksum:

```bash
sha256sum -c RelayBridge-android-vX.Y.Z-debug.apk.sha256
```

Release assets are better for normal downloads because they stay attached to the public release page. Actions artifacts are temporary and mainly useful for testing branch builds.

## Certificate Handling

The app can generate and export `ca.crt`, but Android trust installation must be done by the user. On most devices this is under security or encryption settings.

Only install this CA on devices you control. If the private CA key is exposed, remove the certificate from Android settings and generate a new CA.

## Next Data-Plane Work

The next implementation phase should add:

- packet parsing from the `VpnService` TUN interface
- TCP handling for ports 80 and 443
- QUIC/UDP 443 drop or fallback policy
- HTTP/HTTPS relay mapping to Google Apps Script
- clear failure handling when a protocol cannot be represented as Apps Script HTTP fetches
