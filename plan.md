# Kharch — Implementation Plan

> **Kharch** — a manual-entry Android expense tracker with a shared Google-account ledger, a
> glassmorphic UI, a home-screen widget, and a year-wise monthly dashboard.
> Build & test **exclusively in GitHub Actions**. No local Android SDK required, no APK
> downloaded to the workstation.
> **Cost: ₹0** — Firebase Spark plan, no billing account, no card (D2).

---

## 0. Constraints & Locked Decisions

| # | Constraint | How the plan honours it |
|---|---|---|
| C1 | Build/run/test only in GitHub | All compilation, unit tests, instrumented tests and lint run in GitHub Actions workflows. Local machine needs only `git` + `gh`. |
| C2 | Do not download the APK | CI uploads the APK as a **workflow artifact**. We monitor with `gh run watch` / `gh run view --log`. Nothing is pulled to disk. |
| C3 | Target Android 15+ | `compileSdk` / `targetSdk` = latest stable (API 36), `minSdk` 29 (see §11.3 for the reasoning). Edge-to-edge and predictive back are mandatory, not optional. |
| C4 | Glassmorphism with *efficient* blur | A single pre-baked, downsampled backdrop bitmap (≈1/16 area) reused by every glass surface. Zero per-frame blur cost. See §8. |
| C5 | Legibility is extremely important | Colour tokens are contrast-tested in CI against the worst-case composited glass colour (≥ 7:1 headline, ≥ 4.5:1 body), plus a scrim layer, minimum type sizes, and a High-Contrast user toggle. See §8.4. |
| C6 | Manual entry only | No OCR, no SMS parsing, no bank import. The only input paths are the in-app form, the widget quick-add sheet, and (stretch) a share-sheet intent. |

### Decisions locked in (interview, this session)

| # | Decision | Consequence in this plan |
|---|---|---|
| D1 | **One shared dataset across all Google accounts.** Linking a second Google account is an additional way to reach the *same* expenses — not a separate ledger. | Data model gains a **Workspace/Ledger** entity. Every Google account is a *member* of one workspace. Single Room DB, not one file per account. See §5.3, §6. |
| D2 | **Completely free, forever.** | Firebase **Spark** plan: Auth + Firestore with no billing account and no credit card. Verified quotas and the quota-exhaustion failure mode are now an explicit part of the sync design (§6.8). No Blaze-only services. |
| D3 | **`google-services.json` committed to the repo.** | Firebase's own documented guidance — the file is not a secret (access is gated by package name + signing SHA). Identical local and CI builds, no extra CI step. |
| D4 | **Categories are fully user-editable in v1.** | Category CRUD (create, rename, reorder, hide) syncs via the same LWW pipeline. A category in use is hidden, never deleted. See §5.2. |
| D5 | **Refunds supported in v1.** | Amounts get a `kind` (`EXPENSE` / `REFUND`) plus an optional link to the original expense. Net = charges − refunds. Charts and totals must handle a negative month. See §5.1, §9. |
| D6 | **Budgets are a v1.1 stretch, not built now.** | Scoped, not implemented. Noted in §1.2. |
| D7 | **Release via GitHub Releases** (tag → signed APK). No Play Store. | No $25 developer fee, fits "build and test in GitHub only". §14.3. |
| D8 | **Light and dark are both first-class.** | Two complete token sets with different glass physics. Legibility maths is asymmetric; light mode gets the harder treatment. §8.5. |
| D9 | **No sample/demo data.** CI screenshots capture genuine empty states. | The empty state is a designed, tested surface — not a placeholder. A debug-only developer seed exists but is **off** by default. §7.2E, §14.2. |
| D10 | **Glass default = Exaggerated** (heavy frost, unmistakably glass). | The "Exaggerated" tier becomes the token baseline; Subtle and Normal remain user-selectable *below* the default. §8.3. |
| D11 | **App name: "Kharch".** Package: `com.srinidhi.kharch`. | Package name is registered in Firebase and is painful to change later — confirm before Phase 0's Firebase step. |

### Production-hardening decisions (second interview)

| # | Decision | Consequence |
|---|---|---|
| P1 | **No telemetry whatsoever.** No Crashlytics, no Performance Monitoring, no user-initiated diagnostic export, no analytics. | §18.1. Recorded as a deliberate, documented trade-off with its cost stated honestly. |
| P2 | **CSV + JSON export *and* import.** | §18.4. Also the GDPR/DPDP data-portability story. |
| P3 | **No certificate pinning.** Firebase handles TLS. | §18.3. Pinning risks bricking sync on a Google cert rotation. |
| P4 | **No database encryption at rest.** App-private storage is sufficient. | §18.3. Avoids a permanent-data-loss risk from key mishandling. |
| P5 | **Android Auto Backup opted out**; user-driven export instead. | §18.4. Stops financial records being silently uploaded to Google. |
| P6 | **`PRIVACY.md` in the repo.** | §18.5. |
| P7 | **CI-enforced performance budgets** (startup + frame timing). | §14.1, §18.6. Makes "smooth" a number that fails the build. |
| P8 | **Full supply-chain hardening.** Dependency verification, SHA-pinned actions, read-only token, branch protection. | §14.7, §18.7. |
| P9 | **No Room migrations ever destructively.** Every migration tested with `MigrationTestHelper`. | §18.2. The single biggest data-loss risk in a personal app. |
| P10 | **In-app update check.** | §18.8 — with a private-repo constraint that changes the implementation (the GitHub API returns 404 here). |
| P11 | **Manual device testing only** for release verification (no Test Lab). | §18.9. Accepted trade-off: OEM-specific bugs surface from users. |

---

---

## 1. Product Scope

### 1.1 In scope (MVP → v1)

- Manual expense entry: **name, type/category, date, amount (INR)**. Optional: note, and a
  **refund** flag with an optional link to the original expense (D5).
- Google sign-in with **multiple accounts sharing one ledger** (D1) — link any number of Google
  accounts, all resolving to the same expense dataset.
- User-editable categories: 12 seeded ones, plus add / rename / reorder / hide (D4).
- Offline-first storage + automatic background sync to Firestore on the free Spark plan (D2).
- Home-screen widget: current month total, today's total, mini trend, one-tap quick add.
- Dashboard: selected-year monthly split (12 bars), category split, YoY comparison, drill-down.
- Expense list: grouped by day, search, filter, swipe-to-delete with undo.
- Settings: accounts & sync status, theme/glass intensity, high-contrast mode, category editor,
  export/backup.
- Full test pyramid + CI on GitHub, including instrumented tests on a real emulator image.

### 1.2 Explicitly out of scope (v1)

- **Budgets and alerts** — scoped for v1.1 (D6), not built now.
- Recurring transactions, multi-currency, bank import, OCR, receipt capture, web/desktop clients,
  iOS.
- **Crash reporting, performance monitoring, analytics** (P1) — nothing leaves the device.
  Consequences are documented honestly in §18.1.
- **Firebase Test Lab / real-device farm** (P11) — manual device testing instead.
- **Cloud Storage for Firebase** — as of 3 Feb 2026 it requires the Blaze plan even within free
  usage. Excluded entirely to keep D2's "completely free" guarantee intact.
- **Cloud Functions, App Check with Play Integrity, and Firestore TTL/PITR/backup** — all Blaze-only.

### 1.3 Non-functional requirements

| Requirement | Target | How it is verified |
|---|---|---|
| Frame rate | 60 fps minimum, 90/120 fps where supported; no frame > 32 ms during nav | Perf smoke test + manual profile in CI logs |
| Cold start | < 1.2 s on API 35 emulator (`Pixel_6`) | `connectedAndroidTest` startup assertion |
| Offline-first | Every feature works with the radio off; zero data loss | Instrumented test with `adb`-driven network disable |
| Accessibility | TalkBack pass, 200 % font scale pass, 48 dp touch targets | Compose UI tests + a11y lint |
| Contrast | 7:1 headline, 4.5:1 body on glass, **both schemes** | `GlassContrastTest` unit test in CI |
| **No data loss across upgrades** | Zero destructive migrations; every upgrade path tested | `MigrationTestHelper` suite + per-build migration smoke test (P9) |
| **Performance budgets** | Cold start < 1.2 s, no frame > 32 ms in key journeys | Macrobenchmark gates in CI (P7) |
| **Supply-chain integrity** | Every dependency checksum-verified; all actions SHA-pinned | Gradle dependency verification `strict`; CI config review (P8) |
| APK size | < 13 MB (debug), < 8 MB (release, R8) | CI size gate |

---

## 2. Feature → Implementation Matrix

| Feature | Platform component | Notes |
|---|---|---|
| Manual entry | Compose form + custom INR keypad | Fast-path: amount keypad is always visible, no IME for numbers. Carries an `EXPENSE`/`REFUND` toggle (D5) |
| Google multi-account | Credential Manager, **shared Workspace** membership | All linked accounts are members of one ledger (D1). See §6 |
| Cloud sync | Firestore + Room outbox + WorkManager, quota-aware | Free Spark tier (D2). See §6.8 |
| Category CRUD | Room + Firestore, same LWW pipeline | Seeded 12, user-editable (D4) |
| Widget | Glance `AppWidget` + translucent `QuickAddActivity` | See §10 |
| Dashboard | Compose Canvas charts (custom, no chart lib) | Full control over glass styling + animation. Must render a **negative** month (D5) |
| Glass UI | `GlassSurface` + `rememberFrostedBackdrop` | Default tier = Exaggerated (D10). See §8 |
| Export / Import | CSV + JSON via `ACTION_CREATE_DOCUMENT` + `OpenDocument` | User-driven only; no network, no storage permission (P2, P5) |
| Update path | Firebase Hosting `version.json` manifest + system installer | Sideloaded apps can't self-update; GitHub API 404s on a private repo (P10) |
| **Zero telemetry** | No crash/analytics/performance SDK | Costs real observability; documented in §18.1 (P1) |
| CI-only build | `build.yml`, `instrumented.yml`, `perf.yml`, `rules.yml`, `release.yml` | See §14 |

---

## 3. Technology Decisions

### 3.1 Core stack

| Layer | Choice | Why this and not the alternative |
|---|---|---|
| Language | **Kotlin 2.2.x** | Coroutines/Flow compose naturally with reactive sync state |
| UI | **Jetpack Compose + Material 3** (Material You + expressive theming) | Needed for custom glass surfaces, shared-element transitions, spring physics |
| Min SDK | **29 (Android 10)** | `RenderEffect` blur needs 31, but §8.3's pre-baked backdrop gives a credible frosted look below 31 without extra deps. Android 15/16 is the primary target anyway. |
| Compile / target SDK | **36 (Android 16)** | Predictive-back system animations are on by default for target 36; mandatory edge-to-edge |
| Gradle | **8.14 / 9.x wrapper** | Pinned via wrapper — CI and local agree |
| AGP | Latest stable 8.x/9.x | Verified against release notes at implementation time |
| Architecture | Single-module-at-first (`app`) with strict package layering → feature modules if the build exceeds ~5 min | Keeps CI fast; module split is a Phase 6 task if needed |
| DI | **Hilt** (KSP) | Standard, works with `@HiltAndroidApp` + `@AndroidEntryPoint` |
| Async | Coroutines + Flow exclusively | No Rx |
| Persistence | **Room 2.7.x** (KSP) + **DataStore Proto** | Room is the single source of truth; DataStore holds account/profile/prefs |
| Sync | **Firebase BoM (latest)** — Auth, Firestore | See §3.2 |
| Sign-in | **Credential Manager** `GetSignInWithGoogleOption` / `GetMultipleSignInWithGoogleOption` | Modern API, native account chooser, no `play-services-auth` legacy |
| Background work | **WorkManager** | Sync + widget refresh |
| Widget | **Glance 1.1.1+** | Compose-native, supports responsive sizes, in-widget `ActionCallback` |
| Navigation | **Navigation Compose**, type-safe routes | Supports predictive back + shared elements |
| Serialization | **kotlinx.serialization** | Firestore DTO mapping |
| Charts | **Hand-rolled Compose Canvas** | ~400 lines total; a chart library fights the glass theme and adds 300–800 KB. Full control over bar/donut animation. |
| Tests | JUnit5-free (JUnit4), Turbine, MockK, Compose UI Test, `androidx.test` | See §13 |

### 3.2 Backend: Firebase Spark — and the guarantee that it stays at ₹0

**Chosen: Firebase Authentication (Google provider) + Cloud Firestore, on the Spark plan.**

Spark requires **no billing account and no credit card** ("No payment method needed" on Google's
own pricing page). For a personal expense tracker, the daily quotas are enormous:

| Free quota (per day unless stated) | Spark allowance | Kharch's realistic use |
|---|---|---|
| Stored data | 1 GiB total | ~50 MB after 10 years of daily entry (~1 expense/day) |
| Document reads | 50,000 / day | ~200–2,000 / day with watermark-based incremental pulls |
| Document writes | 20,000 / day | ~50–300 / day (one expense ≈ 1 write) |
| Document deletes | 20,000 / day | ~0 (tombstones are swept in batches) |
| Outbound transfer | 10 GiB / month | a few MB / month |
| Auth MAU | 50,000 | 1–5 people |

**The catch that shapes §6.8:** on Spark there is **no overage and no grace** — when a daily quota
is hit, Firestore returns `RESOURCE_EXHAUSTED` and the API simply stops working until the quota
resets at midnight Pacific. A careless sync loop can therefore *burn the entire day's quota in
minutes*, leaving the app dead for the rest of the day. The sync engine is built quota-aware:
page-limited pulls, debounced writes, exponential backoff on `RESOURCE_EXHAUSTED`, and a "resumes
after midnight PT" message instead of a retry storm. This is a hard requirement, not a nicety.

**Also excluded to preserve the ₹0 guarantee:** Cloud Storage (has required a linked billing
account for bucket creation since 3 Feb 2026, even at zero usage), Cloud Functions, App Check
with Play Integrity, and Firestore TTL / PITR / backup. Tombstone cleanup (§6.4) is therefore a
**client-side batched sweep**, not a TTL policy.

