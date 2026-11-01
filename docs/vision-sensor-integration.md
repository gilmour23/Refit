# Photo measurement and sensor integration

Source branch: feature/vision-sensor-integration-20261101, based on wrist fitting branch 1b52cc8. Existing demo registration and exercise records are retained. No authentication/cloud-sync changes.

## Use

- First setup/new brace: choose left/right, select original top and side JPG/PNG. Real photos open automatic measurement directly; no manual corner or wrist selection.
- Enter the measurement server URL once. The app sends original bytes/EXIF as multipart `top_image`, `side_image`, `return_report=true` with a unique session ID. No `card_*` or `wrist_*` overrides.
- Eleven positive finite dimensions and a readable report are needed to register. Warnings require acknowledgement. Error/partial results never become example results. Reports can be pinch-zoomed. Registration persists encrypted dimensions by owner, without report/body photographs; the report is shown during the current result session. The independent Python server retains its input photos/results.
- Registered measured dimensions are shown in 내 보조기. They do not become a validated 3D shell or completed physical brace. Existing illustrative STL remains only in the explicitly selected example path.
- Home → 센서 연결: grant permissions, enable Bluetooth, search, select an actually observed BLE device. Enter the firmware's service UUID and notify/indicate characteristic UUID, and acknowledge signed INT16 little-endian packets. No UUIDs, calibration factors, µV conversion, or motion classification are invented. Other wire formats need a matching parser from the hardware team.
- Device connection uses GATT service discovery and notification subscription; RSSI uses actual GATT reads. Failures remain failures. Source choice applies to exercise and its saved data_mode; demo is explicit and uses a separate labeled source. Device transport settings persist; reconnect/select is explicit after app restart.

## Measurement server

The `vision/` Python files are copied from the supplied 1101 measurement package. They add validated unique session IDs, reject duplicate IDs rather than stale result reuse, and reject incomplete/truncated cross sections. Automatic card and hand algorithms otherwise remain as supplied. Accuracy is unvalidated beyond the team's original samples.

Use the measurement team's working Python environment. Copy `models/hand_landmarker.task` from the supplied ZIP into `vision/models/`; copy a Korean font into `vision/fonts/` for Korean reports. Binary model/fonts are not duplicated in git.

```bash
cd vision
python -m pip install -r requirements.txt
python -m uvicorn server:app --host 127.0.0.1 --port 8000
```

Android emulator on that PC: debug build measurement URL `http://10.0.2.2:8000`. For a physical phone, run the server on a reachable trusted LAN interface and use that PC's reachable address (debug only), or use an HTTPS endpoint. Release builds retain HTTPS-only networking. This work does not deploy a public server. Do not expose this unauthenticated development measurement service publicly: it stores body photos and exposes report/session read endpoints.

You can also set `VISION_SERVER_URL` in local.properties as the initial value; runtime settings take precedence. No account/cloud settings are required for local app use.

## Validation limits

Android tests exercise loopback HTTP with controlled responses, original-byte upload, strict dimension parsing, report-gated registration, encrypted owner storage, mode/connection failure, and late native callbacks. They do not establish measurement accuracy or real sensor firmware compatibility. Real photos and live BLE hardware remain device checks after the team's server and firmware are available.
