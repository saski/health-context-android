# Local Android development

Android Studio is the primary development environment. The repository includes
the Gradle Wrapper so local builds and GitHub Actions use the same Gradle
version.

## Prerequisites

- Android Studio with its bundled Java runtime.
- Android SDK Platform 36.1 and Build Tools 36.0.0.
- Android SDK Platform Tools for ADB.
- USB debugging enabled and authorized on the phone.

Android Studio can install missing SDK packages during the first project sync.
Machine-specific files such as `local.properties` and `.idea/` are ignored.

## Verify locally

From the repository root on macOS:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Build the debug APK:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the phone

The normal path is Android Studio's **Run app** action. The equivalent CLI path
is:

```bash
"$HOME/Library/Android/sdk/platform-tools/adb" install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

The first local build may not replace a build previously signed by Google AI
Studio. If ADB reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall the old
build once and install again. Uninstalling removes the app's local settings, so
Health Connect permissions, the selected Drive folder, automatic export and the
nightly review must be configured again. Daily Markdown files already stored in
Drive are not removed.

After this one-time transition, Android Studio and CLI builds use the stable
debug key in `~/.android/debug.keystore`, so subsequent `Run` or `adb install -r`
operations update the app without resetting its configuration.

## Source-of-truth workflow

- `main` is the source of truth.
- Make and verify changes locally, then commit and push them to `main`.
- Google AI Studio is optional; when used, pull from and push to the same
  repository instead of transferring ZIP files.
