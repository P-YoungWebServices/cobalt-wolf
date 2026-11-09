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

- Gradle project is added in the M1 scaffold prompt. Until then, the CI
  workflow skips cleanly.
- Once present: `./gradlew test` and `./gradlew assembleDebug` are the
  standard verification commands.
