# AGENTS.md

Repository instructions for coding agents working on the Expense Tracker Flutter app.

## Project Overview
- Flutter app for tracking expenses.
- Target platform: Android (APK), Android 15+ target SDK (`targetSdk 35`).
- Primary artifact published to GitHub Releases: `expense_tracker.apk`.

## Build / Test / Verify (run only in GitHub Actions)
- Never assume a local Flutter SDK or Android SDK is available.
- All verification commands must be executed in GitHub Actions, not locally.
- Commands that must run in CI:
  - `flutter pub get`
  - `flutter analyze --fatal-infos`
  - `flutter test --coverage`
  - `flutter build apk --release`
  - `apksigner verify build/app/outputs/apk/release/expense_tracker.apk`

## CI Lessons
- CI is the source of truth for build / test / lint / release.
- Use named parallel jobs with clear dependencies: `lint` + `test` in parallel, then `build`, then `release`.
- Do not chain all phases in one job; split them so the independent quality gates run concurrently.
- The final APK must be named `expense_tracker.apk`.

## Android Configuration
- `compileSdk` and `targetSdk`: 35
- `minSdk`: 26
- Use `flutter build apk --release` for APK generation.
- Signing config is defined in `android/app/build.gradle.kts` via `android/key.properties`.

## GitHub Releases
- Releases are created with tags (`v*.*.*`).
- Release assets must include the signed APK named `expense_tracker.apk`.
- Use `softprops/action-gh-release@v2` or `gh release create` + `gh release upload`.

## App Icon
- Adaptive icon must support all Android DPIs.
- Provide mipmap densities: `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`.
- Source art: `assets/icon_1024.png`.

## Git Conventions
- Use conventional commit style: `feat:`, `fix:`, `chore:`, `ci:`, `docs:`.
- Keep release changes isolated in their own commit/PR; publish releases only on tags.
