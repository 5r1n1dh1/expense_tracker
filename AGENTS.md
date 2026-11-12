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

## Field rules — persisted from the v1.1.x S23 / One UI 9 incident
These rules exist because violating them caused the app to fail to launch on a
Galaxy S23 running One UI 9 (Android 16), or broke CI. They are not optional.

### Launch-path / crash rules
1. `painterResource()` accepts ONLY VectorDrawable and raster drawables
   (PNG/JPG/WEBP). Drawing a `<shape>` gradient from Kotlin crashes the first
   composition on **every** device. Draw gradients inline with
   `drawWithContent { drawRect(linearGradient(...)) }` (Compose) or a direct
   `canvas.drawRect(Paint().apply { shader = LinearGradient(...) })`.
2. `enableEdgeToEdge()` must run AFTER `super.onCreate()`, never before.
3. `android:windowBackground` must be a `<color>`, never a `<shape>` gradient.
4. Do not hardcode `android:windowLightStatusBar`; let the theme derive it.
5. Application-level work runs through a Hilt `Configuration.Provider`
   `workManagerConfiguration` with `HiltWorkerFactory`; Workers use
   `@HiltWorker @AssistedInject`. Never `WorkManager.getInstance()` in
   `onCreate`. The `WorkManagerInitializer` is removed via
   `androidx.startup` provider + `tools:node="remove"`.
6. Any Hilt binding used by the first composition or by `@HiltWorker`s must be
   `@Provides`'d (e.g. OutboxDao, QuotaGovernor, FirebaseFirestore,
   RemoteLedger). A missing binding fails at build/lint time on CI — there is
   no local compiler.
7. Manifest: keep `QuickAddActivity` non-exported, `standard` launchMode, no
   `taskAffinity`; declare the Glance `GlanceReceiver` (with
   `APPWIDGET_UPDATE` intent-filter + provider meta); every activity/service
   is explicit-none.
8. `tools:targetApi="35"` suppressions are banned — targetSdk is 36 and lint
   must pass without overrides.

### Kotlin / Compose / DEX rules
9. `GlanceAppWidgetReceiver` (glance 1.2.0) is NOT generic; override
   `glanceAppWidget`, implement `override val ... = KharchWidget()`.
10. No reusable widget is complete without a declared receiver + provider
    meta; an undeclared widget silently drops updates.
11. androidTest method names must be legal DEX identifiers — no spaces.
    (Backtick-with-spaces works only on the JVM unit-test source set.)
12. `composeRule.waitUntil {}` exists; there is no `waitUntilExists`.
    Use `fetchSemanticsNodes()` inside the condition.
13. Hit the cold-launch window, not just components: `MainActivityLaunchTest`
    cold-launches `MainActivity` and asserts the dashboard first frame
    (`dashboard-chart-frame`, `dashboard-category-frame`). It needs
    `@HiltAndroidTest` + `HiltAndroidRule(order = 0)`.

### SDK / packaging rules
14. `compileSdk` 37, `targetSdk` 36 (Play/Android-16 requirement), `minSdk` 29
    (`BUILD_CONTRACT.md`). One source of truth: `gradle/libs.versions.toml`.
    Upgrading only one of the three creates silent One UI 9 breakage.
15. `targetSdk 36` activates AGP's 16 KB-page packaging: `classes.dex` and
    `.so` are stored UNCOMPRESSED. Do not re-compress dex — that is a Play
    requirement for Android 15+. The APK floor is ~8 MB and the CI size gate
    accounts for it. Measure with `stat -c%s`, never `du -k`.
16. AGP/Gradle/KSP must be upgraded together, verifying AGP's bundled Kotlin
    matches KSP (see the comment block in `gradle/libs.versions.toml`). Do NOT
    downgrade AGP/Gradle without re-verifying the KSP and built-in-Kotlin
    pairing — a mismatch surfaces as Kotlin compilation errors on CI only.
17. `versionCode` and `versionName` increase on EVERY user-visible build.
    Both use whole SemVer: patch bumps for config fixes, minor for features.

### Firebase / auth rules
18. `app/google-services.json` is committed deliberately (decision D3).
    It must contain BOTH clients (`com.srinidhi.kharch` and `.debug`) and a
    non-empty `oauth_client` before sign-in works. The Web SDK client
    (type 3) maps 1:1 onto `default_web_client_id` in `res/values/strings.xml` —
    change one, change the other.
19. Credential Manager needs the **Web client ID**, not the Android client ID.
20. Sign-in needs: Google provider enabled, SHA-1/SHA-256 registered for BOTH
    signing certificates (debug + release), OAuth consent screen configured
    (or test users registered while in Testing), Firestore database created,
    and rules deployed (`firestore.rules`).
21. The release keystore is never committed; the CI `KEYSTORE_BASE64` secret is
    the only authority. Print its cert digests (`apksigner verify
    --print-certs`) and register those SHAs in the Firebase console for
    release-build sign-in.
22. `google-services.json.README.md` records what is wired and what is still
    missing; keep it current whenever the Firebase setup changes.

### CI rules
23. GitHub Actions only — never run Gradle locally.
24. `lint` and `test` run in parallel; `build` waits on both (faster log
    capture than a single chained job). Upload APK artifacts BEFORE the size
    gate so a failing gate yields an inspectable APK.
25. `gradlew` and `scripts/*.sh` are committed with the executable bit
    (`100755`); tooling that rewrites them stripped it once already — check
    `git ls-files -s gradlew scripts/` after any automated rewrite and
    restore with `git update-index --chmod=+x`.
26. The release workflow is tag-triggered (`v*`): build signed release →
    `apksigner verify` → `softprops/action-gh-release@v2` with asset
    `expense_tracker.apk` → provenance (needs `id-token: write` +
    `attestations: write` permissions). Never release from a dirty or red HEAD.
27. CI artifact upload of instrumented results is mandatory — an emulator run
    reported nothing until `connectedDebugAndroidTest` results were uploaded.
28. Dependabot PRs go red on base-change; merge only after CI green; they are
    inputs, not green-light approvals.
29. A tag created on a red HEAD republishes a broken APK. Always verify
    `Build & Unit Test` and `Instrumented Tests` are green on the exact tagged
    commit before pushing the tag; tag, push, then verify the release asset
    and its digest.
30. `google-services.json` deleted → the `com.google.gms.google-services`
    plugin fails configuration for every Gradle invocation; CI goes red even
    if the rest is correct. Never delete it without removing the plugin first.

