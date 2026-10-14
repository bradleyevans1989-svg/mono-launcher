# Mono Launcher

A minimal Android home screen: clock and date tiles, folders, a favourites row, a dock, a learning page, and **Mono**, an assistant on the home screen that runs on Claude.

## Mono, the home screen assistant

Tap the **Ask Mono anything** bar or the robot. Mono can answer questions (with web search), open apps, open websites, set timers and alarms, and add apps to folders. The mic button asks by voice using the phone's speech recogniser.

It uses your own Anthropic API key (Settings → Assistant). The key is encrypted with an Android Keystore key and only sent to `api.anthropic.com` when you ask something. Requests use `claude-opus-5-5` at low effort with server-side refusal fallbacks enabled.

## Building

The app is plain Java with every view built in code, so it doesn't need Gradle or the Android Gradle plugin. `build.sh` compiles with `javac`, dexes with `d8` (or the standalone `dx`), and `tools/package_apk.py` encodes the manifest, packs the prebuilt resources and signs with APK Signature Scheme v2.

```sh
pip install pyaxml cryptography lxml
ANDROID_JAR=$ANDROID_HOME/platforms/android-35/android.jar \
D8=$ANDROID_HOME/build-tools/35.0.0/d8 \
SIGNING_KEY=path/to/mono-release.pem ./build.sh
```

Without `SIGNING_KEY` the APK is signed with a throwaway key and named `-test-signed`. Android only installs updates signed with the same key, so keep the release key safe and out of this repository.

GitHub Actions (`.github/workflows/build.yml`) runs the Robolectric smoke tests in `smoke/`, builds the APK, checks it with `apksigner`, `zipalign` and `aapt2`, and uploads it as an artifact. Add the release key as the `MONO_SIGNING_KEY_PEM` repository secret to get release-signed builds.

## History

0.10.2 was rebuilt from its APK (the original source wasn't available). 0.15.2 adds the assistant, regroups settings, updates window inset handling for Android 15 and slows the robot's fact bubbles to every three minutes.
