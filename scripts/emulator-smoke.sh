#!/usr/bin/env bash
set -euo pipefail
APK='app/build/outputs/apk/debug/app-debug.apk'
PKG='com.example.automath'
ACTIVITY="$PKG/.MainActivity"
adb wait-for-device
adb install -r "$APK"
INSTALL=$(adb shell pm path "$PKG" | tr -d '\r')
[[ "$INSTALL" == package:* ]] || { echo 'APK did not install'; exit 1; }
LAUNCH=$(adb shell am start -W -n "$ACTIVITY" | tr -d '\r')
echo "$LAUNCH"
grep -q 'Status: ok' <<<"$LAUNCH" || { echo 'Launcher Activity did not start'; exit 1; }
sleep 4
PID=$(adb shell pidof "$PKG" | tr -d '\r')
[[ -n "$PID" ]] || { echo 'Application died after launch'; adb logcat -d -t 200; exit 1; }
FOREGROUND=$(adb shell dumpsys activity activities)
grep -q "$PKG" <<<"$FOREGROUND" || { echo 'Activity not found in ActivityManager'; exit 1; }
echo "Successful launch: $PKG (pid $PID)"
# Permissions and screen sharing require human confirmation; this smoke test
# explicitly checks installation and opening MainActivity, not automatic taps.
