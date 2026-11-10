# AGENTS.md

Conventions and rules for contributing to Blue Wolf Player.

## Language and architecture

- **Kotlin only.** No Java sources.
- **MVVM** architecture with a repository layer.
- **Kotlin coroutines and Flow** for all asynchronous work. No callbacks or
  RxJava.
- UI is Jetpack Compose (phone/tablet) and Compose for TV.
- Follow `docs/SPEC.md` for the product spec and milestone plan.

## Process

- **Small, focused pull requests.** One concern per PR; don't bundle unrelated
  changes.
- **Unit tests for all business logic.** ViewModels, repositories,
  use cases and any logic that can be exercised without Android framework
  dependencies should have tests. UI code may rely on instrumented tests
  later, but keep business logic testable in plain JUnit.
- Run `./gradlew test` before submitting a PR.

## Licensing (important)

- The project is licensed under **GPL v3** (see LICENSE).
- **All code must be written originally.** Reading other projects to learn
  how they solve a problem is fine, but never copy their code. If code is
  adapted from another source, note the source in a comment and confirm its
  license is compatible with GPL v3. GPL v2-only code is **not** compatible.
- **Every new dependency's license must be checked for GPL v3 compatibility**
  before adding it. Prefer well-established libraries; record any
  attribution requirements (e.g. FFmpeg LGPL linking rules) in this file or
  the relevant module's README.
- Do not reproduce JellyWatch's code, branding, icons or artwork. Reproduce
  capabilities only.
- Keep the Jellyfin disclaimer in README/distribution materials: Blue Wolf
  Player is a third-party app, not affiliated with or endorsed by the
  Jellyfin project.

## Build / verify

- `./gradlew test` and `./gradlew assembleDebug` are the standard
  verification commands (CI runs `./gradlew test assembleDebug`).
- `./gradlew test` runs the shared tests on both the JVM target
  (`:shared:jvmTest`) and the Android host target
  (`:shared:testAndroidHostTest`), plus `:app:testDebugUnitTest`.
- Requires JDK 17+ (`JAVA_HOME`) and an Android SDK with platform 37
  (`local.properties` `sdk.dir=...` or `ANDROID_HOME`). Locally, Android
  Studio's bundled JBR works: `JAVA_HOME=/snap/android-studio/current/jbr`.

## Local test credentials

- `dev.credentials.properties` (repo root, gitignored) holds a test Jellyfin
  `server`, `username` and `password` for manual testing on a device. The app
  never reads it; it is only for developers and agents driving the app by hand
  (e.g. via `adb`) or calling the Jellyfin API.
- **Never commit it, copy its values into code, tests, docs, logs or PR text, or
  print the password.** Read values from the file in shell commands only.
- If the file is missing or still has placeholder values, ask the user to fill it
  in rather than guessing credentials.

## Test devices

- **The owner's phone may be used for on-device testing** whenever it is
  attached over USB: a Nokia G50 (Android 13, arm64, HDR10/HLG 720p panel, no
  HDMI output). Check with `adb devices`; `adb` is at
  `~/Android/Sdk/platform-tools/adb` (not on `PATH`). Installing debug builds,
  pushing sample media to `/sdcard/Android/data/<app id>/files/` or
  `/data/local/tmp`, launching activities and reading logcat are all fine. Do
  not touch other apps or user data on it, and do not factory-reset, unlock or
  change system settings.
- A Fire Stick is tested over network adb. Its address is the `firestick.adb`
  key in `dev.credentials.properties`; if it is empty, ask the owner to fill it
  in. Connect with `adb connect <address>` and target it with `adb -s <address>`.
- With more than one device attached, always pass `adb -s <serial>`.

## Testing playback on a device

- Debug builds export `.player.DebugPlayerActivity` (plays `-d <uri>` or
  `--es item_id <id>`) and `.ui.settings.DebugPlaybackSettingsActivity`; see
  README "Testing playback from adb" for the extras. Everything of interest is
  logged under the `BlueWolfPlayback` tag: detected capabilities, fallbacks and
  a `stats` line (codec, decoder, HDR, audio output, dropped frames) 5 s into
  playback and every 15 s after.
- Files pushed with adb into the app's storage are not readable by the app on
  Android 11+. Serve samples over HTTP with Range support instead and, for a
  USB device, `adb reverse tcp:<port> tcp:<port>`.
- Free samples: https://repo.jellyfin.org/test-videos/ (H.264, HEVC, AV1,
  HDR10, Dolby Vision P5/P8). The owner's Jellyfin library has real 4K HDR10,
  Dolby Vision, TrueHD Atmos, DTS-HD MA, PGS and ASS items; find them through
  the API with the credentials file and play them by item id.

## Project layout

- `shared/` (Kotlin Multiplatform: `android` + `jvm` targets): models,
  Jellyfin SDK data sources, repositories, ViewModels and the Koin
  `sharedModule`. `commonTest` holds the unit tests. `androidMain` holds
  `KeystoreSessionStore` (Android Keystore AES-GCM token storage) and
  `androidPlatformModule` (creates the `Jellyfin` instance).
- `app/`: Android app (`com.pyoungws.bluewolfplayer`, minSdk 24), Jetpack
  Compose + Material 3 UI only; no business logic. `player/` holds the Media3
  glue (`PlayerEngine`, stats collector, display mode controller).
- Playback decisions live in `shared/.../playback`: `Capabilities.kt` (what was
  detected), `PlaybackSelection.kt` (best path per codec/HDR format and the
  fallback order), `DeviceProfiles.kt` (what the server is told),
  `DisplayModeMatcher.kt`. `androidMain` holds `AndroidCapabilityDetector` and
  the settings store.
- Versions live in `gradle/libs.versions.toml`.

## Dependencies and licenses

All checked for GPL v3 compatibility:

- Jellyfin Kotlin SDK (`org.jellyfin.sdk`), LGPL-3.0. Keep attribution.
  Requires Jellyfin server 12.0+ (see `docs/SPEC.md`).
- Kotlin, kotlinx.coroutines, kotlinx.serialization (test only): Apache-2.0
- AndroidX (Compose, Material 3, Activity, Lifecycle, Core): Apache-2.0
- Koin: Apache-2.0
- AndroidX Media3 (ExoPlayer, HLS, UI): Apache-2.0
- Coil 3 (image loading): Apache-2.0
- NextLib `nextlib-media3ext` (FFmpeg audio/video renderers for Media3):
  GPL-3.0. Bundles FFmpeg as shared libraries built **LGPL v3+** (no
  `--enable-gpl`/`--enable-nonfree`), with dav1d (BSD-2-Clause) and Mbed TLS
  (Apache-2.0). Its version is tied to the Media3 version
  (`<media3>-<nextlib>`); bump both together. Before bumping, check the
  binaries still say LGPL: `strings libavutil.so | grep license`. The README
  "Licensing" section carries the FFmpeg notice and must stay accurate.
- libass-android `ass-media` (ASS/SSA rendering for Media3): MIT; bundles
  libass (ISC), FreeType (FTL), FriBidi (LGPL-2.1+), HarfBuzz (MIT). It reaches
  into Media3's Matroska extractor by reflection, so retest ASS subtitles after
  any Media3 bump.

App icon (`app/src/main/res/drawable/ic_launcher_*.xml`, `mipmap-*`): original
artwork made for this project (low-poly wolf head), GPL v3 with the code. No
third-party images. The vector drawables are the source; the `mipmap-*dpi` PNGs
are rendered from the same shapes for API 24–25 launchers.
