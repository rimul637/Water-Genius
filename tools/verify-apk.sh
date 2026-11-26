#!/usr/bin/env bash
set -euo pipefail
apk="${1:?Provide the path to a signed APK}"
tools="${ANDROID_HOME:?Set ANDROID_HOME}/build-tools/36.0.0"
"$tools"/apksigner verify --verbose --print-certs "$apk"
"$tools"/zipalign -c -P 16 4 "$apk"
python3 - "$tools" "$apk" <<'PY'
import pathlib
import re
import subprocess
import tempfile
import sys
import zipfile

tools, apk = sys.argv[1:]
badging = subprocess.check_output([tools + "/aapt", "dump", "badging", apk], text=True)
for expected in (
    "name='com.watergenius.water' versionCode='1' versionName='1.0.0'",
    "sdkVersion:'26'",
    "targetSdkVersion:'36'",
):
    assert expected in badging, f"Missing {expected}"
assert not any(line.startswith("uses-permission") for line in badging.splitlines()), badging
manifest = subprocess.check_output([tools + "/aapt", "dump", "xmltree", apk, "AndroidManifest.xml"], text=True)
assert not re.search(r"^\s*E: (?:uses-permission(?:-sdk-\d+)?|permission)\b", manifest, re.M)
feature_sections = re.findall(r"^    E: uses-feature[^\n]*\n((?:^      A:[^\n]*\n)+)", manifest, re.M)
assert len(feature_sections) == 3, feature_sections
features = set()
for attributes in feature_sections:
    name = re.search(r'android:name\([^)]*\)="([^"]+)"', attributes)
    assert name and re.search(r'android:required.*\(type 0x12\)0x0$', attributes, re.M), attributes
    features.add(name.group(1))
assert features == {
    "android.hardware.screen.landscape",
    "android.hardware.screen.portrait",
    "android.hardware.touchscreen",
}, features
with zipfile.ZipFile(apk) as archive:
    native = [name for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")]
    with tempfile.TemporaryDirectory() as directory:
        for name in native:
            binary = pathlib.Path(directory) / "library.so"
            binary.write_bytes(archive.read(name))
            program = subprocess.check_output(["readelf", "-lW", str(binary)], text=True)
            alignments = [int(line.split()[-1], 16) for line in program.splitlines() if line.lstrip().startswith("LOAD ")]
            assert alignments and min(alignments) >= 16384, (name, alignments)
print("APK package, SDK levels, manifest features, permissions and native libraries checked.")
PY
