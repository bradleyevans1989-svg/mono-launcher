#!/bin/sh
# Builds dist/mono-launcher-<version>.apk.
#
# The app is plain Java with every view built in code, so it only needs javac, a dexer and
# tools/package_apk.py (pyaxml + cryptography). Point ANDROID_JAR at an API 35 android.jar and set
# either D8 (Android build-tools d8) or DX_JAR (com.jakewharton.android.repackaged:dalvik-dx).
# SIGNING_KEY is a PEM file holding the private key and certificate; without one a throwaway key
# is generated and the APK is only good for testing.
set -e
cd "$(dirname "$0")"
: "${ANDROID_JAR:?set ANDROID_JAR to an API 35 android.jar}"
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
rm -rf build && mkdir -p build/classes dist
javac --release 8 -nowarn -encoding UTF-8 -cp "$ANDROID_JAR" -d build/classes $(find src -name '*.java')
if [ -n "$D8" ]; then
  "$D8" --release --min-api 26 --lib "$ANDROID_JAR" --output build $(find build/classes -name '*.class')
else
  : "${DX_JAR:?set D8 or DX_JAR}"
  java -cp "$DX_JAR" com.android.dx.command.Main --dex --min-sdk-version=26 --output=build/classes.dex build/classes
fi
KEY_ARGS="--key ${SIGNING_KEY:-build/throwaway-key.pem}"
OUT="dist/mono-launcher-$VERSION.apk"
if [ -z "$SIGNING_KEY" ]; then
  KEY_ARGS="$KEY_ARGS --new-key"
  OUT="dist/mono-launcher-$VERSION-test-signed.apk"
fi
python3 tools/package_apk.py --manifest AndroidManifest.xml --dex build/classes.dex \
  --arsc res/prebuilt/resources.arsc --res res/drawable/ic_launcher.xml=res/prebuilt/ic_launcher.xml \
  --asset assets/countries.bin=assets/countries.bin $KEY_ARGS --out "$OUT"
echo "$OUT"
