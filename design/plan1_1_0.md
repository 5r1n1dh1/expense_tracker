# plan1_1_0.md

# Plan: Expense Tracker — CI green + v1.1.0 release (agent handoff)

This plan is written so independent agents can pick up individual phases without coordinating. Each phase below is a self-contained unit with: **what to change**, **files that must be touched**, **how to verify**, and **acceptance criteria**.

Do not start the next phase until the previous phase's verifications pass, because later phases depend on those artifacts (APK, signed bundle, release tag).

---

## 1. Goal

- Make the repo's CI green for the release path.
- Publish a working signed `expense_tracker.apk` to GitHub Releases as `v1.1.0`.
- Honor `AGENTS.md`: `compileSdk`/`targetSdk` 35, `minSdk` 26, final APK named `expense_tracker.apk`, all verification runs in GitHub Actions only.

## 2. Agentic workflow (pick up by phase)

### Phase A — Fix build alignment to AGENTS.md + dependency reality

**Problem:** `checkReleaseAarMetadata` fails because `compileSdk` is 35 but the dependency graph now requires larger compile SDKs.

**Scope:**
- Keep `targetSdk = 35` and `minSdk = 26` exactly as AGENTS.md requires.
- Set `compileSdk` to the value the current dependency set actually requires. For this repo that is `compileSdk = 37`, driven by the current Compose BOM 2026.09.00 + activity 1.13.0 + core 1.19.1 + navigation 2.10.2 + lifecycle 2.11.0 + hilt-navigation-compose 1.4.0 dependency set.
- Do **not** raise `targetSdk` above 35 for this release; that is an AGENTS.md constraint.
- After the change, re-run the release workflow on the same tag and confirm the signed build step moves past AAR metadata checking.

**Files expected to change:**
- `gradle/libs.versions.toml` (compileSdk version + comment)
- `app/build.gradle.kts` only if it independently hardcodes a compileSdk (it currently reads from the version catalog)
- Release workflow only if it needs the same compileSdk assumption (it does not)

**This repo’s Phase A also included a real compile fix, because the release path must produce a working APK, not just pass AAR metadata:**
- `app/src/main/java/com/srinidhi/kharch/ui/auth/LinkAccountFlow.kt` — `startLink()` was calling `GoogleSignInClient.handleSignInResult(Intent())` and trying to return `LinkResult`; the wired API returns `GoogleSignInResult`. Fixed to use the real Credential Manager result mapping through `WorkspaceManager.ensureWorkspace()` / `currentWorkspace()`.
- `app/src/main/java/com/srinidhi/kharch/ui/navigation/AppNavHost.kt` — removed the leftover hand-written string route block that referenced nonexistent `NavigationType`, `Mode`, and wrong arg converters, and replaced it with a typed `composable<Routes.AddEdit>(...)` matching the in-tree `Routes.AddEdit` / `AddEditMode` API.
- `app/src/main/java/com/srinidhi/kharch/widget/KharchWidget.kt` — fixed `ColorProvider(...)` usage: it takes a `Color` plus a `night` color, not a `Long`.

**Verifications (must pass before Phase B):**
- Latest `release.yml` run on `v1.1.0` has `Build signed release` step green.
- Release workflow produces `app/build/outputs/apk/release/expense_tracker.apk`.
- APK signature check step is green in the same run.

### Phase B — Make the plan agentic and verifiable

**Problem:** Current plan is narrative prose; an agent cannot tell where it is, what done looks like, or what to verify.

**Scope:**
- Re-write `design/plan1_1_0.md` into the current structure: goals, discrete phases, per-phase scope, files touched, verification commands (all CI-only), and acceptance criteria.
- Add an explicit “current status” block at the top so the next agent can see what is done without re-reading the whole file.
- Add a short “definition of done” for the release: signed APK on GitHub Releases named `expense_tracker.apk`, and the release workflow green on the tag.

**Files expected to change:**
- `design/plan1_1_0.md`

**Verifications:**
- Plan file exists and is readable.
- Plan contains explicit per-phase verifications and acceptance criteria.
- Plan explicitly says all build/test/lint/release verification happens in GitHub Actions.

### Phase C — Create/update release and verify GitHub Releases

**Problem:** A v1.1.0 tag may exist, but no working signed APK has landed on GitHub Releases yet because CI failed.

**Scope:**
- After Phase A and Phase B pass, re-run the release workflow against the existing `v1.1.0` tag, or re-tag and push `v1.1.0` again if the release workflow is tag-driven.
- Confirm the release workflow publishes a GitHub Release and uploads `expense_tracker.apk` as an asset.
- Confirm the uploaded asset exists on the releases page for `v1.1.0`.

**Files expected to change:**
- Only git objects (commit/tag) if the release workflow requires a fresh tag.
- No workflow code should change in this phase unless Phase A already required it.

**Verifications:**
- `gh release view v1.1.0` shows the release and the APK asset.
- The APK asset name is exactly `expense_tracker.apk`.
- The release workflow run for that tag is green through the publish step.

---

## 3. Constraints (honored from AGENTS.md)

- All verification commands run in GitHub Actions; do not assume a local Flutter or Android SDK.
- Final artifact name: `expense_tracker.apk`.
- `compileSdk`, `targetSdk`: 35; `minSdk`: 26 — unless the build cannot pass at all without raising `compileSdk`, in which case raise **compileSdk only** and document the reason in the commit and in the plan.
- Signing config lives in `android/app/build.gradle.kts` via `android/key.properties`, and secrets for CI live as GitHub repo secrets.
- Use conventional commits: `feat:`, `fix:`, `chore:`, `ci:`, `docs:`.
- Keep release changes isolated and publish releases only on tags.

---

## 4. Current status

- **Phase A:** Not done. Latest `Signed release` job failed at `checkReleaseAarMetadata` because `compileSdk` 35 is too low for the current dependency set.
- **Phase B:** In progress. This file is being rewritten into the agentic structure you are reading now.
- **Phase C:** Blocked on Phase A. No verified signed `expense_tracker.apk` on the `v1.1.0` release yet.

---

## 5. Definition of done

- The release workflow on `v1.1.0` is green end to end, including the signed build step and the APK publish step.
- `expense_tracker.apk` exists as a release asset on GitHub Releases for `v1.1.0`.
- `design/plan1_1_0.md` is agentic: phases, scope, files, verifications, acceptance criteria, and a current-status block.
