# Contributing to Cobalt Wolf

Thanks for your interest! Cobalt Wolf is GPL v3 — all contributions are
licensed under it, and all code must be written originally (reading other
projects for inspiration is fine; never copy their code).

## Conventions

`AGENTS.md` is the source of truth for architecture and process rules. The
short version:

- **Kotlin only**, MVVM with a repository layer, coroutines + Flow for async.
- Jetpack Compose for phone/tablet, Compose for TV for televisions.
- Small, focused pull requests — one concern each.
- Unit tests for business logic; run `./gradlew test` before submitting.
- Requires JDK 17+ and Android SDK platform 37. CI runs
  `./gradlew test assembleDebug`.
- Never commit `dev.credentials.properties` or copy credentials into code,
  tests, docs, logs, or PR text.

## Reporting bugs

Open an issue with your device model, Android version, Jellyfin server
version, and what you were playing. The in-app player stats overlay values
help a lot for playback issues.
