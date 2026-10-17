# Phase 2+3 Implementation Plan — Sign-in / Sync

> Subagent output for the orchestrator session. Based on repo state at commit in workspace:
> `/home/srinidhi/Documents/Google_AI_Studio/expense_tracker`. All file paths are relative to repo root.

---

## 0. Sources referenced

| Source | Sections used | Why |
|---|---|---|
| `plan.md` | §3.1 (Credential Manager), §3.2 (Firebase Spark), §4 (arch), §5.3 (Workspace), §6.1–6.8 (Sign-in, Sync, Quota), §6.5 (Firestore layout), §6.6 (rules), §13.2 (tests) | Design spec — every component below maps to a numbered subsection. |
| `BUILD_CONTRACT.md` | §1–4 (repo / toolchain / hard rules), §22 (Finance model / pure Kotlin), §61–65 (arch rules), §107 (testing) | CI will fail without these. |
| `config/detekt/detekt.yml` | Lines 67–104 (style: 120-char, no wildcards, `Log.*` banned, `Modifier.blur` banned, `TODO:` banned) | Every file must pass. |
| Existing code | `WorkspaceManager.kt`, `MembershipStore.kt`, `GoogleSignInClient.kt`, `SettingsStore.kt`, `SyncStatus.kt`, `QuotaGovernor.kt`, `ConflictResolver.kt`, `FirestoreRemoteDataSource.kt`, `RemoteLedger.kt`, `LedgerModule.kt`, `ExpenseRepository.kt` | Patterns, seams, naming conventions reproduced exactly. |

---

## 1. Authentication system using Credential Manager

### 1.1 What exists (do not re-implement)

- `data/auth/GoogleSignInClient.kt` — already uses `Identity.getSignInClient()` via `GetSignInIntentRequest`; exchanges ID token for Firebase session; returns `GoogleUser` (Firebase-free). Factory is `GoogleSignInClientFactory`.
- `data/auth/GoogleSignInResult.kt` — sealed `Success | Cancelled | Failed`; cancellation is first-class.
- `data/auth/GoogleUser.kt` — pure data class; `label` property derives email or uid.
- `data/prefs/SettingsStore.kt` — `setActiveAuth(uid, workspaceId)` / `clearActiveAuth()`.

### 1.2 What is missing (Phase 2)

**File: `app/src/main/java/com/srinidhi/kharch/auth/SignInFlow.kt`**

Purpose: orchestrate the first-run/onboarding flow (§6.1). Must stay in `data/auth/` (not domain) because it touches Firebase Auth and Credential Manager.

Requirements:
- `suspend fun startFirstRun(context, launcher): SignInFlowResult` — creates `PendingIntent` via `GoogleSignInClient.createSignInIntent()`; delegates to `ActivityResultLauncher`. Must **not** call `Log.*` (detekt `ForbiddenMethodCall`, BUILD_CONTRACT §22 / detekt line 90–101).
- After `handleSignInResult`, calls `WorkspaceManager.ensureWorkspace(uid)` (see §2).
- Persists profile (uid, email, displayName, photoUri, linkedAt) into `SettingsStore` via `setActiveAuth`. Use `DataStore.preferences` — not `SharedPreferences` (existing pattern, BUILD_CONTRACT §1).
- If `currentUser()` already returns non-null (`FirebaseAuth.getInstance().currentUser`), skip onboarding entirely (§6.1 step 3); emit `AlreadySignedIn(user)`.
- Handle `Cancelled` gracefully — no error shown (§6.1); UI stays on onboarding with same CTA.

**Integration with domain/data:**
- Calls `WorkspaceManager.ensureWorkspace(uid)` (`data/workspace/`).
- Calls `SettingsStore.setActiveAuth(uid, workspaceId)` (`data/prefs/`).
- Does **not** import `android.*` into domain; this file is `data/` layer so Android imports allowed.

