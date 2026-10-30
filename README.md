# ARYAN VPN for Android

ARYAN VPN is an Android client built with Kotlin, Jetpack Compose, and the sing-box engine. The app source supports importing a VLESS profile, connection controls, DNS choices, and English/Persian resources.

## Build an APK automatically

Every push to `main` starts the GitHub Actions workflow. You can also run it manually:

1. Open the **Actions** tab in this repository.
2. Choose **Build AryanVPN APK**.
3. Open the newest workflow run and wait for it to finish.
4. Download the **AryanVPN-debug-apk** artifact.
5. Extract the ZIP and install `app-debug.apk` on your Android phone.

Workflow page: https://github.com/ecewoqomay506-design/newaryanvpn/actions/workflows/build-apk.yml

## First connection

1. Open ARYAN VPN and go to Profiles.
2. Import a fresh VLESS URI from a VPN server you trust.
3. Choose DNS in settings if you want a different resolver.
4. Tap Connect and approve Android's system VPN permission dialog.
5. To disconnect, use the app or Android's VPN notification.

No working server profile or secret is included in this repository. A successful build only proves that the APK compiled; actually connecting requires a valid server configuration and testing on an Android device.

## Engine, privacy, and licensing

The build workflow compiles sing-box v1.9.7 `libbox.aar` from source. sing-box is GPLv3-licensed, so review the upstream license and source/notice obligations before redistributing the app or APK. Do not commit VLESS URIs, UUIDs, passwords, private signing keys, or other credentials.

VPN apps route device traffic through a remote endpoint. Only import configurations from a server operator you trust. Android displays a system permission prompt before the app can start a VPN connection.

## Project layout

- `app/` — Android app source and resources
- `scripts/build-libbox.sh` — builds the pinned sing-box Android library
- `scripts/check-source.py` — source integrity checks
- `.github/workflows/build-apk.yml` — automated debug APK build
- `BUILD-ON-GITHUB.md` — build notes
