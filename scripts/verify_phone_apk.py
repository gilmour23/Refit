"""Verify the generated APK, rather than trusting source build flags."""
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

apk = Path(sys.argv[1])
sdk = Path(os.environ["ANDROID_HOME"])
choices = list(sdk.glob("cmdline-tools/*/bin/apkanalyzer"))
assert choices, "Android APK analyzer is unavailable"
analyzer = next((p for p in choices if "/latest/" in str(p)), choices[0])
xml = subprocess.check_output([str(analyzer), "manifest", "print", str(apk)], text=True)
root = ET.fromstring(xml)
a = "{http://schemas.android.com/apk/res/android}"
app = root.find("application")
assert root.get("package") == "com.refit.app.phone"
assert app.get(a + "debuggable", "false") == "false"
assert app.get(a + "allowBackup") == "false"
assert app.get(a + "usesCleartextTraffic") == "false"
exported = [e.get(a + "name") for e in app if e.get(a + "exported") == "true"]
assert len(exported) == 1 and exported[0].endswith("MainActivity"), exported
permissions = [e.get(a + "name") for e in root.findall("uses-permission")]
assert not set(permissions) & {"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES", "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.CAMERA"}
for p in root.findall("uses-permission"):
    if p.get(a + "name") == "android.permission.ACCESS_FINE_LOCATION":
        assert int(p.get(a + "maxSdkVersion")) <= 30
with zipfile.ZipFile(apk) as z:
    for entry in z.infolist():
        assert not entry.filename.endswith((".jks", ".keystore", ".p12", "local.properties"))
        if entry.filename.endswith(".dex"):
            data = z.read(entry)
            assert b"public-test-key" not in data and b"test.supabase.co" not in data
result = {"package": root.get("package"), "debuggable": False, "backup": False, "cleartext": False,
    "exported_components": exported, "permissions": permissions,
    "min_sdk": root.find("uses-sdk").get(a + "minSdkVersion"), "no_ci_test_credentials": True}
print(json.dumps(result, indent=2))
Path("build/phone-security.json").write_text(json.dumps(result, indent=2))
