# Android phone installation

Install the signed `ReFit-0.6.4-phone.apk` on an Android 8.0+ phone. Download it on the phone, open it and allow installation by that downloader/file app if Android asks. This is direct APK installation, not Google Play publication. The package is `com.refit.app.phone`; previous ReFit installations and their records are not removed or overwritten.

Start Re:Fit, register an example brace for the offline demo or select the original top/side photos for automatic measurement. Sensor connection is available from Home after registration. Android 12+ requests Nearby devices/Bluetooth permission; Android 11 or older uses the BLE-required location permission. No broad photo-library access is requested: use the system document picker.

## Actual photo measurement

The phone build requires an HTTPS measurement URL and the server's upload key in the measurement-server settings. The emulator address `10.0.2.2` is not a real-phone server address. The app does not deploy the server and does not perform computer vision on-device.

For the Python service, set `REFIT_VISION_TOKEN` to a random URL-safe secret of 32–256 characters. If the design team needs session/report downloads, configure an independent `REFIT_VISION_ADMIN_TOKEN`; do not distribute that administrator key to phone users. Generate keys locally with Python `secrets.token_urlsafe(48)` and supply them privately, never in git or app BuildConfig. Run the service behind an HTTPS endpoint; `/health` can check availability. The previously supplied model binary and Korean font are still required.

By default original input files are not retained. Generated report images and dimensions stay in the server's private output directory and require the administrative key for later downloads. For deliberate original-photo validation only, the operator can set `REFIT_VISION_KEEP_ORIGINALS=1`. Ensure the server operator handles report deletion/retention; there is no automatic deletion deadline in this version.

## Actual sensor

Use the firmware team's real service/notify UUIDs. The current parser is signed INT16 little endian raw values; acknowledge that format only if it matches the firmware. No microvolt calibration, medical classification or motor commands are inferred. The explicit demo mode works without a sensor or server.

## Building/signing again

`./gradlew :app:assemblePhone` produces an unsigned non-debuggable APK with cloud settings blank and HTTPS/token enforcement enabled. CI verifies the manifest and packages the aligned unsigned APK plus the Android signing tool. Sign locally with the same private `refit-phone` certificate for future upgrades; keep private key and password outside git and public Actions artifacts. Security review: `docs/verification/2026-11-05-phone-security.md`.