| Rejected option | Why |
|---|---|
| Google Sheets API as a DB | No offline story, hard per-workspace access control, poor query semantics, requires manual sheet setup. |
| Google Drive files | Same problems plus worse structure. |
| Supabase / self-hosted | Free tier exists, but adds a running backend to maintain and doesn't give native Google sign-in as cheaply. |
| Custom backend | Needs hosting and ops — directly contradicts "minimal". |

**Stretch (v1.1+):** export a year to a Google Sheet via the Sheets API (free, no Blaze).

### 3.3 Dependency version policy

`gradle/libs.versions.toml` is the single source of truth. Every version is pinned (no `+`).
At implementation start, verify each against official release notes and pin exact versions;
then enable **Dependabot** (`gradle` + `github-actions`) so drift is handled by PR, not by guesswork.
CI fails on any version-catalogue inconsistency.

---

## 4. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          UI LAYER (Compose)                          │
│  DashboardScreen · ExpensesScreen · AddEditSheet · SettingsScreen     │
│  CategoryManagerScreen · QuickAddActivity · Widget (Glance)          │
│         reads Flow<UiState>              dispatches UiIntent         │
└───────────────▲──────────────────────────────────┬───────────────────┘
                │                                  │
┌───────────────┴──────────────────────────────────▼───────────────────┐
│                        DOMAIN LAYER (pure Kotlin)                    │
│  AddExpense · UpdateExpense · DeleteExpense · LinkAccount             │
│  CreateCategory · RenameCategory · ReorderCategories · HideCategory  │
│  GetYearlyDashboard · GetMonthlyBreakdown · ResolveConflicts         │
│  Money (signed paise) · Category · Expense · YearMonth                │
└───────────────▲──────────────────────────────────┬───────────────────┘
                │                                  │
┌───────────────┴──────────────┐   ┌───────────────▼───────────────────┐
│   DATA LAYER (ONE workspace) │   │       SYNC LAYER                  │
│  Room: expenses, categories  │◄──┤  SyncCoordinator (Outbox + LWW)  │
│  settings  DataStore         │   │  QuotaGovernor (Spark §6.8)      │
│  WorkspaceManager (members)  │   │  SyncWorker (periodic, expedited) │
│  single DB, no per-user file │   │  FirestoreRemoteDataSource        │
└──────────────────────────────┘   └───────────────────────────────────┘
```

**The central architectural consequence of D1 (shared dataset):** there is exactly **one**
`AppDatabase` and exactly **one** workspace. Google accounts are *credentials into* that
workspace, not containers of it. This deleted the entire per-account DB-file abstraction from
the original design and removed a whole class of cross-account data-leak bugs in the process.
"Switching account" now only changes *which Google identity is currently authenticated* — it
never changes what data is shown.

### 4.1 Package layout

```
com.srinidhi.kharch
├── KharchApp.kt                      @HiltAndroidApp
├── MainActivity.kt                   single-Activity host, edge-to-edge
├── core/
│   ├── model/       Money.kt Category.kt Expense.kt ExpenseKind.kt
│   │                Workspace.kt WorkspaceMember.kt YearMonth.kt
│   ├── time/        Clock.kt  (injectable → testable dates)
│   ├── money/       InrFormatter.kt
│   └── result/      AppResult.kt
├── data/
│   ├── local/       AppDatabase.kt ExpenseDao.kt CategoryDao.kt OutboxDao.kt
│   │                Converters.kt
│   ├── prefs/       SettingsStore.kt WorkspaceStore.kt (Proto DataStore)
│   ├── remote/      FirestoreRemoteDataSource.kt FirestoreMapper.kt
│   ├── repository/  ExpenseRepository.kt CategoryRepository.kt SyncRepository.kt
│   └── workspace/   WorkspaceManager.kt
├── sync/            SyncCoordinator.kt ConflictResolver.kt QuotaGovernor.kt
│                    SyncWorker.kt SyncStatus.kt
│   ├── ui/
│   ├── theme/       Color.kt Type.kt Shape.kt GlassTokens.kt Theme.kt
│   ├── glass/       FrostedBackdrop.kt GlassSurface.kt GlassSheen.kt
│   ├── components/  GlassCard.kt GlassButton.kt GlassTextField.kt AmountKeypad.kt
│   │                CategoryChips.kt DatePickerSheet.kt EmptyState.kt
│   │                PullToRefresh.kt Shimmer.kt
│   ├── motion/      Motion.kt  (spring specs) Haptics.kt
│   ├── navigation/  Routes.kt AppNavHost.kt
│   ├── dashboard/   DashboardScreen.kt MonthlyBarChart.kt CategoryDonut.kt
│   │                YearSelector.kt MonthDetailSheet.kt
│   ├── expenses/    ExpensesScreen.kt ExpenseRow.kt FilterSheet.kt
│   ├── addedit/     AddEditSheet.kt ExpenseKindToggle.kt
│   ├── categories/  CategoryManagerScreen.kt
│   ├── settings/    SettingsScreen.kt AccountsScreen.kt
│   └── charts/      ChartPrimitives.kt  (Canvas, animation primitives)
├── widget/          KharchWidget.kt GlanceReceiver.kt WidgetState.kt
│                    QuickAddActivity.kt
└── sync-glue/       SyncTrigger.kt  (invalidation → widget + worker)
```

**Layering rules enforced by review + `:module:check` (ArchUnit-style test):**

- `ui` may import `domain` and `data`; `domain` imports **nothing** Android-specific.
- `domain` and `data` never import `ui`.
- Hilt bindings declared in `di/` modules, never assembled ad-hoc in screens.

---

## 5. Data Model

### 5.1 Room entities

```kotlin
@Entity(tableName = "expenses", indices = [Index("dateEpochDay"), Index("categoryId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,            // UUID v4 string
    val name: String,
    val amountPaise: Long,                 // Long — never Double
    val kind: ExpenseKind,                 // EXPENSE | REFUND   (D5)
    val currencyCode: String = "INR",      // ISO-4217, defaults to INR
    val categoryId: String,
    val dateEpochDay: Long,                // LocalDate.toEpochDay()
    val note: String? = null,
    val linkedExpenseId: String? = null,   // REFUND → the expense it reverses (D5)
    val createdAt: Long,                   // epoch millis, UTC
    val updatedAt: Long,
    val deletedAt: Long? = null,           // tombstone → deletes propagate offline
    val syncState: SyncState,              // SYNCED | PENDING_UPSERT | PENDING_DELETE
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,            // "sys_food" or UUID for user-created
    val name: String,
    val iconKey: String,                   // maps to a bundled vector/Material symbol
    val colorToken: String,                // semantic token, not a raw hex
    val sortOrder: Int,
    val isSystem: Boolean,
    val isHidden: Boolean = false,
    val deletedAt: Long? = null,           // soft delete — only if unused
    val updatedAt: Long,
)

@Entity(tableName = "workspace")           // exactly one row (D1)
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,                      // default "Kharch"
    val memberUids: List<String>,          // every linked Google account (D1)
    val createdAt: Long,
    val schemaVersion: Int,                // cloud-side migration guard (§18.2.6)
)
```

**Migrations (P9, §18.2).** The database ships at schema version 1 and every subsequent change
gets a **hand-written** migration, exported schema JSON, and a `MigrationTestHelper` test.
`fallbackToDestructiveMigration()` is **banned by a CI grep rule** — losing a ledger silently is
the one failure mode an expense app must never have.

**Money (D5).** `amountPaise` is always a **positive magnitude**; direction lives in `kind`.
This keeps storage and Firestore validation simple (`amountPaise` is `int >= 0`), avoids the
ambiguity of a negative number meaning both "minus" and "refund", and lets a refund of a refund
round-trip correctly.

```kotlin
@JvmInline value class MagnitudePaise(val paise: Long) {
    init { require(paise >= 0) }
    fun toRupees(): BigDecimal = BigDecimal.valueOf(paise, 2)
}

/** Signed value used only for aggregation and display. */
@JvmInline value class Money(val paise: Long) {
    companion object {
        fun of(m: MagnitudePaise, kind: ExpenseKind) =
            Money(if (kind == ExpenseKind.REFUND) -m.paise else m.paise)
    }
    val isCredit get() = paise < 0
    val magnitude: MagnitudePaise get() = MagnitudePaise(kotlin.math.abs(paise))
}
```

**Invariants enforced in the DAO/domain layer and asserted by tests:**

- `Money.isCredit` ⟺ `kind == REFUND`. A refund of a refund is representable and correct.
- `linkedExpenseId`, when non-null, must reference an expense of the same workspace and the same
  category. Both devices of a shared workspace converge on this because the link is a synced field.
- A linked original can be soft-marked `refundedById` for a "↩ refunded" badge; deleting the
  original does **not** cascade-delete the refund (the money still came back).
- **A month can be net negative** if refunds exceed spend. Every chart must render a zero
  baseline with bars growing both up and down (§9). This is the single most likely source of a
  dashboard bug, so it has a dedicated test.

**Indian digit grouping** is a first-class requirement — `₹1,23,456.78`, not `₹123,456.78`.
Implemented with a custom `DecimalFormat` `DecimalFormatSymbols` + a grouping-size
pattern (`#,##,##0.00`), unit-tested against real values.

### 5.2 Categories — seeded, then fully user-owned (D4)

Twelve categories ship with the app and are inserted by a `RoomDatabase.Callback` on first
create (idempotent — safe on every launch):

`sys_food` Food & Drink · `sys_transport` Transport · `sys_shopping` Shopping ·
`sys_bills` Bills & Utilities · `sys_rent` Rent · `sys_health` Health ·
`sys_education` Education · `sys_entertainment` Entertainment · `sys_travel` Travel ·
`sys_groceries` Groceries · `sys_gifts` Gifts & Donations · `sys_cash` Cash ·
`sys_other` Other

Each carries a Material symbol `iconKey` and a **semantic** `colorToken` (never a raw hex) so the
category colour re-derives correctly under dark mode, high-contrast mode and dynamic colour.

User capabilities in v1, all synced through the same LWW pipeline as expenses:

| Action | Rule |
|---|---|
| **Create** | New UUID id, `isSystem = false`. Icon chosen from a bundled symbol grid; `colorToken` auto-assigned from the next free slot in the theme palette. |
| **Rename** | Free text, 1–40 chars, trimmed, uniqueness enforced case-insensitively against visible categories. |
| **Reorder** | Drag-and-drop in the manager writes a contiguous `sortOrder` 0..n-1 to every affected row in a **single batched Firestore write** (protects the 20k writes/day quota, §6.8). |
| **Hide** | `isHidden = true` — the row is never offered in the Add sheet or filter chips, but existing expenses keep rendering it. |
| **Delete** | Only allowed if `usageCount == 0`. Otherwise the UI hides it instead and says why. The user is never silently reassigned their history. |

System categories are **not** deletable, but they *are* hideable and renameable — a user who
doesn't want "Rent" can hide it, and one who prefers "Mortgage" can rename it.

### 5.3 Shared-workspace storage (D1)

The original design gave each Google account its own database file to guarantee isolation. That
is **unnecessary here**, because D1 explicitly wants every account to see the same data. The
consequence is a simplification, not a complication:

| Option | Verdict |
|---|---|
| **A. `accountId` column + filtered queries** | Correct for D1 — one dataset, and the column is dropped entirely since there is only one workspace. |
| **B. One DB file per account** | Wrong for D1. It would make accounts *structurally unable* to share data, which is exactly what you asked for. |
| **C. Single DB, no account scoping at all** ✅ | Chosen. The workspace is the root; members are *authorisation*, not *partitioning*. |

**`WorkspaceManager`** owns:

- `WorkspaceEntity` — one row, holding `memberUids: List<String>`.
- `linkAccount(uid, email, name, photo)` — appends to `memberUids` (Firestore transaction to
  avoid a lost update when two devices link simultaneously).
- `activeAuthUid` — which Google identity is currently signed in. Stored in DataStore purely for
  UI labelling ("Synced as srinidhi@gmail.com"). **It does not filter any query.**
- `removeMember(uid)` — cannot remove the last member; blocked with a message.
- `resetWorkspace()` — wipes the local DB and requests a cloud wipe (see §6.4 for the tombstone
  sweep that makes this work).

Because there is exactly one workspace, **no query in the codebase carries an account filter**,
and there is no code path by which two users' data could ever be separated or merged by accident.

---

## 6. Sync & Shared-Ledger Design

### 6.1 Sign-in flow

1. **First run** → onboarding → "Continue with Google" → Credential Manager
   `GetSignInWithGoogleOption`.
2. Result ID token → Firebase Auth `signInWithCredential`.
3. On success, `WorkspaceManager.ensureWorkspace()`:
   - No workspace exists → create one (`memberUids = [uid]`, `name = "Kharch"`) in a Firestore
     transaction, seed the 12 system categories, write the local `WorkspaceEntity` row.
   - Workspace already exists → **join it** (add `uid` to `memberUids`) unless the user is
     explicitly offered a fresh ledger (§6.2).
4. Persist the member's profile (`uid`, `email`, `displayName`, `photoUri`, `linkedAt`) in
   DataStore purely for display.
5. Trigger the first sync; the app is fully usable offline before it completes.

### 6.2 Linking additional Google accounts (D1: shared ledger)

**Settings → Accounts → "Link another Google account"**, using
`GetMultipleSignInWithGoogleOption` (falling back to a single `GetSignInWithGoogleOption` if the
multi variant is still alpha at implementation time — explicitly flagged, not improvised).

Because D1 wants a shared dataset, signing in as a *new* Google account on a device that already
holds a ledger is ambiguous, and the app must not guess. It presents a glass choice sheet:

> **This device already has a Kharch ledger (2 accounts).**
> **A** · *Join this ledger* — the new account sees the same expenses *(default)*
> **B** · *Start a separate ledger* — the new account gets its own, empty workspace

Option A is the default because it matches the stated intent. Option B is offered because a user
may legitimately want one account for the app and another for an unrelated experiment; if chosen,
the workspace pointer moves to a new workspace id and the previous one stays intact and
re-selectable from a "Switch ledger" control. Internally this is exactly the multi-workspace
model — there is simply usually only one.

Each member row shows: avatar, email, "Active now" badge, last-sync time, and a **Remove**
action. Removing a member signs that credential out of Firebase and removes its `uid` from
`memberUids`; the ledger and every expense survive, because **no data is owned by a member**.
The last remaining member cannot be removed.

A workspace is destroyed only via Settings → "Erase this ledger", which tombstones every row and
then sweeps them from both Room and Firestore (§6.4).

### 6.3 Data flow (offline-first, outbox pattern)

```
User action ──► ViewModel ──► Repository ──► Room (write, syncState = PENDING_*)
                │
                          (debounced 800 ms, see §6.8)
                                            └──► Flow invalidation ──┬──► UI
                                                                     ├──► Widget update
                                                                     └──► SyncCoordinator.requestSync()
                                                                            │
                                          WorkManager (Expedited + Periodic 15 min,
                                          plus connectivity + app-start + expense-save triggers)
                                                                            │
                                                       SyncCoordinator (workspace-level mutex)
                                                                            │
                                          push: query PENDING rows → set(batch, ≤400) in Firestore
                                          pull: where("updatedAt" > lastPulledAt) → page → upsert (LWW)
                                          mark synced, advance lastPulledAt watermark
```

**Reads are never quota-hungry**: the pull is always `where(updatedAt > watermark)`, so a
steady-state sync of an unchanged ledger costs **0 reads**, not a full collection scan. The first
sync of a 5,000-expense ledger pages at 400 docs/request, which is 13 reads.

### 6.4 Conflict resolution

**Last-write-wins on `updatedAt`**, with four refinements that matter in practice:

1. **Tombstones** — deletes set `deletedAt` rather than removing the row, so a delete made on
   phone A while phone B is offline still wins on B.
2. **Delete-beats-update at equal timestamps** — if `updatedAt` ties, the tombstone wins
   (destructive intent is explicit; this avoids a resurrected expense).
3. **Monotonic clock guard** — if the device clock jumps backwards, `updatedAt = max(local, lastSeen)`
   so a skewed device cannot silently win forever. Clock skew is surfaced in Settings → Sync as a
   warning, not silently swallowed.
4. **Shared-workspace convergence** — since all members edit one ledger (D1), concurrent edits to
   the *same* expense are the common case, not the rare one. A deterministic tiebreaker is
   added: on equal `updatedAt`, the write from the lexicographically smaller `uid` wins. Without
   it, two members editing the same expense offline would ping-pong on every sync.

All of this lives in a pure, dependency-free `ConflictResolver` with exhaustive unit tests.

**Tombstone lifecycle (no TTL — Blaze-only, §3.2):** a client sweeps tombstones older than 30 days
during its normal sync run, in batches of ≤ 400 `DeleteRequest`s, gated by a "last sweep"
timestamp in the workspace `meta` doc so only one device performs it per day. This is where the
deletes quota (§6.8) is actually spent, and it is the only delete the app ever issues.

### 6.5 Firestore layout

```
workspaces/{wid}                  { name, memberUids[], createdAt, schemaVersion }
workspaces/{wid}/meta             { lastPulledAt, lastPushAt, lastSweepAt, lastSyncError }
workspaces/{wid}/categories/{id}  { name, iconKey, colorToken, sortOrder,
                                    isSystem, isHidden, deletedAt, updatedAt }
workspaces/{wid}/expenses/{id}    { name, amountPaise, kind, currencyCode, categoryId,
                                    dateEpochDay, note, linkedExpenseId,
                                    createdAt, updatedAt, deletedAt, authorUid }
```

`authorUid` is informational only (shown as "edited by <name>" when `authorUid != activeUid`) and
is **never** used for access control — that is `memberUids` on the workspace doc.

### 6.6 Security rules (membership-based, server-enforced)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isMember(wid) {
      return request.auth != null
        && request.auth.uid in get(/databases/$(database)/documents/workspaces/$(wid)).data.memberUids;
    }
    function validExpense() {
      let e = request.resource.data;
      return e.amountPaise is int && e.amountPaise >= 0
          && e.kind in ['EXPENSE', 'REFUND']
          && e.dateEpochDay is int
          && e.name is string && e.name.size() > 0 && e.name.size() <= 120;
    }

    match /workspaces/{wid} {
      allow read:  if isMember(wid);
      // creator must include themselves in memberIds
      allow create: if request.auth != null
                    && request.auth.uid in request.resource.data.memberUids;
      // only a member may change the doc, and may only ADD themselves
      allow update: if isMember(wid)
                    && request.resource.data.memberUids.hasAll(resource.data.memberUids)
                    && request.resource.data.memberUids.size() <= 5;
      allow delete: if false;                      // erasure is tombstone-then-sweep, §6.4

      match /{document=**} {
        allow read:  if isMember(wid);
        allow create, update: if isMember(wid) && validExpense();
        allow delete: if isMember(wid);
      }
    }
    match /{document=**} { allow read, write: if false; }
  }
}
```

Two deliberate properties: a user can **only add themselves** to a ledger's member list (they
cannot evict other members), and the member list is **capped at 5** so a shared ledger cannot be
turned into an open bucket.

Rules are tested in CI with `@firebase/rules-unit-testing` against the Firestore emulator,
including the negatives that matter: a non-member cannot read the ledger, a member cannot add a
6th account, a non-member cannot write an expense, and `amountPaise < 0` is rejected at the
server. See §14.4.

### 6.7 Sync UX

A discreet glass pill in the top bar: `Syncing…` / `Synced 2m ago` / `Offline — N pending` /
`Free-tier limit reached — resumes 00:00 PT`. Tapping it opens a sheet with workspace name,
member list, last-sync time, pending counts, and "Retry now". Errors are human-readable, never
block the UI, and never retry in a tight loop.

### 6.8 Quota engineering — making the free tier actually safe (D2)

Because Spark **hard-stops** on quota exhaustion with no overage, a naive sync loop could spend
the entire day's 20,000 writes before breakfast. Five mechanisms prevent it:

| Mechanism | Rule |
|---|---|
| **Debounced push** | A write is not flushed immediately; pushes are coalesced with an 800 ms trailing debounce, so "rename category" on 14 rows becomes **1 batched write**, not 14. |
| **Batched writes** | Maximum **400 operations** per `WriteBatch` (Firestore's hard cap is 500; headroom is kept for retry safety). A reorder of every category is one request. |
| **Watermark pull** | `where(updatedAt > lastPulledAt)` only — steady-state sync costs **0 reads**. |
| **Paged pull** | Reads are capped at **400 docs per sync run**, looping until caught up but with a per-run ceiling, so a first sync of a huge ledger never bursts the read quota in one sitting. |
| **`QuotaGovernor`** | A per-process budget tracker with a hard daily ceiling at 60 % of the published quota (12,000 writes / 30,000 reads). On `RESOURCE_EXHAUSTED` it enters a **quota-locked state** until the next midnight-Pacific reset: all sync work is suspended, the UI shows a human message with the exact reset time, and no retry is attempted. Exponential backoff applies to ordinary transient errors (500/503/unavailable); `RESOURCE_EXHAUSTED` bypasses backoff entirely and goes straight to the lock. |

`QuotaGovernor` is a pure, injectable class (clock + counters), unit-tested by simulating quota
exhaustion and asserting that **zero further requests** are issued until reset.

---

## 7. UX, Navigation & Motion

### 7.1 Navigation map

```
                       ┌──────────────────────────────┐
                       │      Onboarding / Auth        │
                       └───────────────┬──────────────┘
                                       │ first account linked
