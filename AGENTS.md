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

## Project layout

- `shared/` (Kotlin Multiplatform: `android` + `jvm` targets): models,
  Jellyfin SDK data sources, repositories, ViewModels and the Koin
  `sharedModule`. `commonTest` holds the unit tests. `androidMain` holds
  `KeystoreSessionStore` (Android Keystore AES-GCM token storage) and
  `androidPlatformModule` (creates the `Jellyfin` instance).
- `app/`: Android app (`com.pyoungws.bluewolfplayer`, minSdk 24), Jetpack
  Compose + Material 3 UI only; no business logic.
- Versions live in `gradle/libs.versions.toml`.

## Dependencies and licenses

All checked for GPL v3 compatibility:

- Jellyfin Kotlin SDK (`org.jellyfin.sdk`), LGPL-3.0. Keep attribution.
  Requires Jellyfin server 12.0+ (see `docs/SPEC.md`).
- Kotlin, kotlinx.coroutines, kotlinx.serialization (test only): Apache-2.0
- AndroidX (Compose, Material 3, Activity, Lifecycle, Core): Apache-2.0
- Koin: Apache-2.0

App icon (`app/src/main/res/drawable/ic_launcher_*.xml`, `mipmap-*`): original
artwork made for this project (low-poly wolf head), GPL v3 with the code. No
third-party images. The vector drawables are the source; the `mipmap-*dpi` PNGs
are rendered from the same shapes for API 24–25 launchers.
