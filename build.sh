#!/bin/bash
set -euo pipefail

JDK=${JDK:-/tmp/opencode/android/jdk}
export PATH="$JDK/bin:$PATH"
JAVA=${JAVA:-$JDK/bin/java}
BT=${BT:-/tmp/opencode/android/bt34/android-14}
AJ=${AJ:-/tmp/opencode/android/android-11/android.jar}
R8=${R8:-/tmp/opencode/android/r8.jar}
ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT"

rm -rf build
mkdir -p build/gen build/classes build/flat build/dex

echo "[1/7] aapt2 compile"
"$BT/aapt2" compile --dir res -o build/res.zip
unzip -qo build/res.zip -d build/flat

echo "[2/7] aapt2 link"
"$BT/aapt2" link -o build/app.unsigned.apk \
    -I "$AJ" \
    --manifest AndroidManifest.xml \
    --java build/gen \
    --min-sdk-version 21 \
    --target-sdk-version 30 \
    --version-code 1 --version-name 1.0 \
    build/flat/*.flat

echo "[3/7] javac"
javac -encoding UTF-8 --release 8 \
    -classpath "$AJ" \
    -d build/classes \
    build/gen/com/relaycoord/test/R.java \
    src/com/relaycoord/test/*.java

echo "[4/7] d8"
jar cf build/classes.jar -C build/classes .
"$JAVA" -cp "$R8" com.android.tools.r8.D8 \
    --lib "$AJ" --min-api 21 --output build/dex build/classes.jar

echo "[5/7] package dex"
(cd build/dex && zip -q ../app.unsigned.apk classes.dex)

echo "[6/7] zipalign"
"$BT/zipalign" -f 4 build/app.unsigned.apk build/app.aligned.apk

echo "[7/7] sign"
if [ ! -f build/debug.keystore ]; then
    keytool -genkeypair -keystore build/debug.keystore -storepass android -keypass android \
        -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out build/app.apk build/app.aligned.apk

ls -lh build/app.apk
echo "BUILD OK"
