# CoveTV for Android TV — source code 3.2.4

This archive contains the Android launcher source corresponding to CoveTV 3.2.4 (version code 89).

## Build

Requirements: JDK 17 and Android SDK Platform 34. From this directory run:

```powershell
.\gradlew.bat assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. The build uses Gradle's local Android debug signing key. No signing keys are included. A locally built APK will have a different signing identity from CoveTV's published APK unless you already have the matching key, so it will not install as an in-place update of the published build.

## Project contents

- `app/src/main/` — launcher source, resources, wallpapers, icons, and bundled data
- `app/src/test/` — existing JVM tests
- `gradle/`, `gradlew`, and `gradlew.bat` — Gradle wrapper
- `LICENSE` and `THIRD-PARTY-NOTICES.md` — project and upstream license information
- `FONT-LICENSE-OFL.txt` — Outfit typeface license

The Android application ID remains `com.gathin.tvhome` so supported updates can install over earlier Cove/CleanYourTV builds.

## Licenses

CoveTV-specific original contributions maintained by PixelOTTO are licensed under MIT. CleanYourTV and Free My TV portions retain their upstream MIT notices. Outfit is licensed under SIL OFL 1.1. See the included license and notices files for details.