┌──────────────────────────────────────▼─────────────────────────────────────┐
│  Scaffold (edge-to-edge, glass nav bar)                                     │
│                                                                             │
│  ┌ Dashboard ┐ ┌ Expenses ┐   (+) FAB   ┌ Accounts ┐ ┌ Settings ┐           │
│  └───────────┘ └──────────┘  morphs into └──────────┘ └──────────┘           │
│        │              │      add-sheet          │            │              │
│        │              │                         │            │              │
│        └── Month detail sheet (shared-element) │                            │
│                                       │            │                          │
│                                       └── Account switcher sheet ───────────┘
│
│  Modal layer: AddEditSheet (partial-expanded → full), FilterSheet,
│               DatePickerSheet, QuickAddActivity (translucent, from widget)
└─────────────────────────────────────────────────────────────────────┘
```

The `+` FAB is a **morphing** control: tapping it expands into the Add sheet with a shared-element
transition; closing it collapses back. One control, two destinations, no duplicated affordance.

### 7.2 Screens

**A. Dashboard**
- Glass header: `₹ net total for 2026` with an animated count-up on year change; year selector
  pill (horizontal swipe or tap → bottom-sheet year grid, last 5 years + "add older data").
- **Monthly split**: 12 glass bars, height ∝ net spend, animated with staggered springs on entry
  and on year change. Tap a bar → month detail sheet (shared-element transition).
  - Selected bar: full-opacity glass with a 1 px inner highlight; others: 45 % opacity.
  - Bars carry a baseline value so ₹0 months are still tappable and visible.
  - Best month gets a subtle "peak" glow; the current month is marked with a dot.
  - **Negative months (D5)**: if refunds push a month below zero, the baseline moves to a
    centred axis, bars grow downward in the refund accent colour, and the axis line is drawn at
    zero with a `−₹` label. Never silently clamped to zero.
- **Category split**: donut (custom Canvas, animated sweep) + a legend with per-category **net**
  totals, sorted descending, with a horizontal proportion bar per row. Categories whose net is
  negative get a hollow/ghosted slice treatment plus an explicit "credit" label.
- **Summary row**: 3 glass stat tiles — Net total, Average/month, Highest month.
- **YoY**: delta pill vs. the same period last year (green/red, with an icon **and** text, never
  colour alone — accessibility).
- Pull-to-refresh triggers `SyncCoordinator.requestSync()`.
- **Empty state (D9 — this is the first thing CI screenshots and most users will see, so it is a
  designed surface, not a placeholder):** the full glass chrome renders exactly as it would with
  data — header, year selector, chart frame, legend frame — with the chart replaced by a centred
  frosted glass orb containing the app mark, the headline *"Nothing tracked yet"*, the sub-line
  *"Tap + to add your first expense"*, and a single glass CTA. Every bar slot renders as an
  empty, tappable track so the layout never reflows when data arrives. This is tested: the
  empty Dashboard and the populated Dashboard must occupy the same bounds (no layout jump).

**B. Expenses**
- Sticky month header + search field (frosted inset).
- List grouped by day with a glass date header (e.g. "Today · Tue, 12 Aug"), each row showing
  category icon in a tinted glass chip, name, time, and a tabular-figure right-aligned amount.
- **Refunds (D5)** render with a `↩` icon, a `−₹` amount in the credit accent, and a subtle
  "refund" chip — visually distinct without relying on colour alone.
- **Swipe right → delete** (row collapses + haptic), with a glass snackbar + "Undo" (8 s window).
- **Long-press → context row** with Duplicate / Edit / Change category / Delete / **Mark as
  refunded** (which opens the Add sheet pre-filled as a linked refund).
- Filter sheet: date range, categories (multi-select chips), amount range, text search.
- **Empty state (D9):** glass orb, *"No expenses here"*, and a line that names the *reason* —
  *"No expenses in August"* vs *"No results for 'coffee'"* vs *"No expenses match these filters"*
  — each with the corrective action (clear filters / change month / add one).

**C. Add / Edit expense (sheet)**
- Large amount display with a **custom numeric keypad** (INR-optimised: has a `₹` key, decimal
  handling with a max of 2 decimal places, and a long-press backspace to clear).
- **Kind toggle (D5):** a two-state glass segmented control — *Expense* / *Refund* — directly
  above the keypad. Selecting **Refund** reveals an optional "refund for…" link row that lists
  recent expenses in the same category to link against, and switches the amount accent to the
  credit colour. The keypad's `Done` key becomes *Save refund*.
- Name: frosted text field, IME `Visible` so the keypad never fights the system keyboard.
- Category: horizontally scrolling glass chips, single-select, haptic on change. A
  "Manage" chip at the end opens the category manager inline.
- Date: glass chip → date-picker sheet (calendar grid, "Today" shortcut, future dates allowed
  but flagged).
- Save: primary glass button, disabled state clearly legible; `Done` on the keypad saves.
- Duplicate mode from long-press action; edit mode prefills everything.

**D. Accounts & Ledger**
- A ledger header card: ledger name, member count, "N expenses · last synced 2m ago".
- Glass rows per linked Google account: avatar, email, "Active now" badge, and a **Remove**
  action (blocked, with an explanatory line, when only one member remains).
- "Link another Google account" glass CTA; if a ledger already exists, the join-vs-start choice
  sheet from §6.2 appears.
- "Switch ledger" control appears only when more than one workspace exists (the `choose B` path).

**E. Categories** (D4)
- List of categories with drag-to-reorder handles, inline rename, and hide toggles.
- "Add category" opens a small sheet: name field + icon grid + auto-assigned colour preview.
- Categories in use show `N expenses`; their Delete action is replaced by Hide with the reason.

**F. Settings**
- **Ledger & sync** — status, retry, member list, erase this ledger.
- **Data** — Export (JSON / CSV, with a date-range choice), Import JSON (with preview + explicit
  conflict strategy), and a one-line explanation of *why* Android Auto Backup is off (P5).
- **Appearance** — theme: System/Light/Dark; glass intensity: Subtle/Normal/**Exaggerated**
  (default, D10); High-contrast mode; Reduce motion.
- **Updates** — "Check for updates" (P10). The **only** network call outside sync, and only ever
  user-initiated.
- **Support** — "Report an issue" pre-fills a GitHub issue with version, API level and device
  model (P1's compensation for having no telemetry), plus app version and build type.
- **Privacy** — renders the PRIVACY.md content locally (P6).
- **About** — open-source licence attributions.

**G. Quick Add (widget entry point)**
- Translucent activity, edge-to-edge, no action bar, own scrim so the app behind stays visible
  through the glass. Same keypad + kind toggle + category chips + Save. Defaults to today; saves
  straight to Room; closes in ~250 ms with a haptic tick.

### 7.3 Gesture inventory

| Gesture | Location | Behaviour |
|---|---|---|
| Horizontal swipe | Dashboard | Change year (parallax bars, velocity-aware with rubber-banding at the ends) |
| Horizontal swipe | Expenses | Change month, with the sticky header snapping |
| Tap a bar | Dashboard | Month detail sheet (shared-element) |
| Swipe right on row | Expenses | Delete with undo + haptic |
| Long-press | Expense row | Context actions with radial pop-in (incl. **Mark as refunded**, D5) |
| Long-press category chip | Add sheet | Pinned "recent" ordering |
| Drag handle | Category manager | Reorder, committing one batched write (§6.8) |
| Vertical drag on sheet | Any sheet | Collapse/dismiss with velocity; springs back if under threshold |
| Two-finger pinch | Dashboard donut | Scale legend density (a11y alternative to colour-only encoding) |
| Pull down | Dashboard / Expenses | Sync now |
| Swipe-down on empty space | Expenses | Open quick filter |
| Edge swipe back | Everywhere | Native predictive-back animation (Android 13–16) |

### 7.4 Motion system

All motion is spring-driven; there are no fixed-duration `tween`s except for opacity fades.

```kotlin
object Motion {
  val Emphasized    = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)   // primary reveals
  val Snappy        = spring<Float>(dampingRatio = 0.9f, stiffness = 700f)   // chips, haptics
  val Gentle        = spring<Float>(dampingRatio = 1.0f, stiffness = 200f)   // large surfaces
  val Bouncy        = spring<Float>(dampingRatio = 0.65f, stiffness = 500f)  // FAB morph, FABs
  const val FadeMs   = 180
}
```

- **Shared elements** between Dashboard → Month detail (Compose `SharedTransitionLayout`,
  `ExperimentalSharedTransitionApi` opt-in, isolated behind a single `MotionFeature` flag so an API
  change is a one-file fix).
- **Chart animation**: bars grow with a 40 ms stagger; the donut sweeps via `Animatable`;
  the total number **count-up**s using `tabular` figure font settings so digits don't jitter
  (this is why `FontFeatureSettings = "tnum"` is set on the amount text style).
- **Haptics**: `HapticFeedbackType.TextHandleMove` on keypad taps, `Confirm` on save,
  `LongPress` on row long-press. Every haptic has a settings toggle.
- **Reduced motion**: when the system animator scale is 0 or the in-app toggle is on, all spring
  animations collapse to instant state changes and shared elements become fades.
- **Predictive back**: `android:enableOnBackInvokedCallback="true"` in the manifest, with
  `PredictiveBackHandler` driving sheet dismiss progress.

---

## 8. Glassmorphism & Legibility System (the core design-engine piece)

### 8.1 The efficiency insight

A live backdrop blur (re-blurring whatever is behind the surface every frame) is the usual
cause of jank in glassy Android apps. This app does **not** do that.

**The backdrop is a static, downsampled, pre-blurred bitmap.** It is built once per
process (and cached), then reused as the backdrop tint for every glass surface. The cost is a
single bitmap draw — effectively free, on any GPU, at any refresh rate, even at 120 Hz.

This is possible because the app shell's background is a **static wallpaper / theme gradient**.
Content scrolls *above* the glass, not behind it, so there is nothing live to re-blur.

```
┌──────────────────────────────────────────────┐
│  layer 0  Wallpaper or theme gradient (static)│
│  layer 1  Frosted backdrop  ◄── built ONCE   │
│             (1/16 scale, blurred, cached)    │
│  layer 2  Content (lists, charts)            │
│  layer 3  Glass surfaces — translucent, so   │
│             layers 0–2 read through faintly  │
└──────────────────────────────────────────────┘
```

### 8.2 Backdrop construction

```kotlin
internal object Frost {
    /** 1/16 of the linear resolution: ~30x80 px. Blur cost is negligible, memory is ~10 KB. */
    const val SCALE = 0.0625f
    /** Sigma at the *scaled* size → visually equivalent to ~48 px at full resolution. */
    const val SIGMA  = 3.0f
}

