# AGENTS.md

Repository instructions for coding agents working on Kharch (native Kotlin/Compose app).

## Project Overview
- Native Android app (Kotlin + Compose), package `com.srinidhi.kharch` — NOT Flutter.
- Target: Android 15+ devices (API 35+ tested). `compileSdk` 37, `targetSdk` 36 (Android 16 / Galaxy S23 One UI 9), `minSdk` 29 per `BUILD_CONTRACT.md` and `gradle/libs.versions.toml`.
- Primary artifact: `expense_tracker.apk` (signed, on GitHub Releases).

## Build / Test / Verify — GitHub Actions ONLY
- Never run Gradle locally. CI is the only compiler.
- All verification commands run in GitHub Actions (`.github/workflows/`):
  - `lint` job: `./gradlew lintDebug`, `./gradlew detekt`, grep guards (no destructive migrations, no telemetry)
  - `test` job: `./gradlew testDebugUnitTest`
  - `build` job (needs `lint` + `test`): `./gradlew assembleDebug`, `assembleRelease`, APK size gate
  - `instrumented.yml`: `./gradlew connectedDebugAndroidTest` on API 34/35/36 emulators (cold-launch smoke test is the S23 regression test)
  - `release.yml`: signed release on `v*` tags + `apksigner verify`

## CI Lessons
- Split jobs — `lint` + `test` in parallel, then `build`, then `release`. Do not chain everything into one job; parallel jobs give fast log capture.
- Final APK must be named `expense_tracker.apk` (rename step in `release/apk-rename.sh`).

## Android Configuration
- `compileSdk`: 37, `targetSdk`: 36, `minSdk`: 29 (see `gradle/libs.versions.toml` — single source of truth).
- Release signing via `android/key.properties` / `keystore.properties` (CI-decoded, never committed).
- AGP 9.3.1 built-in Kotlin — no `kotlin-android` plugin. Toolchain pinned in `BUILD_CONTRACT.md`.

## GitHub Releases
- Tags `v*.*.*`, release assets include signed `expense_tracker.apk`.
- Use `softprops/action-gh-release@v2` or `gh release create` + `gh release upload`.

## App Icon
- Adaptive icon `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` (monochrome included for One UI themed icons).
- Legacy PNG fallbacks in `mipmap-mdpi…xxxhdpi` still TODO — `mipmap-anydpi-v26` only ships today.
- Source art: `assets/icon_1024.png` (keep in sync).

## Git Conventions
- Conventional commits: `feat:`, `fix:`, `chore:`, `ci:`, `docs:`.
- Keep release changes isolated; publish releases only on tags.
