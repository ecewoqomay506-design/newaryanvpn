# Build AryanVPN APK on GitHub Actions

1. Create a **private** repository on GitHub. Do not publish signing keys, server links, UUIDs, or passwords.
2. Upload the contents of this project folder to the repository's `main` branch, including the `.github/workflows/build-apk.yml` file.
3. Open the repository's **Actions** tab and select **Build AryanVPN APK**.
4. Click **Run workflow** → **Run workflow**.
5. Open the new run and wait for it to finish. If it fails, open the failed step and save/copy the error text.
6. If successful, open **Artifacts** at the bottom of the run and download `AryanVPN-debug-apk`. Extract the ZIP to get `app-debug.apk`.
7. Transfer `app-debug.apk` to your Android phone and install it. Android may ask you to allow installs from the app you used to open the file.

This workflow is an attempted build recipe, not a guarantee that the source compiles or the VPN connects. The first run may fail if the pinned engine's build requirements or this app's bridge API are incompatible; use the failed step's log to fix the actual error. A debug APK is for testing, not a signed production release.