**Coding style:**
- 4-space indent, max 120 chars (detekt `MaxLineLength`).
- No wildcard imports (detekt `WildcardImport`).
- No `!!`; use `?.let` or `checkNotNull` with `@Suppress` + comment if unavoidable.
- Expression bodies preferred (`val x = ...` rather than `val x: Type = ...` with block).
- KDoc on public functions (not enforced but required by REVIEW standard from existing `WorkspaceManager.kt`).
- Every file ends with trailing newline (BUILD_CONTRACT §5 / script).

### 1.3 Tests

**File: `app/src/test/java/com/srinidhi/kharch/auth/SignInFlowTest.kt`**

Requirements (from `BUILD_CONTRACT.md` §107 / `plan.md` §13.2):
- JUnit4 (`org.junit.Test`), no JUnit5.
- Use `FakeClock` (`core/time/FakeClock.kt`) for time-sensitive assertions (e.g., `linkedAt`).
- Mock `GoogleSignInClient` via a test seam / interface — never initialize real Firebase on JVM (BUILD_CONTRACT §22: no real auth in unit tests).
- Test cases per `plan.md` §13.2 checklist: first-run creates workspace, returning user skips onboarding, cancelled stays on screen, failed shows error, profile persisted correctly.
- Test names as sentences: `fun `first run creates workspace and saves active auth`() {}`.
- Assert no `Log.d` calls made (use shadow / mock verification; detekt prevents source-level usage but verify absence in test code too).

---

## 2. Workspace management (shared across Google accounts)

### 2.1 What exists

- `data/local/WorkspaceEntity.kt` — Room entity; exactly one row (`PRIMARY KEY`). `memberUids: List<String>`, `schemaVersion`, `name`.
- `data/local/WorkspaceDao.kt` — `current()`, `observeCurrent()`, `upsert()`, `findById()`.
- `data/workspace/WorkspaceManager.kt` — full implementation of `ensureWorkspace()`, `linkAccount()`, `removeAccount()`, `eraseLedger()`, `createWorkspace()`; uses transactional `MembershipStore`.
- `data/workspace/MembershipStore.kt` — interface for transactional membership changes.
- `data/remote/FirestoreRemoteDataSource.kt` — implements `MembershipStore` with Firestore `runTransaction` (merge-on-race for `createWorkspace`, atomic `addMember`/`removeMember`).
- `core/model/Workspace.kt` — domain model.

### 2.2 What is missing / needs hardening (Phase 2)

**File: `app/src/main/java/com/srinidhi/kharch/data/workspace/WorkspaceStore.kt`** (proto DataStore wrapper — exists conceptually in `plan.md` §4 but missing file)

Purpose: persist the active workspace pointer and member profile separately from `SettingsStore`, so sync can read workspace meta without reading appearance settings. Actually `SettingsStore` already holds both `activeAuthUid` and `workspaceId`; for simplicity keep using `SettingsStore` as the single pointer source (§4 architecture: "Settings and the workspace pointer"). No new file needed unless profile expansion requires it.

If expanded profile needed (photoUri, displayName, linkedAt per `plan.md` §6.1.4), add to `SettingsStore`:
- `Profile` proto or preference keys for `linkedAtMillis`, `displayName`, `photoUri`.
- Methods `setProfile(uid, name?, photo?)` / `getProfile(uid?)`.

Requirements (BUILD_CONTRACT §61–65 / `plan.md` §4):
- Domain layer (`core/model/Workspace.kt`) must not import Android or Firebase types. Already satisfied.
- Data layer (`WORKSPACE` package) may import domain and `local`/`remote`. Already satisfied.
- `WorkspaceManager` is `@Singleton`; keep that.

**Space for multi-ledger (§6.2):** `WorkspaceManager.createWorkspace()` already generates random UUID; `linkAccount()` distinguishes join vs. start. UI sheet uses `WorkspaceManager.ensureWorkspace()` (default = join) or `createWorkspace()` (separate ledger). No extra code required unless new action needed.

### 2.3 Security rules verification (Phase 2)