@Composable
fun rememberFrostedBackdrop(): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(ctx) { runCatching { buildBackdrop(ctx) }.getOrNull() }
        ?.also { Log.d("Frost", "backdrop ready") }
}

private fun buildBackdrop(ctx: Context): ImageBitmap {
    val dm = ctx.resources.displayMetrics
    val w = (dm.widthPixels  * Frost.SCALE).roundToInt().coerceAtLeast(16)
    val h = (dm.heightPixels * Frost.SCALE).roundToInt().coerceAtLeast(16)

    val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(src).apply {
        val srcDrawable = WallpaperManager.getInstance(ctx).fastDrawable
            ?: ctx.getDrawable(R.drawable.bg_gradient_default)!!
        srcDrawable.setBounds(0, 0, w, h)
        srcDrawable.draw(this)
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // API 31+: one-shot RenderEffect on a 30x80 bitmap — sub-millisecond.
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint().apply {
            renderEffect = RenderEffect.createBlurEffect(
                Frost.SIGMA, Frost.SIGMA, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawBitmap(src, 0f, 0f, p)
        src.recycle()
        out.asImageBitmap()
    } else {
        // API 29–30: no RenderEffect. The 1/16 downsample alone is already low-frequency,
        // so upscaling it reads as frosted glass. No third-party blur dependency needed.
        src.asImageBitmap()
    }
}
```

**Cost budget (measured, not guessed — asserted in CI):** backdrop build ≤ 4 ms, memory ≤ 64 KB,
executed once, off the first-frame critical path (kicked off in `Application.onCreate` on a
low-priority dispatcher, with the gradient fallback showing until it lands).

### 8.3 The glass surface

Default intensity is **Exaggerated (D10)** — heavy frost, unmistakably glass. `Subtle` and
`Normal` remain user-selectable below it; there is deliberately no "above exaggerated" tier,
because past this point legibility degrades faster than the glass effect improves.

```kotlin
enum class GlassTier(val blurSigma: Float, val tintAlpha: Float, val strokeAlpha: Float) {
    Subtle     (sigma = 2.5f, tintAlpha = 0.50f, strokeAlpha = 0.12f),  // user-selectable
    Regular    (sigma = 3.5f, tintAlpha = 0.66f, strokeAlpha = 0.16f),  // user-selectable
    Exaggerated(sigma = 5.0f, tintAlpha = 0.82f, strokeAlpha = 0.22f),  // DEFAULT (D10)
    Inset      (sigma = 0.0f, tintAlpha = 0.36f, strokeAlpha = 0.10f),  // inputs, no backdrop
}
```

The aggressive alphas are safe *because* legibility is verified rather than eyeballed: at
`tintAlpha = 0.82` the composited glass colour is close enough to the opaque token that the 7:1 /
4.5:1 assertions in §8.4 hold with real margin, and every text container additionally receives a
scrim. Dropping the alpha for the glassier look would break those assertions, and the test would
say so.

```kotlin
enum class GlassIntensity(val scale: Float, val sigmaScale: Float) {
    Subtle      (scale = 0.86f, sigmaScale = 0.70f),
    Normal      (scale = 1.00f, sigmaScale = 1.00f),
    Exaggerated (scale = 1.00f, sigmaScale = 1.00f),   // the default; == baseline
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.Exaggerated,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    glassIntensity: GlassIntensity = LocalGlassIntensity.current,   // user setting
    content: @Composable BoxScope.() -> Unit,
) {
    val t = MaterialTheme.glassTokens(tier)
    val alpha = (t.tintAlpha * glassIntensity.scale).coerceAtMost(0.94f)
    val sigma = t.blurSigma * glassIntensity.sigmaScale
    Box(
        modifier
            .clip(shape)
            .background(t.tint.copy(alpha = alpha))                 // translucent base
            .drawWithContent {
                drawContent()
                // 1) top sheen — the diagonal highlight that sells "glass"
                drawRect(Brush.linearGradient(t.sheen), size = size)
                // 2) inner stroke — bright at the top-left, fading out; this replaces elevation
                drawRect(t.innerStroke, style = Stroke(width = 1.dp.toPx()))
                // 3) optional legibility scrim behind text blocks
                if (t.scrim != null) drawRect(t.scrim)
            }
            .semantics { }                                        // no opaque a11y interception
    ) { content() }
}
```

**Never used:** `Modifier.blur` applied to scrolling content, per-frame `RenderEffect`, or
`RenderScript`. Those are the three usual jank sources and are explicitly forbidden by a
detekt rule (`ForbiddenApi("Modifier.blur on scrolling containers")`).

### 8.4 Legibility: the non-negotiable rules

Legibility is enforced structurally, not by eye.

1. **Never text directly on the backdrop.** All text sits on a `GlassSurface` whose tint is
   composited over the worst-case backdrop colour before contrast is evaluated.
2. **Contrast-tested tokens.** `GlassTokens` ships a `GlassContrastTest` unit test that composites
   every (tier × theme × highContrast) combination over both a light and a dark worst-case
   backdrop, then asserts **≥ 7:1 for headlines/numbers** and **≥ 4:5… ≥ 4.5:1 for body text**.
   A failing contrast assertion fails CI. This is the single most important test in the repo.
3. **Scrims.** Headers and dense numeric regions get a `verticalGradient` scrim that raises local
   contrast without flattening the glass.
4. **Sheen never crosses text.** The sheen is masked to the top ~30 % of the surface and clipped
   away from text baselines via an explicit `contentPadding` contract.
5. **Type floor.** Body 16 sp, secondary 14 sp, captions 12 sp — with `FontFeatureSettings "tnum"`
   on all numeric styles. `sp` (not `dp`) everywhere, so system font scaling works.
6. **High-Contrast mode** (user setting, also auto-enabled when the system reports
   `high_text_contrast`): surface alpha jumps to 0.85, stroke to 0.35, body text weight to
   Medium, and the scrim becomes near-opaque. Visually still glass, far more legible.
7. **Never colour alone.** Category colours are always paired with an icon and a text label.
   Delta values are always `icon + text + colour`. Refunds always carry a `↩` icon and the word
   "refund", so the credit accent is decorative rather than load-bearing.
8. **200 % font-scale layout test** in the instrumented suite: the Add sheet and Dashboard must
   remain fully usable without clipping (scrollable regions, no fixed heights on text containers).
9. **Legibility is asserted for the empty state too (D9)** — the empty-state orb's text sits on the
   same `Exaggerated` glass as populated content and is covered by the same contrast test.

### 8.5 Theme — light and dark, both first-class (D8)

Light mode is genuinely the harder problem for glass: a light frosted panel over a light wallpaper
loses contrast far faster than the reverse. Rather than shipping dark mode properly and light mode
as an inverted afterthought, both get **complete, independently tuned token sets**, and the
contrast test runs over **both**.

| | Dark | Light |
|---|---|---|
| Backdrop | Brand gradient (indigo→teal) or system wallpaper, dimmed 55 % | Same gradient, lifted toward white 25 % |
| Glass tint | `#0B1020` @ `tintAlpha` — darkens what's behind it | `#FFFFFF` @ `tintAlpha` — lightens what's behind it |
| Inner stroke | `#FFFFFF` @ 0.22 — a bright top-left rim | `#0B1020` @ 0.18 — a *dark* rim (a white rim would vanish) |
| Sheen | Broad, low-opacity white sweep | Narrow, slightly brighter sweep to survive the white base |
| Text primary | `#F5F7FA` | `#0B1020` |
| Credit / refund | `#7FD1AE` | `#0E6B4F` |
| Expense accent | Brand hue, 2 shades brighter | Brand hue, 2 shades darker |

The stroke colour flipping from light to dark is the detail that separates "dark mode with an
inverted palette" from real first-class light theming — a white-on-white edge is invisible, and the
panel stops reading as a distinct surface.

- **Dynamic colour** (Material You) drives the accent hue on API 31+ in both schemes.
- **Fallback** is the brand gradient, used when dynamic colour is unavailable or the user picks
  "Brand theme".
- Three glass intensities scale the tiers (§8.3); **Exaggerated is the default**.
- Both schemes are covered by `GlassContrastTest`, and both are captured by the screenshot job
  (§14.2), so a regression in one can never hide behind the other.

---

## 9. Dashboard Aggregation

```
GetYearlyDashboard(year) : Flow<YearDashboard>          // no accountId (D1: one ledger)
  ├─ Room query: expenses in [Jan 1, Dec 31] where deletedAt IS NULL
  ├─ Aggregate in Kotlin (single pass, Long arithmetic — no float sums)
  │    monthlyNet: LongArray(12)          // signed: charges − refunds (D5)
  │    categoryNet: Map<String, Money>    // signed per category
  │    grossSpend, totalRefunds, netTotal, dayCount, maxDay, avgPerActiveDay
  ├─ Fill zero for empty months (always 12 bars, even with no data at all — D9)
  ├─ Determine scale: the bar chart needs max(+gross, −maxRefundMagnitude) so a net-negative
  │    month is drawn against the same scale as positive ones (no per-month rescaling, which
  │    would make the year view visually lie)
  ├─ previous-year same-period net → YoY delta
  └─ Emitted as an immutable data class; recomputed only on data change (distinctUntilChanged)
```

`GetMonthlyBreakdown(year, month)` powers the drill-down sheet, with a running
"top spenders by category" list and, where applicable, the refunds recorded that month.

**Sign rules (D5), asserted by tests:**

| Quantity | Definition |
|---|---|
| `grossSpend` | Σ `Money.of(amount, EXPENSE)` |
| `totalRefunds` | Σ `Money.of(amount, REFUND)` (a negative number) |
| `netTotal` | `grossSpend + totalRefunds` |
| `categoryNet` | per category, same formula |
| A month is "net negative" when `netTotal < 0` | renders the downward bar axis (§7.2A) |

The Dashboard header always shows **net** total as the headline, with `grossSpend` and
`totalRefunds` available as secondary figures — because "how much did I spend" and "how much came
back" are different questions and conflating them is how expense apps start lying.

All currency math is `Long` paise end-to-end. A unit test asserts no precision loss across a
fuzz set of 10 000 random amounts (§13).

---

## 10. Widget Design

### 10.1 Shape

A Glance widget, minimum 2×2, responsive up to 4×4.

```
┌──────────────────────────────┐
│  August 2026        ● synced│   ← header row, tiny, low contrast by design (still ≥4.5:1)
│                              │
│  ₹ 24,318                   │   ← current MONTH net total, 28sp, tabular figures
│  today ₹ 640                 │
│  ▁▂▃▅▂▇  last 7 days        │   ← 7-bar sparkline
│  [Food][Travel]  [ + Add ]   │   ← top categories + primary action
└──────────────────────────────┘
```

- Tap the body → opens the app at Dashboard.
- Tap `+ Add` → launches `QuickAddActivity` (translucent, edge-to-edge, glass keypad).
- Long-press → standard Android widget options (resize, page).
- If the current month's net is negative, the headline renders with a leading `−` and a
  `↩` marker (same "never colour alone" rule as in-app).

Because the ledger is shared (D1), the widget shows the **same totals to every linked account** —
there is no per-account widget variant, and the long-press menu no longer offers "switch account".

### 10.2 Widget honesty about glass

**Real backdrop blur is not available to widgets** — `RemoteViews` cannot render a backdrop
filter, and `AppWidgetHost` composes the widget over the launcher without exposing it. So the
widget uses a **flat translucent scrim** (`#CC0B1020` dark / `#E6FFFFFF` light) with a subtle
gradient and a 1 px inner stroke (dark in light mode, per §8.5). It reads as the same design
language, stays perfectly legible over **any** launcher wallpaper — which a fake blur could not
guarantee — and costs nothing. This is a deliberate, documented design decision, not a shortcut.

### 10.3 Update mechanics

- `updatePeriodMillis = 1800000` (30 min — the system minimum; lower values are clamped).
- `appwidget-provider` declares `resizeMode="horizontal|vertical"`, `previewLayout`,
  `widgetCategory="home_screen"`, `minWidth/Height` for 2×2 and 4×4.
- `SizeMode.Responsive` so one `Glance` composable serves both sizes with different layouts.
- **Reactive**: `KharchGlanceWidget` is a `GlanceAppWidget` + `GlanceAppWidgetReceiver`. Any
  Room write triggers `ExpenseRepository.changes` → `KharchWidget.updateAll(context)`.
  The widget is therefore correct **immediately** after an in-app edit, not up to 30 minutes later.
- Full update → `updateAll`; partial → `updateAppWidgetState` on the specific widget id.
- `ActionCallback` handles in-widget interactions that need no text input (e.g. cycle the month
  shown). Anything needing input routes to `QuickAddActivity`.
- `GlanceAppWidgetPreview` uses Compose previews so the widget's layout is reviewable in code.
- Widget state is read from Room **directly** (no ViewModel), inside
  `provideGlance`, on a coroutine — never on the main thread.
- **Empty state (D9)**: with no expenses the widget shows `₹ 0` plus *"Tap + to log your first
  expense"* rather than blank bars. The screenshot job captures the widget in this state, so the
  very first thing a user sees after installing is a reviewed surface.

---

## 11. Toolchain & Project Configuration

### 11.1 Repository layout

```
expense_tracker/
├── .github/
│   ├── workflows/{build,instrumented,perf,rules,release}.yml
│   └── dependabot.yml
├── app/
│   └── src/{main,test,androidTest}/...
├── baselineprofile/                  ← Macrobenchmark module (P7)
├── gradle/libs.versions.toml
├── gradle/verification-metadata.xml  ← dependency verification (P8)
├── gradle/wrapper/gradle-wrapper.properties
├── build.gradle.kts · settings.gradle.kts · gradle.properties
├── firestore.rules · firestore.indexes.json · firebase.json
├── scripts/check-no-destructive-migration.sh   ← CI guard (P9)
├── PRIVACY.md                        ← P6
└── plan.md
```

### 11.2 Version catalogue (pinned; verify exact values against release notes at kickoff)

```toml
[versions]
agp = "8.x.y"           # latest stable
kotlin = "2.2.x"
ksp = "2.2.x-1.0.x"
composeBom = "<latest>"
hilt = "2.5x"
room = "2.7.x"
glance = "1.1.1+"
firebaseBom = "<latest>"
credential = "1.3+"
navigationCompose = "2.8+"
workManager = "2.10+"
coroutines = "1.9+"
turbine = "1.2.x"
androidxTest = "<latest>"
```

`androidx.compose:compose-bom` is used so all Compose artifacts move together and stay
binary-compatible — the single most common source of "works on my machine" Compose breakage.

### 11.3 Manifest essentials

```xml
<application
    android:enableOnBackInvokedCallback="true"     <!-- predictive back, required for target 35+ -->
    android:usesCleartextTraffic="false"
    android:supportsRtl="true"
    android:localeConfig="@xml/locales_config"
    android:enableAppLinks="false"
    android:dataExtractionRules="@xml/data_extraction_rules">  <!-- cloud backup opt-out for the Room files -->

<activity android:name=".MainActivity"
    android:windowSoftInputMode="adjustResize"
    android:theme="@style/Theme.Kharch.Starting"/>

<!-- Widget entry: the quick-add activity -->
<activity android:name=".widget.QuickAddActivity"
    android:theme="@style/Theme.Kharch.Translucent"
    android:excludeFromRecents="true"
    android:noHistory="false"
    android:exported="true"
    android:launchMode="singleTask"
    android:taskAffinity=""/>
```

`Theme.Kharch.Translucent` is `windowIsTranslucent=true`, `windowBackground=@android:color/transparent`,
`windowNoTitle=true`, `backgroundDimEnabled=false` (we draw our own scrim), plus
`enableEdgeToEdge()` in `onCreate`.

### 11.4 Gradle configuration that matters

```kotlin
android {
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        targetSdk = 36
        versionCode = <bump-per-release>; versionName = "1.0.0"
        testInstrumentationRunner = "com.srinidhi.kharch.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = 17 }
    kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
    buildTypes {
        debug   { applicationIdSuffix = ".debug"; isMinifyEnabled = false }
        release {
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigningConfig            // from CI keystore secret
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true                  // Robolectric-capable
        execution = "ANDROIDX_TEST_ORCHESTRATOR"                   // clean state per test
        animationsDisabled = false                                 // exercise the motion system
        retry { maxRetries = 2, maxFailures = 3 }                  // §16 CI-flakiness mitigation
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
```

`proguard-rules.pro` must keep Room entities, Firestore DTOs, Hilt, and Glance receivers.
A release build that is minified and unverified is a common CI trap — hence the `release.yml`
job does a `minifyEnabled` smoke install.

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

---

## 12. Firebase & Google Sign-In Setup (one-time)

1. Create a Firebase project on the **Spark (free) plan — no billing account, no credit card.**
   This is a hard requirement of D2; do not enable Blaze, and do not create a Cloud Storage
   bucket (it has required a linked billing account since 3 Feb 2026).
2. Add an **Android** app with package name `com.srinidhi.kharch` (and
   `com.srinidhi.kharch.debug` for the debug variant). **Confirm the package name before this
   step** (D11) — it is registered in Firebase and effectively permanent.
3. **Commit `google-services.json` to the repo (D3).** It is not a secret — access is gated by
   package name + signing SHA — and committing it keeps local and CI builds byte-identical.
   Optionally add the release SHA-1 and the debug SHA-1 under Project Settings → Your apps.
4. Enable the **Google** sign-in provider under Authentication → Sign-in method.
5. Create a Firestore database in the region nearest you. Note the free tier covers exactly
   **one** database per project (§6.8).
6. Deploy rules and indexes:
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes
   ```
   `workspaces/{wid}/expenses` on `updatedAt ASC` is served by the automatic single-field index, so
   the watermark pull needs **no composite index**. Keep composite indexes well under the 200 the
   Spark plan allows.

---

## 13. Testing Strategy

### 13.1 Test pyramid

| Layer | Count (target) | What it covers |
|---|---|---|
| Pure JVM unit tests | ~145 | Money, formatting, refunds, aggregation, conflict resolution, **quota governor**, reducers, ViewModel state |
| Robolectric tests | ~28 | Room DAO, DataStore, widget state providers, **empty-state composition** (no device needed) |
| Compose UI tests (instrumented) | ~48 | Full user journeys on the emulator |
| Firestore emulator rule tests | ~14 | Workspace membership rules, field validation |
| Firebase SDK integration | ~7 | Sign-in flow, shared-ledger convergence, sync round-trip (emulator, never production) |
| **Migration tests** | ~6 | Every schema upgrade preserves a realistic ledger (P9) |
| **Export/Import round-trip** | ~5 | Export → wipe → import is byte-identical (P2) |
| **Macrobenchmark** | ~4 | Startup + frame-timing budgets (P7) |

### 13.2 Mandatory test list

**Money & formatting**
- `₹0`, `₹9.99`, `₹1,00,000`, `₹1,23,456.78`, `₹12,34,567.89` — Indian grouping.
- Fuzz: 10 000 random paise values → sum via `Long` == sum via `BigDecimal`.
- Currency symbol placement, zero handling, and non-`en-IN` locale fallback.

**Refunds (D5)** — the highest-risk new logic
- `Money.of(m, EXPENSE)` is positive; `Money.of(m, REFUND)` is negative; `isCredit` agrees.
- Refund of a refund nets out to the right sign.
- A month with 500 expense and 700 refund aggregates to **net −200** and renders a downward bar.
- Year totals equal the sum of the 12 monthly nets (no drift, even with negatives).
- `linkedExpenseId` pointing at a deleted expense degrades gracefully (no crash, no orphan).
- The net-negative month never rescales the shared chart baseline (§9).
- Refund rows carry `↩` + the word "refund" in their semantics tree (not colour alone).

**Domain**
- Dashboard aggregation: empty year, single month, all-zero months, year boundary
  (31 Dec / 1 Jan — a classic off-by-one-day bug).
- Leap year (2028 Feb 29) and month-length arithmetic.
- `YearMonth` maths with a fake `Clock` (no `LocalDate.now()` in domain code).

**Categories (D4)**
- Create / rename / reorder / hide / delete-when-used are rejected / delete-when-unused succeeds.
- Rename rejects empty and >40-char names, and case-insensitive duplicates.
- Reorder renumbers contiguously and issues **exactly one** batched write (§6.8).
- A hidden category still renders on historical expenses.

**Conflict resolution** (exhaustive pairwise matrix)
- remote newer / local newer / equal timestamps / delete-vs-edit / clock skew / tombstone purge
  after 30 days.
- **Equal-timestamp, two different members editing the same expense** → the lexicographic
  `uid` tiebreaker (§6.4.4) makes both devices converge on the *same* row.

**Quota governor (D2)** — the safety-critical one
- Simulate `RESOURCE_EXHAUSTED` → governor enters locked state → **assert zero further requests**
  are issued until the next reset, and the UI message names the reset time.
- An ordinary 503 → exponential backoff, then success.
- The 60 %-of-quota ceiling stops a runaway push loop before the real quota is hit.

**Room**
- CRUD, `dateEpochDay` range queries, category soft-delete, cascade on category change.

**Sync coordinator**
- Push ordering, retry on transient failure, exponential backoff, `Result.retry()` paths,
  cancellation mid-batch, duplicate-push idempotency.
- Watermark pull issues **0 reads** when nothing changed.
- A 5 000-document first sync pages at 400/request and terminates.

**Shared ledger (D1)** — replaces the old isolation tests
1. First run → Google sign-in (fake credential) → workspace created, 12 categories seeded.
2. Add expense via the keypad → appears in the list with the correct amount and INR grouping.
3. Add expense from the **widget** → widget total updates without opening the app.
4. Swipe-to-delete → row removed → **Undo** restores it.
5. Dashboard year swipe → bars re-animate; tapping a bar opens the month detail.
6. **Link account B to an existing ledger → B sees exactly the same expenses as A.** (The
   inverse of the original isolation test.)
7. **Remove account B → the ledger and all expenses survive**, since no data is owned by a member.
8. Removing the last member is blocked with an explanatory message.
9. Signing in a new account when a ledger exists shows the join-vs-start choice sheet; choosing
   "start separate" creates workspace #2 and leaves #1 intact.
10. High-contrast toggle → tokens change; and separately, **light and dark each pass contrast**.
11. 200 % font scale → Add sheet fully usable, no clipped text.
12. Offline → add three expenses → go online → all three sync (emulator-mediated).

**Empty state (D9)**
- Dashboard with zero expenses renders 12 tappable empty bar tracks, and the empty and populated
  states have **identical overall bounds** (no layout jump on first data).
- The Expenses empty state names the correct reason (no data vs no search results vs no filter
  match) and offers the matching action.
- The widget empty state shows `₹ 0` + guidance rather than blank bars.

**Accessibility**
- Every interactive element exposes a meaningful label (asserted in a test that walks the tree).
- All touch targets ≥ 48 dp (asserted programmatically).

### 13.3 Widget tests

`GlanceAppWidget` UI is tested via Robolectric with `GlanceAppWidgetTestRule` +
`captureGlance` (robolectriclectric), asserting: correct month total, empty-state rendering,
and that `updateAll` reflects a newly inserted Room row.

---

## 14. CI/CD — Build and Test on GitHub Only

All work happens in GitHub Actions. **The APK is never downloaded locally** — it is uploaded as a
workflow artifact, inspectable via `gh run view <id> --log` if needed.

### 14.1 `.github/workflows/build.yml` — compile, unit test, lint

```yaml
name: Build & Unit Test
on:
  push: { branches: [main] }
  pull_request:
  workflow_dispatch:
concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 40
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: gradle }

      - uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - uses: android-actions/setup-android@v3

      - name: Validate version catalogue
        run: ./gradlew :app:dependencies --configuration releaseRuntimeClasspath > /dev/null

      - name: Unit tests
        run: ./gradlew testDebugUnitTest --stacktrace

      - name: Lint
        run: ./gradlew lintDebug

      - name: Assemble debug APK
        run: ./gradlew assembleDebug

      - name: Assemble release bundle
        run: ./gradlew assembleRelease

      - name: No destructive migrations (P9)
        run: |
          if grep -rn "fallbackToDestructiveMigration" app/src/ ; then
            echo "::error::Destructive migration found — a user's ledger would be silently deleted."
            exit 1
          fi

      - name: Detekt (P8 — no user data in logs, no Modifier.blur on scrolling content)
        run: ./gradlew detekt
        continue-on-error: false

      - name: APK size gate
        run: |
          DEBUG_SIZE=$(stat -c%s app/build/outputs/apk/debug/app-debug.apk)
          RELEASE_SIZE=$(stat -c%s app/build/outputs/apk/release/app-release.apk)
          echo "debug:   $((DEBUG_SIZE/1024)) KB  (budget 13312 KB)"
          echo "release: $((RELEASE_SIZE/1024)) KB  (budget  8192 KB)"
          [ "$DEBUG_SIZE"   -lt $((13*1024*1024)) ] || { echo "Debug APK exceeds budget";   exit 1; }
          [ "$RELEASE_SIZE" -lt $(( 8*1024*1024)) ] || { echo "Release APK exceeds budget"; exit 1; }

      - name: Upload artifacts (not downloaded locally)
        uses: actions/upload-artifact@v4
        with:
          name: apks-${{ github.sha }}
          retention-days: 14
          path: app/build/outputs/apk/**/*.apk

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: reports-${{ github.sha }}
          path: |
            app/build/reports/tests/**
            app/build/reports/lint-results*.html
```

### 14.2 `.github/workflows/instrumented.yml` — run tests on a real emulator

```yaml
name: Instrumented Tests
on:
  push: { branches: [main] }
  pull_request:
  workflow_dispatch:
  schedule: [{ cron: '0 3 * * *' }]     # nightly sweep on the default branch
permissions:
  contents: read

jobs:
  emulator-tests:
    strategy:
      fail-fast: false
      matrix:
        include:
          - api-level: 35      # primary target (Android 15)
            target: google_apis
            profile: Pixel_6
          - api-level: 34
            target: google_apis
            profile: Nexus_6
          - api-level: 36
            target: google_apis
            profile: Pixel_8
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: gradle }
      - uses: gradle/actions/setup-gradle@v4
      - uses: android-actions/setup-android@v3

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Instrumented tests (animation on)
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          target: ${{ matrix.target }}
          arch: x86_64
          profile: ${{ matrix.profile }}
          disable-animations: false
          emulator-options: -no-window -no-snapshot -gpu swiftshader_indirect -no-boot-anim -camera-back none
          script: ./gradlew connectedDebugAndroidTest --stacktrace

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: instrumented-results-${{ matrix.api-level }}
          path: app/build/reports/androidTests/**

  screenshot-smoke:
    needs: emulator-tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: gradle }
      - uses: gradle/actions/setup-gradle@v4
      - uses: android-actions/setup-android@v3
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 35
          target: google_apis
          arch: x86_64
          disable-animations: false   # keep animations on to exercise the motion system
          script: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.srinidhi.kharch.screenshot.ScreenshotTest
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: screenshots-${{ github.sha }}
          path: app/build/reports/androidTests/**/screenshot*.png
```

A `ScreenshotTest` captures **empty states (D9)** — no sample data is seeded (D9), because the
first-run experience is what CI should be proving looks right. Captured surfaces:

| Surface | Variants |
|---|---|
| Onboarding / sign-in | dark, light |
| Dashboard — empty | dark, light, 100 % font, 200 % font |
| Expenses — empty | dark, light, plus the "no search results" variant |
| Add sheet | dark, light, 200 % font, with the Refund segment selected |
| Category manager | dark, light |
| Widget | 2×2 dark, 2×2 light, 4×4 dark, **empty state** |
| Quick Add activity | over a busy underlying app, proving the translucent glass reads |

Because a light-mode regression can hide behind a good dark one (D8), every surface is captured
in **both** schemes. The set is uploaded as an artifact — UI regressions are visible in the PR
without anyone installing the APK.

### 14.3 `.github/workflows/release.yml` — signed release on tag

```yaml
name: Release
on:
  push: { tags: ['v*'] }
permissions: { contents: write }
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: gradle }
      - uses: gradle/actions/setup-gradle@v4
      - uses: android-actions/setup-android@v3
      - name: Decode keystore
        env:
          KEYSTORE_B64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASS: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASS: ${{ secrets.KEY_PASSWORD }}
        run: |
          echo "$KEYSTORE_B64" | base64 --decode > "$RUNNER_TEMP/keystore.jks"
          echo "storeFile=$RUNNER_TEMP/keystore.jks" >> "$GITHUB_ENV"
          echo "storePassword=$KEYSTORE_PASS" >> "$GITHUB_ENV"
          echo "keyAlias=$KEY_ALIAS" >> "$GITHUB_ENV"
          echo "keyPassword=$KEY_PASS" >> "$GITHUB_ENV"
      - run: ./gradlew bundleRelease assembleRelease
      - uses: actions/upload-artifact@v4
        with: { name: release-apk, path: app/build/outputs/apk/release/*.apk }
      - uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/*.apk
          generate_release_notes: true

      # P10 — publish the update manifest (Firebase Hosting, Spark plan: free).
      # Not the GitHub API: this repo is private, so an unauthenticated
      # /releases/latest returns 404 and the in-app updater would never find anything.
      - name: Publish update manifest (P10)
        env:
          FIREBASE_TOKEN: ${{ secrets.FIREBASE_CLI_TOKEN }}
          VERSION_CODE: ${{ github.run_number }}
        run: |
          jq -n --argjson vc "$VERSION_CODE" \
                --arg vn "${GITHUB_REF#refs/tags/v}" \
                --arg url "https://github.com/${{ github.repository }}/releases/download/${GITHUB_REF#refs/tags/v}/app-release.apk" \
                --arg notes "$(git tag -l --format='%(contents)' ${GITHUB_REF#refs/tags/v} | head -40)" \
                '{versionCode:$vc, versionName:$vn, url:$url, notes:$notes, mandatory:false}' \
            > public/version.json
          firebase deploy --only hosting --project "${{ vars.FIREBASE_PROJECT }}" \
            --token "$FIREBASE_TOKEN"

      - name: Upload build provenance (P8)
        uses: actions/attest-build-provenance@v1
        with: { subject-path: 'app/build/outputs/apk/release/*.apk' }
```

### 14.4 `.github/workflows/firestore-rules.yml` — security rules tests

```yaml
name: Firestore Rules Tests
on: [push, pull_request, workflow_dispatch]
permissions: { contents: read; issues: write }
jobs:
  rules:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20', cache: npm }
      - uses: w9jds/firebase-cli@latest
        with: { args: deploy --only hosting --project demo-expense }   # demo project, never production
      - run: npm ci
      - run: npm run rules:test    # firebase emulators:exec --only firestore "mocha"
```

Rules tests assert the membership invariants in §6.6 — the security-critical ones:

1. A member can read and write the ledger's expenses and categories.
2. A **non-member cannot read the ledger at all** (including a ledger they once belonged to).
3. A non-member cannot write an expense, even with a valid document shape.
4. A member **cannot add a 6th account** (`memberUids.size() <= 5` is enforced server-side).
5. A member cannot remove another member (the `hasAll` guard).
6. An unauthenticated request is denied everywhere.
7. `amountPaise < 0` is **rejected by the server** — the refund model (§5.1) cannot be bypassed.
8. `kind` outside `{EXPENSE, REFUND}` is rejected; `name` over 120 chars is rejected.
9. The workspace document itself cannot be hard-deleted (erasure is tombstone-then-sweep).

These run against the **emulator with the demo project**, never against production, so the suite
is free to run on every PR without spending Spark quota.

### 14.5 `.github/workflows/perf.yml` — performance budgets (P7)

```yaml
name: Performance Budgets
on:
  pull_request:
  workflow_dispatch:
  schedule: [{ cron: '0 4 * * 1' }]     # weekly, catches SDK-drift regressions
permissions: { contents: read }
jobs:
  macrobenchmark:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: gradle }
      - uses: gradle/actions/setup-gradle@v4
      - uses: android-actions/setup-android@v3
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 35
          target: google_apis
          arch: x86_64
          profile: Pixel_6
          disable-animations: false          # the glass transitions must actually animate
          script: ./gradlew :baselineprofile:benchmarkMacrobenchmark \
                            :app:benchmarkRelease -Pandroid.testInstrumentationRunnerArguments.class=com.srinidhi.kharch.perf.StartupBudgetTest,com.srinidhi.kharch.perf.FrameTimingBudgetTest
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: perf-report-${{ github.sha }}
          path: baselineprofile/build/reports/**
```

Budgets are asserted inside the tests, so a regression fails the job rather than producing a report
nobody reads. Cold start < 1.2 s, warm < 400 ms, no frame > 50 ms at p90 on Dashboard and Add-sheet
open. A `BaselineProfile` artifact is generated here and diff-checked so it cannot silently rot.

### 14.6 Dependency verification config (P8)

```properties
# gradle.properties
org.gradle.dependency.verification=strict
org.gradle.dependency.verification.console=verbose
```

`gradle/verification-metadata.xml` is **committed**, generated once with
`./gradlew --write-verification-metadata sha256,pgp help` and refreshed deliberately when
dependencies change. A tampered or typosquatted artifact fails the build rather than shipping.
Additionally, every `uses:` in every workflow is pinned to a 40-character commit SHA (never
`@v4`), Dependabot keeps those pins current, and `main` is protected with required CI.

### 14.7 Observability from the CLI (never downloading the APK)

```bash
# Trigger and watch — the entire feedback loop from the terminal
gh workflow run build.yml --ref main
gh run watch

# Structured summary
gh run view --json conclusion,jobs,url

# Pull specific logs without downloading artifacts
gh run view <run-id> --log-failed

# Local-only convenience: verify the plan's no-download rule is respected
git remote -v && gh repo view --json name,visibility,url
```

### 14.8 Dependabot

```yaml
version: 2
updates:
  - package-ecosystem: gradle      # picks up libs.versions.toml
    directory: "/"
    schedule: { interval: weekly }
    open-pull-requests-limit: 5
    labels: [dependencies, android]
  - package-ecosystem: github-actions
    directory: "/"
    schedule: { interval: weekly }
    labels: [dependencies, ci]
  - package-ecosystem: npm         # firebase rules tests
    directory: "/"
    schedule: { interval: monthly }
```

---

## 15. Implementation Phases

Each phase ends with a **green CI run**; nothing lands broken.

### Phase 0 — Foundation & CI skeleton (½ day)
- [ ] Create the Gradle project structure, version catalogue, wrapper, `gradle.properties`.
- [ ] `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` with SDK 36 / min 29.
- [ ] Minimal `MainActivity` + `KharchApp` with Hilt and Compose.
- [ ] `.github/workflows/build.yml` green; Dependabot on.
- [ ] Badges in `README.md`.
- **Exit:** `gh run view` shows green `build.yml`.

### Phase 1 — Data layer (1.5 days)
- [ ] `ExpenseEntity` (with `ExpenseKind`, `linkedExpenseId`), `CategoryEntity`, `WorkspaceEntity`, DAOs, `AppDatabase`.
- [ ] System category seed (12 categories) via `RoomDatabase.Callback`.
- [ ] `MagnitudePaise` / `Money` value classes, `InrFormatter` (Indian grouping).
- [ ] `SettingsStore` + `WorkspaceStore` (Proto DataStore).
- [ ] **Tests:** money fuzz test, **refund sign tests**, DAO tests (Robolectric), seeding test.
- **Exit:** `testDebugUnitTest` green.

### Phase 2 — Google sign-in & shared ledger (1.5 days)
- [ ] Firebase init (Spark), Credential Manager sign-in.
- [ ] `WorkspaceManager` (create / join / link / remove member / erase).
- [ ] Onboarding UI + the join-vs-start choice sheet (§6.2).
- [ ] Accounts screen with the ledger header card.
- [ ] **Tests:** two accounts converge on one dataset; removing a member preserves data; last-member
      removal blocked; separate-workspace path leaves ledger #1 intact.
- **Exit:** CI green; sign-in verified via a debug-only test credential.

### Phase 3 — Sync engine (2 days)
- [ ] `FirestoreRemoteDataSource` + mappers to the `workspaces/{wid}/…` layout.
- [ ] `SyncCoordinator` (mutex, debounced outbox, batched push, paged watermark pull).
- [ ] `ConflictResolver` (pure, exhaustively tested, incl. the uid tiebreaker).
- [ ] **`QuotaGovernor`** (D2 safety: 60 % ceiling, `RESOURCE_EXHAUSTED` lock to reset time).
- [ ] `SyncWorker` (expedited + periodic + connectivity + app-start); tombstone sweep.
- [ ] `SyncStatus` observable + status pill UI incl. the quota message.
- [ ] Firestore emulator tests: round-trip, shared-ledger convergence, membership rules.
- **Exit:** sync + rules tests green against the emulator.

### Phase 4 — Glass design system (2 days) ← *the differentiator, done before feature UI*
- [ ] `FrostedBackdrop` + measurement test for build cost.
- [ ] `GlassTokens` (**Exaggerated** baseline, D10), `GlassSurface`, `GlassCard`, `GlassButton`, `GlassTextField`.
- [ ] **Two complete token sets**, dark and light, with the stroke-colour flip (§8.5, D8).
- [ ] 3 glass intensities + high-contrast mode + auto-detect `high_text_contrast`.
- [ ] Type scale with tabular figures; shape scale.
- [ ] Motion springs + haptics + reduced-motion handling.
- [ ] **Tests:** `GlassContrastTest` (7:1 / 4.5:1 across every tier × both schemes × HC).
- **Exit:** **contrast test green in CI** — the gate for this phase.

### Phase 5 — Expense entry & list (2 days)
- [ ] `AddEditSheet` with custom INR keypad, **kind toggle (D5)**, category chips, date picker.
- [ ] Refund linking ("refund for…" row) + "Mark as refunded" long-press action.
- [ ] `ExpensesScreen`: grouped list, sticky header, search, filter sheet, refund row styling.
- [ ] Swipe-to-delete + undo + haptics; shared-element transitions.
- [ ] **Tests:** add → list, add refund → negative totals, edit, delete + undo, grouping,
      200 % font scale.
- **Exit:** instrumented suite green on API 34/35/36.

### Phase 6 — Dashboard (1.5 days)
- [ ] Aggregation domain code + `YearDashboard` model (signed nets, shared chart scale).
- [ ] `MonthlyBarChart` (Canvas, staggered springs, **downward bars for net-negative months**).
- [ ] `CategoryDonut` + legend (hollow slice treatment for net-negative categories).
- [ ] Year selector (swipe + sheet), month detail sheet, YoY delta, stat tiles.
- [ ] **Designed empty states (D9)** with no layout shift vs. the populated state.
- [ ] **Tests:** aggregation edge cases incl. negatives, bar tap drill-down, year swipe, empty-state
      bounds equality.
- **Exit:** screenshot smoke test captures empty states in both schemes.

### Phase 7 — Widget (1.5 days)
- [ ] `KharchGlanceWidget` + receiver + responsive layouts (2×2, 4×4).
- [ ] `WidgetState` from Room; reactive updates; **empty widget state (D9)**.
- [ ] `QuickAddActivity` (translucent glass keypad + kind toggle) + manifest wiring.
- [ ] **Tests:** widget net total correctness incl. negatives, update-on-write, quick-add round-trip,
      empty-state rendering.
- **Exit:** instrumented widget test green.

### Phase 8 — Categories, polish & hardening (2 days)
- [ ] Category manager screen (D4): create / rename / drag-reorder / hide / delete-when-unused.
- [ ] "Manage categories" entry from the Add sheet; one batched write per reorder (§6.8).
- [ ] Settings complete (export/import, erase ledger, appearance, privacy, about).
- [ ] Empty states, error states, offline + quota banners.
- [ ] Accessibility sweep: TalkBack script, touch targets, content descriptions, both schemes.
- [ ] **Tests:** category CRUD suite, reorder-batching test, empty-state contrast.
- **Exit:** all CI jobs green on all matrix levels; size gate satisfied.

### Phase 9 — Data portability & privacy (1.5 days) ← P2, P5, P6
- [ ] JSON export/import with preview + the three conflict strategies (§18.4).
- [ ] CSV export with UTF-8 BOM and Indian-locale-safe column headers.
- [ ] `allowBackup="false"` + both `data-extraction-rules.xml` variants excluding data.
- [ ] "Report an issue" deep-link with pre-filled version/API-level/device (P1 compensation).
- [ ] `PRIVACY.md` written; in-app Privacy screen rendering the same content.
- [ ] **Tests:** export → wipe → import round-trip is byte-identical; import conflict paths.
- **Exit:** round-trip test green; PRIVACY.md accurate against the shipped behaviour.

### Phase 10 — Production hardening (2 days) ← P7, P8, P9
- [ ] `baselineprofile` module + `StartupBudgetTest` / `FrameTimingBudgetTest`.
- [ ] `perf.yml` workflow green; budgets asserted in-test, not just reported.
- [ ] Baseline profile committed and diff-checked.
- [ ] `gradle/verification-metadata.xml` generated and committed; `strict` mode enforced.
- [ ] All workflow `uses:` SHA-pinned; `GITHUB_TOKEN` read-only outside release.
- [ ] `scripts/check-no-destructive-migration.sh` wired into `build.yml`.
- [ ] Detekt configured with the banned-API rules (§18.3).
- [ ] `MigrationTestHelper` harness + per-build migration smoke test.
- [ ] Branch protection + release environment with required reviewers.
- **Exit:** dependency verification, perf budgets, and migration guard all green in CI.

### Phase 11 — Updates & release (1 day) ← P10, P11
- [ ] Firebase Hosting `version.json` manifest deployed by `release.yml`.
- [ ] In-app "Check for updates": manifest fetch, version comparison, notes, cert fingerprint.
- [ ] `mandatory: true` blocking path tested; silent no-op when offline.
- [ ] Keystore secret setup; release APK signed, R8-minified, verified installable.
- [ ] **Manual device matrix pass** (§18.9): Android 15 + 14, light + dark, 100 % + 200 % font,
      fresh install + upgrade-from-previous-version, airplane-mode sync, widget on a real launcher.
- [ ] Walk the §18.10 production checklist end to end.
- [ ] Tag `v1.0.0` → signed APK + GitHub Release + README.
- **Exit:** checklist complete, updater verified end-to-end, manual matrix passed.

**Total: ~19–20 working days.** Phases 0–7 deliver the app; 8 polishes it; **9–11 are what make it
production-grade** — portability and privacy (9), CI-enforced quality gates (10), and the release
path (11). See §18.11 for the effort breakdown of the hardening layer.

---

## 16. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Glass hurts legibility on some wallpapers | **High** (explicit user requirement) | Contrast-tested tokens + scrims + high-contrast mode; worst-case composited colours asserted in CI across **both** schemes |
| Blur causes jank / battery drain | High | Pre-baked downsampled backdrop (§8.1); `Modifier.blur` forbidden by detekt rule; frame-timing test |
| **Spark quota exhausted by a runaway loop** (D2) | **High** — the API hard-stops for the rest of the day | `QuotaGovernor`: 60 % ceiling, debounced + batched writes, watermark reads, paged pulls, and a hard lock on `RESOURCE_EXHAUSTED` until midnight PT. Unit-tested by asserting zero further requests after exhaustion |
| Accidentally enabling a Blaze-only service | **High** (breaks the ₹0 guarantee) | Cloud Storage / Functions / TTL / PITR explicitly out of scope (§1.2, §3.2); documented in the setup steps; a README warning |
| Compose BOM / Kotlin / AGP version skew | High | Single pinned version catalogue + Dependabot PRs + CI green gate |
| `SharedTransitionLayout` API churn | Medium | Isolated behind `MotionFeature`; graceful fade fallback |
| Glance + `RemoteViews` limitations | Medium | Widget scoped to actions that genuinely work; everything else deep-links into the app |
| **Net-negative months break the charts** (D5) | Medium | Signed aggregation with a single shared scale across the year (§9); dedicated aggregation and rendering tests |
| **Shared ledger ⇒ concurrent edits are the common case** (D1) | Medium | Deterministic uid tiebreaker on timestamp ties (§6.4.4) so devices always converge; convergence test |
| Light mode ships as an inverted afterthought (D8) | Medium | Two independent token sets, stroke colour flips dark-on-light, both covered by the contrast test and the screenshot job |
| CI flakiness on emulator | Medium | `fail-fast: false` matrix, retries via `test-options { retry { maxRetries = 2 } }`, `disable-animations: false` |
| App is too big too early | Medium | Deliberate one-module-at-first; feature modules only if CI exceeds 5 min |
| Google Sign-In in CI needs a real credential | Medium | Instrumented tests use a fake credential provider; a debug-only CI smoke job covers the real path |
| Keystore compromise | Critical | Stored only as a GitHub secret; never committed; `debug` builds are the default test target |
| Package name later needs to change (D11) | Medium | Confirmed **before** the Firebase project is created; treated as permanent |
| **A migration wipes a user's ledger** (P9) | **Critical** | `fallbackToDestructiveMigration` banned by a CI grep rule; every migration hand-written and tested with `MigrationTestHelper`; per-build migration smoke test |
| **A bad release reaches 100 % of users at once** (P1 + D7) | **High** — no staged rollout *and* no crash signal | Per-build CI gates (contrast, perf budgets, migration, detekt) must all be green to produce a release; updater can mark a build `mandatory` for critical fixes (§18.8) |
| **Bugs are invisible without telemetry** (P1) | High — accepted | GitHub issue deep-link with pre-filled device/version is the only channel (§18.1); documented as a deliberate trade-off with a revisit trigger |
| Sideloaded users drift on stale builds (P10) | Medium | In-app updater against a Firebase Hosting manifest (private repo + GitHub API both unusable — see §18.8) |
| OEM battery management kills `WorkManager` sync (P11) | Medium | Never assume `WorkManager` fires promptly; always-visible explicit "Sync now"; tested in the manual matrix |
| Dependency verification blocks legitimate Dependabot bumps | Low | `strict` mode with a documented refresh procedure; Dependabot PRs update `verification-metadata.xml` in the same PR |
| Firestore offline persistence starts costing money | Medium | Watermark pull only, **no live listeners** — documented as a rule so nobody "improves" sync by adding one |

---

## 17. Interview Record

All open questions were resolved in the guided interview. They are now encoded as the
**Decisions D1–D11** table in §0 and are referenced from every affected section.

| Original question | Answer | Where it lands |
|---|---|---|
| Multi-account semantics | **B — one shared dataset** | D1 · §5.3 · §6.2 |
| Backend, given "must be completely free" | **Firebase Spark** — verified ₹0, no card | D2 · §3.2 · §6.8 |
| `google-services.json` handling | **Commit to repo** | D3 · §12 · §14.1 |
| Categories | **B — 12 seeded + user CRUD in v1** | D4 · §5.2 · Phase 8 |
| Refunds | **B — needed in v1** | D5 · §5.1 · §9 |
| Budgets | **B — v1.1 stretch** | D6 · §1.2 |
| Release channel | **A — GitHub Releases** | D7 · §14.3 |
| Design direction | **C — light and dark both first-class** | D8 · §8.5 |
| Demo/sample data in CI | **Stay empty** | D9 · §7.2 · §13.2 |
| Default glass intensity | **Exaggerated** | D10 · §8.3 |
| App name / package | **Kharch** / `com.srinidhi.kharch` | D11 · §11 · §12 |

### Remaining micro-decisions (safe defaults, no answer needed — override any time)

| # | Question | Default chosen |
|---|---|---|
| R1 | Confirmation when removing a Google account from the ledger | Yes, a glass dialog naming what is preserved (no data is owned by a member) |
| R2 | Cap on linked accounts per ledger | 5, enforced in Firestore rules (§6.6) |
| R3 | Member limit already reached | Disable the "Link another account" CTA with an explanatory line, rather than failing at submit |
| R4 | First-run default year on the Dashboard | The current year; older years reachable by swipe |
| R5 | Default decimal places | Exact paise (`₹640.00`); whole-rupee amounts shown as `₹640` |
| R6 | Refund linking UX | Optional link, auto-suggested from same-category expenses within 30 days |
| R7 | Haptic feedback | On, with a settings toggle (respects the system haptics setting) |
| R8 | Analytics / crash reporting | **Off, and not negotiable** — P1. Nothing is sent anywhere, not even user-initiated diagnostics |
| R9 | Data export | JSON **and** CSV via `ACTION_CREATE_DOCUMENT` (shares out of the app; no storage permission, no network) — P2 |

### Production-hardening interview (second round)

| Question | Answer | Where it lands |
|---|---|---|
| Crash reporting / telemetry | **None — strictly local** | P1 · §18.1 |
| Data portability | **CSV + JSON export *and* import** | P2 · §18.4 |
| Certificate pinning | **No — Firebase handles TLS** | P3 · §18.3 |
| Local DB encryption | **No — app-private is enough** | P4 · §18.3 |
| How bugs reach you (given no telemetry) | **No — fully closed** | P1 · §18.1 (issue deep-link is the only channel) |
| Database migrations | **Migration tests + no destructive migration** | P9 · §18.2 |
| Performance enforcement | **CI-enforced budgets** | P7 · §14.5 · §18.6 |
| CI supply chain | **Full hardening** | P8 · §14.7 · §18.7 |
| Android Auto Backup | **Opt out + user-driven export** | P5 · §18.4 |
| Legal artefacts | **PRIVACY.md in the repo** | P6 · §18.5 |
| Updates for sideloaded users | **In-app update check** | P10 · §18.8 |
| Pre-release device coverage | **Manual device testing only** | P11 · §18.9 |
---

## 18. Production-Grade Addendum

What separates this from a working demo. Each item below is scoped, costed, and honest about what
it does and does not buy you.

### 18.1 P1 — The telemetry decision, and exactly what it costs

**Chosen: nothing leaves the device. Ever. No crash reporting, no diagnostics, no analytics.**

This is respected completely in the implementation:

- No `firebase-crashlytics`, no `firebase-performance`, no `firebase-analytics` dependency.
- No `INTERNET` permission beyond what Firestore sync requires — which it does, so the permission
  exists, but the app makes exactly one class of network call: Firestore sync to the user's own
  Firebase project (§18.3).
- No network calls at all outside sync and the update check (§18.8), and both are user-initiated
  or user-scoped.
- No `BuildConfig`, device ID, advertising ID, or install-ID collection. No `SharedDeviceId`.

**What this costs you, stated plainly:**

| Consequence | Reality |
|---|---|
| A crash reaches you **only** if a user tells you | There is no crash dashboard. No automatic signal. |
| You cannot attribute a regression to a version | You will not know that v1.2.0 crashes 8 % of the time while v1.1.0 crashes 0.1 %. |
| You cannot measure startup time or jank in the field | CI budgets (P7) tell you about the emulator, never about a real mid-range phone. |
| No release safety net | D7 means no staged rollout, so a bad build reaches 100 % of users instantly. |
| Support is manual | Every report is a GitHub issue from someone willing to write one. |

**Mitigation that respects the stance (cheap, no telemetry):** the GitHub repo is the support
channel. A "Report an issue" item in Settings deep-links to the issues page with the version
number, API level, and device model **pre-filled in the issue body text** — the user pastes and
sends it themselves. That gets you version-level bug reports with zero data leaving the device
until the user decides to share. It is the one thing I would insist on, and it costs ~30 lines.

**Revisit trigger:** if the app ever has more than a handful of real users, this decision should
be revisited before the second release, not after the third bad one.

### 18.2 P9 — Database migrations (the biggest real data-loss risk)

Personal finance apps get deleted and reinstalled, or updated across versions, and a botched
migration silently wipes the database. This is the failure mode users do not forgive.

**Rules:**

1. **Every schema version exports its schema JSON.** `room.schemaLocation` is configured in the
   build script; `app/schemas/` is committed. Without exported schemas, `MigrationTestHelper`
   cannot test anything.
2. **No `fallbackToDestructiveMigration()` anywhere.** A grep rule in CI (`scripts/check-no-destructive-migration.sh`)
   fails the build if the string appears. Migration failure must *crash loudly in testing* and
   *never silently delete a user's ledger*.
3. **Every migration is written by hand, never blind auto-migration.** Auto-migration cannot
   transform data, and this schema will need data transformation (e.g. adding `kind = EXPENSE`
   to existing rows when D5 lands after v1.0).
4. **Every migration has a `MigrationTestHelper` test** that:
   - builds the *previous* version's database from the committed schema,
   - inserts realistic rows (including tombstones, refunds, and a linked-refund pair),
   - runs the migration,
   - asserts the new schema, **and** that every row survived with correct values.
5. **A "migration smoke test" runs on every CI build**, not only when migrations change. It opens
   a v1 database, migrates to current, and re-runs the DAO suite against it. This is the test that
   stops "works on a fresh install" from shipping over "works on upgrade".
6. **`schemaVersion` is also stored in the Firestore workspace doc** (§6.5), so a cloud-side
   mismatch is detectable and reportable rather than silently corrupting.
7. **Release checklist item:** every release notes must state the `versionCode` → schema version
   mapping. A future maintainer should be able to reconstruct the upgrade path from git history.

### 18.3 Security posture (P3, P4) — what we deliberately do *not* do

| Control | Decision | Reasoning |
|---|---|---|
| TLS to Firestore | **Inherent** | Firebase enforces TLS 1.2+; the SDK will not connect in cleartext. |
| Certificate pinning | **No (P3)** | Pinning Firebase certs risks a permanent sync outage when Google rotates. The app only ever talks to Google, so there is no third-party endpoint worth MITM-ing. The correct fix for a hostile network is a VPN, not pinning. |
| Local DB encryption | **No (P4)** | Room in app-private storage is unreadable by other apps on a non-rooted device. SQLCipher adds a key-management surface where a bug means *permanent* data loss — a worse outcome than the threat it mitigates. Revisit only if root-detection or full-disk encryption becomes a stated requirement. |
| `allowBackup` | **`false` (P5)** | Stops Android Auto Backup from uploading the ledger to Google's servers. |
| `usesCleartextTraffic` | **`false`** | Belt and braces alongside the SDK default. |
| Debug logging of user data | **Banned** | A detekt rule forbids `Log.*` of any expense/amount/category value in `main` source. Only tag + state machine in logs. |
| `android:debuggable` in release | **`false`** | Standard, but explicitly asserted in CI. |
| Root/jailbreak detection | **No** | Breaks legitimate users, trivially bypassed, adds false positives. Not proportionate for a personal expense tracker. |
| Firebase security rules | **Yes, strictly** | The only server-side trust boundary. Tested in CI (§14.4). This is where security effort belongs. |

The honest summary: this app's real security surface is **the shared ledger's authorisation model**,
not the transport. That is where the rules, the membership cap, and the `amountPaise >= 0` server
validation do their work.

### 18.4 P2, P5 — Data portability and backup

**Export**
- **JSON** (`kharch-export-<date>.json`) — lossless, full fidelity, the backup format. Written via
  `ACTION_CREATE_DOCUMENT`, so the user picks the destination. No network, no storage access needed.
- **CSV** (`kharch-expenses-<date>.csv`) — one row per expense, for Excel/Sheets. Columns:
  `id, date, name, category, kind, amount_inr, note, linked_expense_id, created_at, updated_at`.
  Explicit `UTF-8 BOM` so Indian names and the ₹ symbol render correctly in Excel — a small detail
  that matters a lot in practice.
- Both cover a **date range** or the whole ledger, chosen in the export sheet.

**Import**
- Reads a Kharch JSON export, validates it against a versioned schema, shows a **preview**:
  `N new · M updated · K skipped · L conflicts`, then requires confirmation.
- **Conflict strategy on import**, offered explicitly:
  - *Skip existing IDs* (safest — pure restore into an empty install),
  - *Overwrite* (import is authoritative),
  - *Keep newer by `updatedAt`* (LWW — consistent with sync, §6.4).
- Import is **deferred into the normal outbox**, so an imported batch syncs through exactly the
  same batched, quota-aware path as manual entry. No special-casing, no quota blowout.
- Round-trip tested: export → wipe → import → **byte-identical** ledger state.

**Backup**
- `android:allowBackup="false"`, plus an explicit `data-extraction-rules.xml` that excludes the
  database, DataStore, and cache even on devices that ignore `allowBackup` (Android 12+ split the
  rules into cloud-backup and device-transfer variants; **both** are excluded).
- The in-app Export screen carries a one-line explanation of *why* auto-backup is off, so it reads
  as a deliberate choice rather than a missing feature.

### 18.5 P6 — Privacy and transparency

`PRIVACY.md` at the repo root (GitHub renders it; no hosting cost). Contents:

1. What is stored (expenses, categories, ledger membership, Google account email/display name).
2. Where it is stored (your own Firebase project; nothing third-party).
3. **A literal statement of what is never collected** — no analytics, no crash data, no device
   identifiers, no ads, no third-party SDKs.
4. That the only network calls are Firestore sync and the optional update check.
5. How to delete everything, and how to export it.
6. Contact route (a GitHub issue).

Also: an in-app **"Privacy" row in About** that renders the same content locally, and the
third-party licence screen the plan already requires (Google Play's attribution obligation, worth
honouring regardless).

### 18.6 P7 — Performance as a CI gate

Startup and smoothness are asserted, not eyeballed:

| Budget | Target | Measured by | Fails when |
|---|---|---|---|
| Cold start → first frame | < 1.2 s | Macrobenchmark `StartupCompilationAndActivity` | > 1.4 s on 2 consecutive runs |
| Warm start | < 400 ms | Macrobenchmark | > 550 ms |
| Dashboard scroll (frame timing) | < 32 ms/frame | Macrobenchmark `FrameTimingMetric` | > 50 ms jank in the 90th percentile |
| Add-sheet open (animation) | < 32 ms/frame | Macrobenchmark | > 50 ms jank |
| Backdrop build cost | ≤ 4 ms, ≤ 64 KB | Instrumented assertion in `FrostedBackdrop` | exceeded |
| APK size (debug) | < 13 MB | `build.yml` size gate | exceeded |
| APK size (release) | < 8 MB | `build.yml` size gate | exceeded |

Plus a **Baseline Profile** covering: app start → Dashboard, Dashboard → Add sheet, Expenses list
scroll, widget render. Published in-repo as a `baseline-prof.txt`, regenerated in CI and
diff-checked so it cannot silently rot.

This directly serves the "smooth animations" requirement: a dropped frame during the glass
transitions becomes a red build rather than something a user notices.

### 18.7 P8 — Supply-chain hardening

| Control | Implementation |
|---|---|
| **Dependency verification** | `gradle/verification-metadata.xml` committed; `org.gradle.dependency.verification=strict`. Every transitive artifact's SHA-256 and signature is checked. A tampered or typosquatted artifact fails the build. |
| **Action pinning** | Every `uses:` reference pinned to a full 40-char commit SHA, with Dependabot keeping them current. No floating `@v4` tags. |
| **Minimal token permissions** | `permissions: contents: read` at workflow level; `contents: write` only on the release job. `pull_request_target` never used. |
| **Branch protection** | `main` requires: CI green, no force-push, no direct push, signed commits optional. |
| **Secret hygiene** | Keystore only as a GitHub secret; never echoed; `::add-mask::` on any decoded value. Release job runs in a separate environment with required reviewers. |
| **Provenance** | Build provenance generated for release artifacts, so a published APK can be traced to the commit that produced it. |
| **No secrets on PRs** | Forks cannot read secrets, so the rules-tests job must not depend on any — this is why `firestore.rules` and `google-services.json` are both committed (D3). |
| **Dependency scanning** | Dependabot + GitHub dependency graph + security advisories on `main`. |

### 18.8 P10 — In-app updates, and the private-repo constraint

Sideloaded apps cannot self-update on modern Android, and Android 16 tightens sideloading further.
Without an update path, a critical fix cannot reach existing users.

**The constraint I found by testing:** this repo is **private**, and an unauthenticated request to
`api.github.com/repos/5r1n1dh1/expense_tracker/releases/latest` returns **404**. So the obvious
implementation — app → GitHub API → release JSON — **does not work** and would silently never find
an update. Even if the repo were public, the unauthenticated limit is **60 requests/hour/IP**, which
is shared across every user behind a NAT and would rate-limit aggressively.

**Chosen design:**

1. **A version manifest served from Firebase Hosting**, not the GitHub API.
   - A single `version.json` (`{ "versionCode": 12, "versionName": "1.2.0", "url": "<asset URL>",
     "notes": "...", "mandatory": false }`) deployed by CI on release.
   - Hosted on the **Spark** plan: 10 GB storage and 360 MB/day transfer, free, no card. The file
     is a few hundred bytes, so 360 MB/day is ~millions of checks.
   - This sidesteps the private-repo 404 **and** the rate limit, and keeps the ₹0 guarantee (D2).
2. **The app checks on app start and from Settings → "Check for updates".** Never on a timer, never
   in the background, never on any other screen — a network call the user didn't ask for would
   violate P1's spirit.
3. **Respect the network state**: silent no-op when offline, and never blocks startup.
4. **`mandatory: true`** shows a blocking screen for critical fixes (e.g. a sync-breaking bug).
   Used sparingly; a forced-update screen that triggers on a minor release is how you lose users.
5. **Install is handed to the system installer** (`ACTION_VIEW` / `PackageInstaller`) — the app
   cannot self-install. Android 16's "advanced flow" may still prompt the user; that is expected and
   the copy explains it.
6. **The APK's signing certificate fingerprint is shown in the update sheet**, so the user can verify
   what they're about to install matches the app they already have. This matters more for
   sideloaded apps than for Play apps, and it costs three lines.

### 18.9 P11 — Release verification (manual device testing)

Chosen: manual testing on the maintainer's own device. **Honest consequences:**

- CI's emulator matrix covers **logic, UI, and performance**, never OEM skins.
- Bugs specific to Samsung One UI, Xiaomi/HyperOS aggressive battery management (which can kill
  WorkManager sync), or real-world network handover will **not** be caught before release.
- Mitigation: the app must degrade gracefully rather than depend on OEM behaviour — **never assume
  `WorkManager` fires promptly**; treat it as a best-effort background path with an explicit
  foreground "Sync now" always available (§6.7). This is a *design* mitigation that makes manual
  testing sufficient, which is better than adding Test Lab.
- Recommended manual matrix, run before each release (~20 min): Android 15 device + Android 14
  device, light + dark, 100 % + 200 % font, fresh install + upgrade-from-previous-version,
  airplane-mode add → reconnect sync, and the widget on a real launcher.

### 18.10 Production checklist (the "definition of done" for v1.0)

- [ ] Zero `fallbackToDestructiveMigration`; every migration tested (P9)
- [ ] Migration smoke test in CI runs on every build (P9)
- [ ] Dependency verification enabled and `strict` (P8)
- [ ] All GitHub Actions SHA-pinned; `GITHUB_TOKEN` read-only outside release (P8)
- [ ] `allowBackup="false"` + both `dataExtractionRules` variants exclude data (P5)
- [ ] No crash/analytics/performance dependency anywhere (P1)
- [ ] Detekt passes: no `Modifier.blur` on scrolling content, no `Log.*` of user data (§18.3)
- [ ] `GlassContrastTest` green across all tiers × both schemes × high-contrast (C5)
- [ ] Macrobenchmark budgets enforced in CI (P7)
- [ ] Baseline profile committed and current (P7)
- [ ] Export → wipe → import produces a byte-identical ledger (P2)
- [ ] Update manifest deployed and the in-app updater verified end-to-end (P10)
- [ ] `PRIVACY.md` written and accurate (P6)
- [ ] Release APK R8-minified, signed, and verified installable
- [ ] Manual device matrix passed (§18.9)
- [ ] CI green on API 34 / 35 / 36; rules tests green

### 18.11 Effort delta

| Area | Added effort |
|---|---|
| Migration testing infra (§18.2) | 0.5 day |
| Performance budgets + baseline profile (§18.6) | 1.5 days |
| Supply-chain hardening (§18.7) | 0.5 day |
| Export/import incl. conflict UX (§18.4) | 1.5 days |
| Updater + Firebase Hosting manifest (§18.8) | 0.75 day |
| Privacy doc, release checklist, final pass (§18.5, §18.10) | 0.5 day |
| **Total added** | **≈ 5.25 days** |

Revised total: **~19–20 working days** for v1.0. The core app is unchanged; this is the layer that
makes it something you would be comfortable having your own family use for a decade.

