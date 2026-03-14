# Inversion Coach (Android, Kotlin, Compose)

Inversion Coach is an on-device real-time posture/mechanics coach for inversion drills, optimized around side-view wall-supported handstand and pike progressions.

## MVP highlights
- Single-activity app with Compose + MVVM/UDF architecture
- Side-view-first drill analysis profiles (each drill has its own metric/fault/cue priorities)
- CameraX integration points for preview + analysis + recording
- MediaPipe Pose Landmarker integration scaffolding
- Deterministic rule-based cue engine with issue persistence and cooldowns
- Debug overlay toggle for raw metrics, angles, and confidence
- Room persistence for sessions, settings, per-frame metrics, and issue timeline events
- WorkManager hook for background retention/cleanup tasks
- Local summary + recommendation generation (no cloud dependency)

## Project structure
- `camera/` camera session orchestration
- `pose/` pose landmarker and smoothing
- `overlay/` live skeletal + ideal line overlays
- `biomechanics/` drill profiles, metrics, scoring, fault classification
- `coaching/` cue prioritization + voice coach
- `recording/` MediaStore recording
- `storage/` Room DB + repository + service locator
- `summary/` local summary and recommendation engines
- `history/` background worker and history-related infra
- `ui/` Compose screens, components, navigation
- `model/` shared domain models

## Setup
1. Open in Android Studio (Ladybug+ recommended).
2. Let Gradle sync.
3. Add `pose_landmarker_lite.task` under `app/src/main/assets/`.
4. Run on a Pixel device (Pixel 10 target profile).
5. Grant camera permission.


## Installable testing builds (local signed APK)

This project is now configured for local, installable testing builds without any Play Store publishing setup.

### 1) Create a local test keystore (one-time)
Use a stable keystore so future APKs are signed with the same key and can update the already-installed app:

```bash
keytool -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore ~/inversioncoach-testing.jks \
  -alias inversioncoach \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### 2) Configure local signing secrets (do not commit)
Add the signing values to your `~/.gradle/gradle.properties` (recommended) or project `gradle.properties` on your machine:

```properties
RELEASE_STORE_FILE=/absolute/path/to/inversioncoach-testing.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=inversioncoach
RELEASE_KEY_PASSWORD=your_key_password
```

> If these values are not present, `release` builds fall back to the debug signing key so build output remains installable for local-only testing.

### 3) Versioning for iterative testing
`versionCode` and `versionName` are now driven by Gradle properties:

- `APP_VERSION_CODE` (integer, must increase for updates)
- `APP_VERSION_NAME` (string label shown in device/app info)

Default values are defined in `gradle.properties`. Override them per build to create replaceable update APKs.

### 4) Build from Android Studio
1. Open **Build Variants** and select `release` for app module (or keep default and use signed APK wizard).
2. Run **Build > Generate Signed Bundle / APK...**
3. Choose **APK** and use your local test keystore.
4. Confirm or bump version values (`APP_VERSION_CODE`, `APP_VERSION_NAME`) before building next update APK.

### 5) Build from Gradle (CLI)

```bash
./gradlew clean :app:assembleRelease
```

Or override version values for an update build:

```bash
./gradlew :app:assembleRelease \
  -PAPP_VERSION_CODE=2 \
  -PAPP_VERSION_NAME=1.0.1
```

APK output:

```
app/build/outputs/apk/release/app-release.apk
```

### 6) Install on a physical device
Transfer APK to device and install normally, or install over USB:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

- `-r` replaces existing app while preserving app data when signatures match.
- Future builds will install as updates as long as:
  - `applicationId` stays `com.inversioncoach.app`
  - signing key stays the same
  - `versionCode` increases

## Notes on current MVP implementation
- Analysis, UI, and persistence flows are deterministic and local-first.
- Per-frame metrics and issue events are stored for replay/timeline analysis.
- `PoseAnalyzer.analyze()` still requires concrete `ImageProxy -> MPImage` conversion and async detect invocation for full live MediaPipe inference.
- Annotated video export pipeline is scaffolded; raw MediaStore recording path is implemented.

## Next steps
- Complete MediaPipe live stream frame conversion and timestamped `detectAsync` loop.
- Connect stored per-frame metrics into replay timeline and frame thumbnails.
- Persist complete session lifecycle from `LiveCoachingViewModel` to `SessionRecord` on stop.
- Add freestanding handstand as a separate drill mode/profile in a future release.