**File: `app/src/test/java/com/srinidhi/kharch/data/remote/FirestoreRulesTest.kt`** (new; `plan.md` §6.6 requires `@firebase/rules-unit-testing` — but since CI has no real project, implement using emulator rules test with `FirebaseFirestore` test instance if available, else mock-based assertion of rules document; at minimum document rules in `firestore.rules` and verify syntactically.

Requirements:
- Rules document (`firestore.rules`) must contain the `isMember` function, `validExpense` validation (`amountPaise` int >= 0, `kind` in `['EXPENSE', 'REFUND']`, etc.), `memberUids.size() <= 5`, create/update restrictions (§6.6).
- No `delete` allowed on workspace (`allow delete: if false`).
- Non-member read denied. Non-member write denied. Member adding 6th refused.
- Verify with `@firebase/rules-unit-testing` in CI or document in build.

---

## 3. Sign-in flow (first run, linking accounts)

### 3.1 Onboarding sequence (§6.1–6.2)

```
Onboarding screen (Glass UI, §7)
  └─ "Continue with Google" → SignInFlow.startFirstRun()
       └─ Credential Manager intent → handleSignInResult()
            └─ Firebase Auth session → WorkspaceManager.ensureWorkspace(uid)
                 ├─ No workspace → createWorkspace(uid, "Kharch")
                 │            ├─ Firestore transaction (MembershipStore.createWorkspace)
                 │            ├─ Seed 12 categories (DatabaseSeeder / RoomCallback)
                 │            └─ Write WorkspaceEntity (workspaceDao.upsert)
                 └─ Workspace exists → linkAccount(existing, uid) (§6.2 default = join)
                      ├─ Firestore transaction (addMember)
                      └─ Update Room (memberUids + uid)
            └─ SettingsStore.setActiveAuth(uid, workspace.id)
            └─ Trigger SyncCoordinator.requestSync() (§6.3)
```

### 3.2 Linking additional accounts (§6.2)

**UI: `ui/settings/AccountsScreen.kt` (existing concept, needs wiring)**
- Action "Link another Google account" uses `GetMultipleSignInWithGoogleOption` per `plan.md` §3.1 / §6.2. Fallback to `GetSignInWithGoogleOption` if multi is alpha (explicitly flagged, not improvised).
- After result, present glass choice sheet (§6.2):
  - **A** *Join this ledger* (default) → `WorkspaceManager.linkAccount()`.
  - **B** *Start a separate ledger* → `WorkspaceManager.createWorkspace()`; previous workspace selectable via "Switch ledger".
- Each member row: avatar, email, "Active now" badge, last-sync, Remove (blocked if last member — `LastMemberException` / `RemovalOutcome.LastMember`).

**File: `app/src/main/java/com/srinidhi/kharch/auth/LinkAccountFlow.kt`**
- Similar to `SignInFlow` but explicitly passes `workspaceId` to manager (join path) or creates new (start path).
- Must use `GetMultipleSignInWithGoogleOption` when available.
- Must handle concurrent link via `MembershipStore` transaction (already implemented in `FirestoreRemoteDataSource`).

### 3.3 Tests

**File: `app/src/test/java/com/srinidhi/kharch/auth/LinkAccountFlowTest.kt`**
- Assert join appends to `memberUids`; assert start creates new UUID; assert concurrent link merges (simulate two transactions in `MembershipStore` test double); assert removal of last member blocked; assert removal of non-last succeeds and does not delete data.

---

## 4. Sync coordination with quota management

### 4.1 Architecture recap (§4, §6.3, §6.5, §6.8)

```
UI (Pull-to-refresh / save triggers)
  └─ SyncCoordinator.requestSync()
       └─ Workspace-level mutex (only one sync per workspace)
            ├─ QuotaGovernor.acquireRead/Write (checks ceiling / lock)
            ├─ Push: OutboxDao PENDING_* → batched Firestore set (≤400 ops)
            │        └─ Mark SYNCED; advance lastPushAt meta
            ├─ Pull: where(updatedAt > lastPulledAt) → page ≤400
            │        └─ LWW merge (ConflictResolver) → Room upsert
            │        └─ Advance lastPulledAt watermark
            └─ Tombstone sweep (30-day) → pullExpiredTombstones → batch delete
```

### 4.2 Missing components to build

**File: `app/src/main/java/com/srinidhi/kharch/sync/SyncCoordinator.kt`** (NEW — referenced throughout `plan.md` but does not exist)

Purpose: workspace-level mutex, debounced outbox, paged pull, batched push, meta watermarks.

Requirements (from `plan.md` §6.3, §6.4, §6.8):
- `@Singleton`; injected with `RemoteLedger`, `QuotaGovernor`, `Clock`, `WorkspaceDao`, `OutboxDao`, `ExpenseDao`, `CategoryDao`.
- `suspend fun requestSync()` — entry point from UI / WorkManager.
- Must hold a `Mutex` (Kotlin `Mutex`) per workspace so concurrent calls serialize (§6.3: "workspace-level mutex").
- Push is **debounced 800 ms** (§6.8): do not flush on every save; coalesce using a `MutableStateFlow` or `Channel` with `debounce`. Existing UI already triggers via `SyncStatus` / `SyncStateHolder`; coordinator consumes.
- Pull uses **watermark** (`where("updatedAt", ">", lastPulledAt)`) — never full collection scan (§6.8). Read cap: `MAX_PAGE_DOCS = 400`. Loop until caught up or per-run ceiling reached.
- Push batch cap: `MAX_BATCH_OPS = 400` (existing in `FirestoreRemoteDataSource`). Call `RemoteLedger.pushExpenses` / `pushCategories`.
- After push, query `OutboxDao` for remaining `PENDING_UPSERT` / `PENDING_DELETE`; repeat if needed but never exceed quota.
- After pull, apply `ConflictResolver.resolve()` per document; write to Room; update meta via `RemoteLedger.writeMeta`.
- On `SyncException` with `kind == RESOURCE_EXHAUSTED`: call `QuotaGovernor.onResourceExhausted()`; do **not** retry; emit `QuotaLocked` to `SyncStateHolder` (§6.7 / `SyncStatus`).
- On `TRANSIENT`: use `QuotaGovernor.delayForAttempt(attempt, kind)`; retry with exponential backoff (1s, 2s, 4s … cap 5 min — `QuotaGovernor.MAX_BACKOFF_MILLIS`).
- Zero live snapshot listeners (§6.8 / `RemoteLedger` contract comment).

**Integration with domain/data:**
- Uses domain `ConflictResolver` (pure Kotlin) — no Android / Firestore imports in merge logic.
- Uses `data/remote/RemoteLedger` — abstract, testable with fake.
- Uses `data/local/OutboxDao` (need to verify exists; if not, add — `plan.md` §4 shows `OutboxDao`).
- Uses `data/local/AppDatabase` (Room) for writes.
- Updates `SyncStateHolder` (singleton in `sync/`) for UI pill.

**File: `app/src/main/java/com/srinidhi/kharch/sync/SyncWorker.kt`** (NEW)

Purpose: `WorkManager` periodic + expedited sync (§6.3, §3.1).

Requirements:
- `Worker` subclass; `PeriodicWorkRequest` 15 min (§6.3); `OneTimeWorkRequest` expedited triggered by expense save / connectivity change / app-start.
- Must call `SyncCoordinator.requestSync()` via injection (Hilt `Worker` as `@HiltWorker`).
- Must respect `QuotaGovernor` — do not start if locked (check `currentStatus().isLocked`).
- Must not run if `settingsStore.activeAuthUid` is null (not signed in).
- Must use `Constraints` (network required for sync, not required for save — separate triggers).

**File: `app/src/main/java/com/srinidhi/kharch/sync/SyncTrigger.kt`** (NEW / `plan.md` §4 mentions `sync-glue/SyncTrigger.kt`)

Purpose: bridge from Room writes to sync request (invalidate → worker). Could be a `Flow` collector in `KharchRoot` or a `RoomDatabase.Callback`. Keep minimal.

Requirements:
- On `ExpenseDao.insert/update/delete`, emit to `SyncCoordinator` (or enqueue `WorkManager` expedited work).
- Must not block Room writer (use `CoroutineScope` + `launch`).

### 4.3 Quota governance (already exists — reinforce usage)

**File: `app/src/main/java/com/srinidhi/kharch/domain/sync/QuotaGovernor.kt`** — fully implemented; just needs to be wired into `SyncCoordinator`.

Requirements for coordinator integration:
- Before any `RemoteLedger` call: `quotaGovernor.acquireWrite(count = batchSize)` or `acquireRead(count = pageSize)`. If `Refused`: stop sync, update `SyncStateHolder` with message from `quotaGovernor.lockMessage()`, do not retry until reset.
- After `RESOURCE_EXHAUSTED`: immediately `onResourceExhausted()`; do not call `delayForAttempt` (returns `null` for this kind).
- Budget roll-over: `QuotaGovernor.refresh()` uses `clock.epochMillisNow` and Pacific date; if device clock jumps backward, `refresh()` ignores roll-over (existing logic line 153–156) — this is correct to prevent fresh budget exploit.

### 4.4 Conflict resolution (exists — enforce usage)

**File: `app/src/main/java/com/srinidhi/kharch/domain/sync/ConflictResolver.kt`** — fully implemented.

Requirements for coordinator:
- Every pulled document: create `LedgerRow`; merge with local using `ConflictResolver.resolve()`; persist winning row to Room via DAO.
- Tombstone sweep: pull `where("deletedAt", "<", now - 30 days)`; batch delete via `RemoteLedger.deleteExpenses` / `deleteCategories`; only one device per day (use meta `lastSweepAt`).
- `monotonicTimestamp()` must be applied before writing to Room: if device clock < `lastSeenUpdatedAt`, raise `updatedAt` (prevents backwards clock from losing merges).

### 4.5 Tests

**File: `app/src/test/java/com/srinidhi/kharch/sync/SyncCoordinatorTest.kt`**

Requirements (plan.md §13.2):
- Use fake `RemoteLedger` (in-memory) — never real Firestore (BUILD_CONTRACT §22 / `RemoteLedger` contract).
- Use `FakeClock` for time / quota roll-over.
- Assert debounced push (multiple saves within 800 ms → 1 batch).
- Assert paged pull (first sync 5000 expenses → loop 13 pages of 400, stop at done; second sync 0 reads).
- Assert quota lock stops all requests after `RESOURCE_EXHAUSTED`; assert zero requests between lock and midnight reset.
- Assert LWW convergence: two devices edit same expense offline → same winner (`authorUid` tiebreak).
- Assert tombstone propagates; delete beats update at equal timestamp.
- Assert backward-clock guard raises timestamp.

---

## 5. Tests for all components

### 5.1 Overall test pyramid (BUILD_CONTRACT §107, plan.md §13)

| Layer | Tools | Pattern from existing |
|---|---|---|
| Unit (pure Kotlin) | JUnit4, `kotlinx-coroutines-test`, Turbine, MockK only if needed | `ConflictResolverTest`, `QuotaGovernorTest`, `PaiseMathTest` |
| Data / Room | `MigrationTestHelper`, `Robolectric` (`unitTests.isIncludeAndroidResources = true`) | `WorkspaceDaoTest`, `ExpenseDaoTest`, `ConvertersTest` |
| Remote / Sync | Fake in-memory `RemoteLedger`; no Firebase init on JVM | `FirestoreRemoteDataSource` tested with emulator if available; else mock `RemoteLedger` |
| UI / Compose | Compose UI Test; `GlassContrastTest` existing | `DashboardScreen`, `AddEditSheet` |

### 5.2 Specific test files to create

| Component | Test file | Key assertions (must pass CI) |
|---|---|---|
| SignInFlow | `auth/SignInFlowTest.kt` | First-run workspace creation; returning user skip; cancelled handled; profile saved |
| LinkAccountFlow | `auth/LinkAccountFlowTest.kt` | Join vs start choice; concurrent link merge; remove last blocked; remove others OK |
| WorkspaceManager (hardening) | `data/workspace/WorkspaceManagerTest.kt` | `ensureWorkspace` idempotent; `eraseLedger` tombstones; `linkAccount` transactional |
| SyncCoordinator | `sync/SyncCoordinatorTest.kt` | Debounced push; paged pull; quota lock stops; LWW merge; tombstone sweep |
| SyncWorker | `sync/SyncWorkerTest.kt` | Expedited work enqueued on save; periodic 15 min; skips when locked / unsigned |
| QuotaGovernor (integration) | `domain/sync/QuotaGovernorTest.kt` | Already exists — verify `SyncCoordinator` uses it correctly |
| ConflictResolver (integration) | `domain/sync/ConflictResolverTest.kt` | Already exists — verify coordinator applies it |
| Firestore Rules | `data/remote/FirestoreRulesTest.kt` | `isMember`; `validExpense`; size <=5; delete false; non-member denied |

---

## 6. Integration with existing domain/data layers

### 6.1 Package / layer mapping (plan.md §4, BUILD_CONTRACT §61)

```
com.srinidhi.kharch
├── auth/                 (NEW)  SignInFlow, LinkAccountFlow
├── sync/                 (NEW)  SyncCoordinator, SyncWorker, SyncTrigger
├── data/
│   ├── auth/             (exists) GoogleSignInClient, GoogleUser, GoogleSignInResult
│   ├── workspace/        (exists) WorkspaceManager, MembershipStore
│   ├── prefs/            (exists) SettingsStore
│   ├── remote/           (exists) FirestoreRemoteDataSource, RemoteLedger, SyncException
│   └── local/            (exists) AppDatabase, WorkspaceDao, ExpenseDao, CategoryDao, OutboxDao
├── domain/
│   ├── sync/             (exists) QuotaGovernor, ConflictResolver, SyncErrorClassifier, LedgerRow
│   └── expense/          (exists) ExpenseRepository, ExpenseValidation
├── core/
│   └── model/            (exists) Workspace, Expense, Money, Category
└── ui/
    └── settings/         (exists concept) AccountsScreen wiring needed
```

Rules enforced:
- `ui` → `domain` + `data`; `domain` → nothing Android/Firebase; `data` → `domain` only.
- All money is `Long` paise; `kind` separates direction (`ExpenseKind.EXPENSE | REFUND`).
- Room entities live in `data/local`; domain models in `core/model`.
- No `fallbackToDestructiveMigration()` anywhere (CI grep guard, BUILD_CONTRACT §9).

### 6.2 Dependency injection (LedgerModule.kt, BUILD_CONTRACT §61)

In `di/LedgerModule.kt`, add / verify:

```kotlin
@Provides @Singleton
fun provideSyncCoordinator(
    remote: RemoteLedger,
    governor: QuotaGovernor,
    clock: Clock,
    workspaceDao: WorkspaceDao,
    outboxDao: OutboxDao,
    expenseDao: ExpenseDao,
    categoryDao: CategoryDao,
    db: AppDatabase,
    stateHolder: SyncStateHolder
): SyncCoordinator = SyncCoordinator(...)

@Provides @Singleton
fun provideQuotaGovernor(clock: Clock): QuotaGovernor = QuotaGovernor(clock)
```

Keep existing `provideClock()`, `provideAppDatabase()`, `provideExpenseRepository()`. No duplicate `@Provides` for same type (BUILD_CONTRACT §3 / CI compile error).

### 6.3 Firestore layout compliance (§6.5)

Confirm `FirestoreLayout.kt` paths match all writes/reads above:
- `workspaces/{wid}` — workspace doc.
- `workspaces/{wid}/meta` — `SyncMetaDto` (lastPulledAt, lastPushAt, lastSweepAt, lastSyncError).
- `workspaces/{wid}/expenses/{id}` — full docs with `authorUid`.
- `workspaces/{wid}/categories/{id}` — categories.
- No listener registrations; all `get()` are one-shot.

---

## 7. Compliance checklist (before any commit)

Use this checklist from `BUILD_CONTRACT.md` §"Before you finish" (§122) and `plan.md` §14 (CI):

- [ ] `grep` for `Log.` / `println` — must be zero in new files.
- [ ] `grep` for `TODO:` / `FIXME:` — banned by detekt `ForbiddenComment` (line 79–84).
- [ ] `grep` for `fallbackToDestructiveMigration` — banned by CI grep guard.
- [ ] `grep` for `Modifier.blur` — banned by detekt / §8.1 (pre-baked backdrop only).
- [ ] Confirm every `.kt` ends with trailing newline (`scripts/ensure-trailing-newline.sh`).
- [ ] Confirm max line length 120 (`detekt.yml` line 76 / 110).
- [ ] Confirm no wildcard imports (`detekt.yml` line 77).
- [ ] Confirm `@Composable` naming ignored if applicable (`detekt.yml` line 31).
- [ ] Confirm no `!!` without `@Suppress` + comment (BUILD_CONTRACT §53 / style).
- [ ] Confirm tests named as sentences (BUILD_CONTRACT §115 / `plan.md` §13.2).
- [ ] Confirm `Money` uses `MagnitudePaise` / `Money` exactly — never `Double`/ `Float` for amounts (`BUILD_CONTRACT.md` §87–105).
- [ ] Confirm Indian grouping `InrFormatter` used for all amount display (`plan.md` §5.1, `core/money/InrFormatter.kt`).
- [ ] Confirm `Credential Manager` uses `GetSignInWithGoogleOption` / `GetMultipleSignInWithGoogleOption` — not legacy `GoogleSignInClient` (`plan.md` §3.1 / §6.1).
- [ ] Confirm `google-services.json` committed (D3; already present) and `Firebase BoM` pinned to `34.19.0` (BUILD_CONTRACT §27).
- [ ] Confirm `detekt` passes with `maxIssues: 100` but ideally 0.
- [ ] Confirm ` assemblyRelease` / `assembleDebug` would succeed (proguard rules adequate for reflection/serialization in `FirestoreMapper`).

---

## 8. File inventory (new + modified)

New files:
- `app/src/main/java/com/srinidhi/kharch/auth/SignInFlow.kt`
- `app/src/main/java/com/srinidhi/kharch/auth/LinkAccountFlow.kt`
- `app/src/main/java/com/srinidhi/kharch/sync/SyncCoordinator.kt`
- `app/src/main/java/com/srinidhi/kharch/sync/SyncWorker.kt`
- `app/src/main/java/com/srinidhi/kharch/sync/SyncTrigger.kt`
- `app/src/test/java/com/srinidhi/kharch/auth/SignInFlowTest.kt`
- `app/src/test/java/com/srinidhi/kharch/auth/LinkAccountFlowTest.kt`
- `app/src/test/java/com/srinidhi/kharch/sync/SyncCoordinatorTest.kt`
- `app/src/test/java/com/srinidhi/kharch/sync/SyncWorkerTest.kt`
- `app/src/test/java/com/srinidhi/kharch/data/remote/FirestoreRulesTest.kt` (or `firestore.rules` doc + syntax check)

Modified files:
- `app/src/main/java/com/srinidhi/kharch/di/LedgerModule.kt` — add `SyncCoordinator`, `QuotaGovernor` bindings
- `app/src/main/java/com/srinidhi/kharch/data/workspace/WorkspaceManager.kt` — verify `linkAccount()` / `ensureWorkspace()` fully compliant (already is; may need minor adjustments for multi-account profile)
- `app/src/main/java/com/srinidhi/kharch/data/prefs/SettingsStore.kt` — add profile fields if needed
- `app/src/ui/settings/AccountsScreen.kt` — wire "Link another" action (existing screen; needs action binding)
- `app/src/main/AndroidManifest.xml` — ensure `WorkManager` initialized; `Credential Manager` available; `PredictiveBackHandler` (`plan.md` §7.4) already present if target 36.

---

*End of plan. All components reference existing code patterns (`WorkspaceManager`, `GoogleSignInClient`, `QuotaGovernor`, `RemoteLedger`) and comply with `plan.md`, `BUILD_CONTRACT.md`, `detekt.yml`, and the architecture rules (§4) of the repo.*
