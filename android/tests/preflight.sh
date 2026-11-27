#!/usr/bin/env bash
# Fast CI gate; no KataGo binary or model download needed.
set -euo pipefail
cd "$(dirname "$0")/../.."
src=android/src/org/gomasters/mobile
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/tests" "$work/classes"
javac -encoding UTF-8 --release 8 -d "$work/tests" \
  "$src/GameRecord.java" "$src/CoachText.java" "$src/BoardInsights.java" \
  android/tests/GameRecordTest.java android/tests/CoachTextTest.java android/tests/BoardInsightsTest.java
for test in GameRecordTest CoachTextTest BoardInsightsTest; do
  java -cp "$work/tests" "org.gomasters.mobile.$test"
done
python3 -m py_compile android/tests/device.py
bash -n android/build.sh android/native-x86.sh
sdk=${ANDROID_HOME:?Set ANDROID_HOME}
platform="$sdk/platforms/android-35/android.jar"
if [[ ! -f "$platform" ]]; then
  "$sdk/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35"
fi
javac -encoding UTF-8 --release 8 -cp "$platform" -d "$work/classes" "$src/"*.java
echo "Android Java sources compile successfully"
