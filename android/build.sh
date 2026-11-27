#!/usr/bin/env bash
# Assemble a sideloadable offline APK with JDK 17+ and the Android SDK.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
sdk=${ANDROID_HOME:?Set ANDROID_HOME}
tools="$sdk/build-tools/35.0.0"
platform="$sdk/platforms/android-35/android.jar"
if [[ ! -f "$platform" || ! -x "$tools/d8" ]]; then
  "$sdk/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35" "build-tools;35.0.0"
fi
out="$root/android/out"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$out" "$work"/{assets/notices,classes,dex,lib/arm64-v8a}
cp external/native/katago-android/arm64-v8a/libkatago.so "$work/lib/arm64-v8a/"
if [[ -f external/native/katago-android/x86_64/libkatago.so ]]; then
  mkdir -p "$work/lib/x86_64"; cp external/native/katago-android/x86_64/libkatago.so "$work/lib/x86_64/"
fi
# Use the same published URLs and hashes as the verified desktop pair.
python3 - "$work/assets" <<'PY'
import sys
from pathlib import Path
from shutil import copyfile
sys.path.insert(0, "scripts")
import play
play.CACHE.mkdir(exist_ok=True, parents=True)
for spec in play.MODELS:
    source = play.fetch(*spec)
    copyfile(source, Path(sys.argv[1]) / source.name)
PY
cp configs/gtp-cpu.cfg "$work/assets/game.cfg"
cp android/licenses/* "$work/assets/notices/"
cp external/masame/src-tauri/resources/licenses/katago/* "$work/assets/notices/"
cp external/masame/src-tauri/resources/licenses/linux-engine/eigen-MPL-2.0.txt "$work/assets/notices/"
cp "$ANDROID_HOME/ndk/27.2.12479018/NOTICE" "$work/assets/notices/Android-NDK-NOTICE.txt"
"$tools/aapt2" link -o "$work/base.apk" -I "$platform" --manifest android/AndroidManifest.xml -A "$work/assets" -0 gz
javac -encoding UTF-8 --release 8 -cp "$platform" -d "$work/classes" android/src/org/gomasters/mobile/*.java
jar cf "$work/classes.jar" -C "$work/classes" .
"$tools/d8" --min-api 26 --lib "$platform" --output "$work/dex" "$work/classes.jar"
cp "$work/dex/classes.dex" "$work/"
(cd "$work" && zip -q -0 -r base.apk lib classes.dex)
"$tools/zipalign" -P 16 -f 4 "$work/base.apk" "$work/aligned.apk"
# Preview signing only. Supply an existing private keystore for updateable local builds.
key=${ANDROID_PREVIEW_KEYSTORE:-"$out/debug.p12"}
if [[ ! -f "$key" ]]; then
  keytool -genkeypair -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 3650 -keystore "$key" \
    -storepass android -keypass android -dname "CN=Go Masters Development"
fi
apk="$out/GoMasters-Android.apk"
"$tools/apksigner" sign --ks "$key" --ks-pass pass:android --ks-key-alias androiddebugkey --out "$apk" "$work/aligned.apk"
"$tools/apksigner" verify --verbose "$apk"
"$tools/zipalign" -c -P 16 4 "$apk"
"$tools/aapt2" dump badging "$apk" | head -30
(cd "$out" && sha256sum GoMasters-Android.apk > SHA256SUMS)
