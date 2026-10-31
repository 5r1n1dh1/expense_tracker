# Plan: Expense Tracker App v1.1.0 — CI-green, signed APK on GitHub Releases

Persisted implementation plan for the current repo only. It is intentionally agentic: phases are contractible, each phase includes a verification command and a fail signal, and nothing requires a human to be present except for secrets and the final tag push.

## 1. Goal

- Reach a state where the CI gates can run clean on the main branch head.
- Publish a working signed expense_tracker.apk to GitHub Releases under tag v1.1.0.
- Honor AGENTS.md: Android 15+ target (compileSdk 35, targetSdk 35, minSdk 26), all verification in GitHub Actions, parallel CI phases, release name expense_tracker.apk, adaptive icon across all DPIs, conventional commits.

## 2. Current Status (observed from the repo + GH CLI)

- Branch: main — head 4c26ed687b7ba01314e30c76d2fbcf6928b85c07.
- Repo: 5r1n1dh1/expense_tracker, public, default branch main, no release yet.
- GitHub Actions workflows present: build.yml, instrumented.yml, release.yml.
- Recent CI: nightly scheduled runs of Instrumented Tests are failing; the latest observed failed run is 41187309814 (API 36 matrix).
- Last dispatch of Build & Unit Test on that same head also failed (40174019259).
- Secret set exists: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.

## 2.1 Current CI failure mode (the real, observed blocker)

- Build and instrumented jobs are failing at Kotlin compilation of app:compileDebugKotlin.
- Exact unresolved references observed in the failed run:
  - app/src/main/java/com/srinidhi/kharch/ui/auth/LinkAccountFlow.kt:4:49 — GetSignInWithGoogleOption
  - app/src/main/java/com/srinidhi/kharch/ui/auth/LinkAccountFlow.kt:31:30 — GetSignInWithGoogleOption
  - app/src/main/java/com/srinidhi/kharch/ui/navigation/AppNavHost.kt:136:29 — setSaveState
  - app/src/main/java/com/srinidhi/kharch/ui/navigation/AppNavHost.kt:155:29 — setSaveState
  - app/src/main/java/com/srinidhi/kharch/ui/navigation/AppNavHost.kt:174:29 — setSaveState
  - app/src/main/java/com/srinidhi/kharch/ui/navigation/AppNavHost.kt:193:29 — setSaveState
  - app/src/main/java/com/srinidhi/kharch/widget/KharchWidget.kt:43:29 — ColorProvider overload mismatch
  - app/src/main/java/com/srinidhi/kharch/widget/KharchWidget.kt:49:29 — ColorProvider overload mismatch
- Those compile-time errors make the app unbuildable, so a release APK cannot be published until the source tree compiles in CI.

## 3. Scope for this plan (v1.1.0)

1. Fix the compile-time blockers first. Until ./gradlew compileDebugKotlin compiles in CI, nothing else matters.
2. Make the repo consistent with AGENTS.md in terms of SDK targets and the expected release artifact name.
3. Ensure the signed APK is the one released, named expense_tracker.apk.
4. Tag and release v1.1.0 from main and verify the release asset exists.

Do not expand scope beyond getting a compilable, buildable, releasable tree for v1.1.0.

## 4. What each agentic phase must deliver

- A phase is done only when the relevant verification command can be run in CI and the phase declares its own pass/fail signal.
- Phases can run in parallel if they do not depend on each other's outputs.
- Any phase that touches source code must keep ./gradlew :app:compileDebugKotlin compilable.

### Phase A — Portability fixes (compile blockers)

Purpose: make compileDebugKotlin succeed for the main sources currently in the repo.

Work:
- Remove references to symbols that do not exist in the current dependency set, or replace them with the correct API the module actually exposes.
- Specifically:
  - `LinkAccountFlow.kt`: remove direct use of `GetSignInWithGoogleOption` from this file.
  - `AppNavHost.kt`: remove use of `setSaveState` inside `popUpTo` lambdas; keep the navigation behavior but within the API the current Navigation Compose version actually supports.
  - `KharchWidget.kt`: fix `ColorProvider` usage to match the `androidx.glance` API that is on the classpath.

Recent fix recorded in this file (do not re-edit unless CI still fails on the same symbols):
- `LinkAccountFlow.kt`: removed the direct `GetSignInWithGoogleOption` usage; delegated sign-in to the injected `GoogleSignInClient`.
- `AppNavHost.kt`: removed `setSaveState(true)` from all `popUpTo` extras.
- `KharchWidget.kt`: changed `androidx.glance.unit.ColorProvider` to `androidx.glance.color.ColorProvider`.

Verification:
- Local pre-check (optional, only if a platform is available): `./gradlew :app:compileDebugKotlin`.
- Real verification: CI `Instrumented Tests` or `Build & Unit Test` runs can compile the debug variant.

Fail signal:
- Any `compileDebugKotlin FAILED` in CI, or any `Unresolved reference` / overload mismatch in the compilation log.

### Phase B — SDK targets and release-artifact alignment

Purpose: the repo's Agp/Gradle/dependency configuration must match `AGENTS.md` targets for v1.1.0.

Work:
- If current config is not already `compileSdk 35`, `targetSdk 35`, `minSdk 26`, align it.
- Ensure the release variant produces an APK whose filename is `expense_tracker.apk`, not a variant-specific name like `app-release-unsigned.apk` or `app-release.apk`, by configuring the APK output name.

Verification:
- CI `assembleRelease` must produce `app/build/outputs/apk/release/expense_tracker.apk`.
- `AGENTS.md` commands are the source of truth; do not invent a different build path.

Fail signal:
- Release APK path/name is wrong, or SDK targets do not match the repo agreement.

### Phase C — Signed release and GitHub Release

Purpose: publish v1.1.0.

Work:
- Once the tree compiles and the release workflow is correctly configured, create tag `v1.1.0` and push it.
- Trigger or allow the `Release` workflow to run against that tag.
- Verify the release exists and its asset is a signed APK named `expense_tracker.apk`.

Verification:
- `gh release view v1.1.0` shows the asset.
- Signed APK verification is done by the release workflow itself (`apksigner verify`).

Fail signal:
- No release for `v1.1.0`, or release asset missing/wrong name, or signature verification step failed in CI.

## 5. CI architecture we are relying on

- `build.yml`: compile, unit tests, lint, detekt, APK size gate.
- `instrumented.yml`: instrumented tests on an emulator matrix.
- `release.yml`: signed release build + GitHub Release publish on tag push.
- Independent quality gates run in parallel; release depends on a tagged commit that has already passed compilation and tests.

## 6. Non-goals for v1.1.0

- Do not add new features.
- Do not refactor away existing behavior unless a compile blocker requires it.
- Do not change the release signing model; it is already wired to repo secrets.
- Do not assume a local Flutter/Android SDK exists; verify in CI.

## 7. Agent handoff notes

- When continuing this plan, always re-read the latest failed run logs before editing sources; the failure may already be fixed on a later push.
- If a planned edit introduces a new compile error, stop and fix that before moving on.
- Do not commit until the tree is at least compilable; a broken compile is the fastest way to keep CI red.
- Keep the plan file itself replaceable: if a later agent changes scope, update this file with the new observed status and fail signals, not prose about intent.
