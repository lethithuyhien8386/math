#!/usr/bin/env bash
set -euo pipefail
APK='app/build/outputs/apk/debug/app-debug.apk'
test -s "$APK" || { echo 'Missing or empty APK'; exit 1; }
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then echo 'Missing ANDROID_HOME/ANDROID_SDK_ROOT'; exit 1; fi
TOOLS="$SDK/build-tools/35.0.0"
"$TOOLS/aapt" dump badging "$APK" | tee /tmp/math-apk-badging.txt
python3 - "$APK" <<'PY'
import sys, zipfile, re
apk = sys.argv[1]
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None, 'APK has a corrupted ZIP entry'
    names = set(z.namelist())
    for expected in ('AndroidManifest.xml','classes.dex','resources.arsc'):
        assert expected in names, f'Missing {expected}'
s = open('/tmp/math-apk-badging.txt', encoding='utf8').read()
package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", s, re.M)
assert package, 'Cannot extract APK package/version using aapt'
assert package.group(1)=='com.example.automath', f'Wrong application ID: {package.group(1)}'
print('Valid package/version from built APK:', package.groups())
assert "sdkVersion:'26'" in s, 'Unexpected minimum SDK level'
PY
"$TOOLS/apksigner" verify --verbose --print-certs "$APK"
echo 'APK is structured and signed correctly.'
