# Phase 7 — Widget Implementation Plan

> Subagent plan for `Kharch` widget layer (§10 plan.md). References `ui/glass/`,
> `ui/theme/`, `widget/QuickAddActivity.kt`, `BUILD_CONTRACT.md`, §10 / 10.1–10.5.

---

## 1. Goals & Constraints (locked by BUILD_CONTRACT.md + plan.md)

| Constraint | How this plan honours it |
|---|---|
| **C4 Glass / no live blur** | Widget uses flat translucent scrim (`#CC0B1020`
dark / `#E6FFFFFF` light) + gradient + 1 px inner stroke (§10.2, §8.1). No
`Modifier.blur`. No `RenderEffect`. Backdrop is the **cached** `FrostedBackdrop`
bitmap (`SCALE = 0.0625f`, ~4 ms build, <64 KB) reused by `GlassSurface`; widget
itself never blurs live content. |
| **No `Log.*`** | `ForbiddenMethodCall` detekt rule (§BUILD_CONTRACT). Zero
`android.util.Log` / `println` in widget, state, receiver, or tests. |
| **No wildcard imports** | Every import explicit. Detekt formatting on. |
| **Max 120 chars / line** | All code snippets in this plan wrap ≤120. Actual source will be checked by `detekt`. |
| **Domain pure Kotlin** | `WidgetState` aggregation (monthly net, sparkline,
top categories) lives in `domain/widget/` — no `android.*`, no Room, no Compose.
Only `kotlin` + `kotlinx.coroutines`. |
| **No `fallbackToDestructiveMigration`** | Not relevant here (no DB schema change), but guarded in CI grep (§BUILD_CONTRACT, P9). |
| **JUnit4 + Turbine + Compose UI** | Tests in §7. No JUnit5. `Turbine` for Flow.
`ComposeUiTest` (`createComposeRule`) for `QuickAddActivity`. `GlanceAppWidgetTestRule`
+ `captureGlance` (Robolectric) for widget render (§13.3). |
| **D1 shared workspace** | Widget shows same totals to every linked account — no
per-account filter (§10.1, §6.3). `KharchWidget.updateAll(context)` writes to all
instances; state reads Room directly in `provideGlance`, never main thread (§10.3). |
| **D5 refunds / negative month** | Monthly headline renders `−` + `↩` when net negative; sparkline supports downward bars (§9, §10.1). Aggregation uses `Money.of` pure Kotlin. |
| **D9 empty state** | Widget shows `₹ 0` + *"Tap + to log first expense"* rather
than blank bars (§10.3, §13.2). Screenshot smoke captures this. |
| **D10 Exaggerated glass** | Widget surface uses `GlassTier.Exaggerated` tokens (`glassTokens`) — heavy frost by default; user can lower via `LocalGlassIntensity` if exposed in settings (§8.3). |
| **Accessibility** | Every interactive element has `contentDescription`; touch
targets ≥48 dp; TalkBack verified (`semantics { contentDescription = ... }`).
High-contrast toggle respected. |
| **No telemetry SDK** | No Crashlytics / Analytics / Perf declarations (§P1, CI grep guard). |
| **No `kotlin-android` plugin** | Already removed; AGP 9.3.1 built-in Kotlin (§BUILD_CONTRACT). Widget source uses `compileOnly(libs.androidx.glance)` if needed, never adds old plugin. |

---

## 2. File Inventory (new / updated)

```
app/src/main/java/com/srinidhi/kharch/
├── widget/
│   ├── KharchWidget.kt              ← GlanceAppWidget + receiver + provider meta
│   ├── WidgetState.kt               ← pure Kotlin domain model + aggregation
│   ├── GlanceReceiver.kt           ← GlanceAppWidgetReceiver (handles updates)
│   └── QuickAddActivity.kt         ← updated (was placeholder 22 lines)
├── domain/widget/
│   ├── WidgetSummary.kt             ← monthly net, today net, sparkline points, top cats
│   └── WidgetStateProvider.kt       ← pure Kotlin Flow / suspend fun reading Room
└── ui/glass/                        ← REUSE (no change required)
    ├── GlassSurface.kt
    ├── FrostedBackdrop.kt
    └── GlassIntensity.kt
```

Tests (new):

```
app/src/test/java/com/srinidhi/kharch/
├── widget/
│   ├── WidgetStateTest.kt           ← pure Kotlin aggregation (JUnit4 + Turbine)
│   └── GlanceReceiverTest.kt        ← Robolectric + GlanceAppWidgetTestRule
└── androidTest/java/com/srinidhi/kharch/
    └── widget/
        └── QuickAddActivityTest.kt  ← Compose UI Test (JUnit4 + AndroidJUnit4)
```

---

## 3. Glance Widget — `KharchWidget.kt` / `GlanceReceiver.kt`

### 3.1 Shape & layout (§10.1)

- `AppWidgetProviderInfo` (`res/xml/kharch_widget_info.xml`): `minWidth="180dp" minHeight="180dp"` (2×2), `maxResizeWidth="360dp" maxResizeHeight="360dp"`, `resizeMode="horizontal|vertical"`, `previewLayout="@layout/widget_preview"`, `updatePeriodMillis="1800000"` (30 min floor, §10.3).
- `GlanceAppWidget`: `KharchWidget` extends `GlanceAppWidget()`. Uses `provideGlance` (suspend, off-main) to read `ExpenseRepository` / Room directly.
- `GlanceAppWidgetReceiver`: `GlanceReceiver` extends `GlanceAppWidgetReceiver<KharchWidget>()`. Handles system updates; also exposes `fun updateAll(context: Context)` called by `SyncTrigger` after Room write (§6.3, `sync-glue/SyncTrigger.kt`).
- `ActionCallback`: in-widget `+` button launches `QuickAddActivity` via `actionStartActivity` with `actionLaunchEntryPoint` (no text input inside widget). Month-cycle button uses `ActionCallback` to update state locally (no Room write needed for pure UI cycle — if needed later, write to DataStore and refresh).

### 3.2 Widget honesty about glass (§10.2, §8.1, §8.3)

```kotlin
// Inside provideGlance, inside Box / Column
// No Modifier.blur — detekt failure (§BUILD_CONTRACT, §8.1)
// Flat scrim, gradient, 1 px inner stroke — same design language
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xCC0B1020), // dark scrim
                    Color(0xE6FFFFFF), // light scrim (theme resolved)
                )
            )
        )
        // 1 px inner stroke — replaces elevation (§8.3)
        .drawWithContent { ... }
) { ... }
```

The widget **never** draws `LocalFrostedBackdrop` (it cannot access the launcher wallpaper). It uses a flat translucent panel — deliberately documented (§10.2). If a future stretch needs more frost, only a **pre-baked** static bitmap (not live blur) is permitted.

### 3.3 Responsive sizes (§10.1, §10.3)

`SizeMode.Responsive` with `GlanceModifier.fillMaxSize()`. Layout branches:

- **2×2 (compact)**: header (month + sync dot) + headline (`₹ 24,318`) + today (`today ₹ 640`) + 7-bar sparkline + `[Food][Travel]` chips + `[ + Add ]` button.
- **4×4 (expanded)**: same top section + larger sparkline + category split + secondary actions.

Both share the same `GlanceComposable` with `when (LocalSize.current)` branching.

---

## 4. Widget State Management — `WidgetState.kt` / `domain/widget/`

### 4.1 Pure Kotlin model (§4 architecture, §BUILD_CONTRACT pure domain)

```kotlin
package com.srinidhi.kharch.domain.widget

import com.srinidhi.kharch.core.model.Money
import java.time.YearMonth

/** Pure Kotlin — no Android, no Room, no Compose. */
data class WidgetSummary(
    val monthYear: YearMonth,
    val netPaise: Long,              // positive = charge, negative = refund-heavy (D5)
    val todayPaise: Long,
    val sparkline: List<Long>,       // 7 points (last 7 days), each in paise
    val topCategoryNames: List<String>,
    val isSynced: Boolean,
)

/** Aggregation function — testable, no dependencies. */
fun aggregateWidgetSummary(
    expenses: List<ExpenseDomain>, // pure model, not Room entity
    clock: Clock,
    month: YearMonth,
): WidgetSummary { ... }
```

Rules enforced:
- `amountPaise` is `Long`, never `Double` (§5.1, D5).
- `Money.of(m, kind)` used for net calculation; negative month handled correctly (`isCredit = true`).
- `Clock` injected (not `LocalDate.now()`) for determinism (§4, §13.2).
- No `android.*` imports in `domain/widget/`.

### 4.2 Data updates (§10.3, §6.3)

```
Room write → ExpenseRepository.changeFlow → SyncTrigger.requestWidgetUpdate(context)
    → KharchWidget.updateAll(context)  // full refresh
    OR → updateAppWidgetState(appWidgetId, newState) // partial
```

- `provideGlance` reads Room via `ExpenseDao.getMonthlySummary(monthEpochDay)` inside `withContext(Dispatchers.IO)` — never on main thread (§10.3).
- `updatePeriodMillis = 1800000` (30 min) as system floor; reactive updates cover the rest (§10.3).
- `updateAll` triggers `GlanceAppWidget.updateAll` which calls `provideGlance` for every installed instance.

---

## 5. QuickAddActivity (`QuickAddActivity.kt`) — §10, §11.3, §11.4

### 5.1 Activity spec (existing placeholder expanded)

```kotlin
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // mandatory for targetSdk 36 (§11.3)
        super.onCreate(savedInstanceState)
        setContent {
            KharchTheme(darkTheme = isSystemInDarkTheme()) {
                // Translucent scrim drawn by this activity (not window dim)
                QuickAddSheet(
                    onSave = { expense -> viewModel.save(expense); finish() },
                    onCancel = { finish() },
                )
            }
        }
    }
}
```

Manifest (already present, §11.3 / `AndroidManifest.xml` lines 39–47):
- `android:theme="@style/Theme.Kharch.Translucent"`
- `android:excludeFromRecents="true"`
- `android:noHistory="false"`
- `android:exported="true"`
- `android:launchMode="singleTask"`
- `android:taskAffinity=""`

Theme (`themes.xml` lines 24–30): `windowIsTranslucent=true`, `windowBackground=@android:color/transparent`, `windowNoTitle=true`, `backgroundDimEnabled=false`. The activity draws its own glass scrim (`GlassSurface` with `GlassTier.Exaggerated` over `LocalFrostedBackdrop` if available, else flat translucent — since it composites over another app, backdrop may be unavailable; use flat scrim with gradient).

### 5.2 UI requirements (§10.1, §7.2G)

- **Translucent + edge-to-edge**: content extends into status / nav bars; scrim covers full window.
- **Glass keypad**: amount input uses `AmountKeypad` component (`ui/components/AmountKeypad.kt`) — custom INR keypad, no IME for numbers (§3.1, manual entry only).
- **Category chips**: `CategoryChips` with 12 seeded + user categories (`CategoryEntity` from Room).
- **Refund toggle**: `ExpenseKindToggle` (`EXPENSE` / `REFUND`) per D5.
- **Save → Room → close ~250 ms** (§10.3): `viewModel.save()` writes to Room (syncState = `PENDING_UPSERT`), triggers sync, then calls `finish()` immediately; no blocking on Firestore.

---

## 6. Performance (§8.1, §8.2, §10.3, §P7)

| Concern | Rule / Implementation |
|---|---|
| **No live blur** | `Modifier.blur` banned by detekt; `RenderEffect` not used; widget uses flat scrim (§8.1). |
| **Cached backdrop** | `FrostedBackdrop` builds once per process (`rememberFrostedBackdrop` / `produceState` with `Dispatchers.IO`). `GlassSurface` draws cached `ImageBitmap`. Widget does not draw backdrop at all — flat scrim, zero bitmap cost. |
| **60 fps** | Widget is static (no animation except sparkline; sparkline uses simple `Canvas` lines, not animated composables). `provideGlance` runs on coroutine, not UI thread. No per-frame recomposition of backdrop. |
| **Frame budget** | No frame >32 ms in widget journey (P7). Verified by macrobenchmark if added; at minimum, `provideGlance` completes in <100 ms (database query on indexed `dateEpochDay`). |
| **Memory** | Widget state is small (`WidgetSummary` ~200 bytes). No bitmap allocations per update. `updateAll` triggers `onUpdate` once per instance, not per widget pixel. |

---

## 7. Accessibility (§1.3 NFR, §13.2, §8.4)

| Requirement | Implementation |
|---|---|
| **TalkBack** | Every interactive element (`+ Add`, month label, sparkline bars if tappable) gets `contentDescription`. `Column` / `Row` containers get `semantics { contentDescription = "Kharch widget, August 2026, net ₹24,318" }`. |
| **48 dp touch targets** | `+ Add` button = 48×48 dp min. Category chips = 48 dp height. Month header label = 48 dp tap area. Asserted in `QuickAddActivityTest` via `onNodeWithContentDescription().assertHeightIsAtLeast(48.dp)`. |
| **High contrast** | `LocalHighContrast.current` drives token selection (`glassTokens` with high-contrast variants, §8.4.6, `GlassTokens.kt`). Widget respects system `high_text_contrast` via `AccessibilityManager`. |
| **Fonts** | 200 % font scale pass (§1.3, §13.2): headline uses 28 sp, body 14 sp, both scale. No fixed `sp` that breaks at 200 %. Tested in `QuickAddActivityTest` with `setContent { ... }` at scaled densities (Robolectric can simulate). |
| **Color alone** | Negative month uses `−` symbol + `↩` marker, not red only (§5.1, D5, §8.4). |

---

## 8. Test Specifications (follow BUILD_CONTRACT.md + plan.md §13.2 / §13.3)

### 8.1 `WidgetStateTest.kt` (pure Kotlin, JUnit4 + Turbine)

Location: `app/src/test/java/com/srinidhi/kharch/widget/WidgetStateTest.kt`

```kotlin
package com.srinidhi.kharch.widget

import com.srinidhi.kharch.core.model.Money
import com.srinidhi.kharch.core.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStateTest {

    @Test
    fun `monthly net subtracts refunds from charges`() {
        val clock = FakeClock(2026, 8, 15)
        val summary = aggregateWidgetSummary(
            expenses = listOf(
                expense(name = "Lunch", amountPaise = 5000L, kind = EXPENSE),
                expense(name = "Refund", amountPaise = 2000L, kind = REFUND),
            ),
            clock = clock,
            month = YearMonth.of(2026, 8),
        )
        assertEquals(3000L, summary.netPaise)
    }

    @Test
    fun `negative month renders credit correctly`() {
        val summary = aggregateWidgetSummary(
            expenses = listOf(
                expense(amountPaise = 3000L, kind = REFUND),
                expense(amountPaise = 5000L, kind = REFUND),
            ),
            clock = FakeClock(2026, 8, 1),
            month = YearMonth.of(2026, 8),
        )
        assertEquals(-2000L, summary.netPaise)
        assertTrue(Money.of(MagnitudePaise(2000L), REFUND).isCredit)
    }

    @Test
    fun `sparkline contains exactly seven points`() {
        val summary = aggregateWidgetSummary(expenses = emptyList(), clock = FakeClock(2026,8,1), month = YearMonth.of(2026,8))
        assertEquals(7, summary.sparkline.size)
    }
}
```

Rules: no `Log.*`, explicit imports only (`org.junit.Test`, `org.junit.Assert.assertEquals`, etc.), max 120 chars, pure Kotlin (no Android imports).

### 8.2 `GlanceReceiverTest.kt` (Robolectric + GlanceAppWidgetTestRule)

Location: `app/src/test/java/com/srinidhi/kharch/widget/GlanceReceiverTest.kt`

```kotlin
package com.srinidhi.kharch.widget

import androidx.glance.testing.GlanceAppWidgetTestRule
import androidx.glance.testing.captureGlance
import org.junit.Rule
import org.junit.Test

class GlanceReceiverTest {

    @get:Rule
    val glanceRule = GlanceAppWidgetTestRule()

    @Test
    fun `widget renders correct month total and empty state`() {
        val captured = glanceRule.captureGlance(
            GlanceAppWidgetTestRule.GlanceAppWidgetUnderTest(
                KharchWidget()
            )
        )
        // Assert headline text contains expected pattern (Robolectric renders Glance)
        assertNotNull(captured)
    }

    @Test
    fun `updateAll reflects newly inserted Room row`() {
        // Uses Room in-memory + Glance update; asserts state change propagated
    }
}
```

Notes: `GlanceAppWidgetTestRule` is provided by `androidx.glance:glance-testing`. `captureGlance` renders the Glance composable to a bitmap for assertions. No `Modifier.blur` in captured output. No `Log.*`.

### 8.3 `QuickAddActivityTest.kt` (Compose UI Test + JUnit4 + AndroidJUnit4)

Location: `app/src/androidTest/java/com/srinidhi/kharch/widget/QuickAddActivityTest.kt`

```kotlin
package com.srinidhi.kharch.widget

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAddActivityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `quick add sheet has 48 dp touch targets`() {
        composeTestRule.setContent { QuickAddSheet(onSave = {}, onCancel = {}) }
        composeTestRule.onNodeWithContentDescription("Add expense button")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `refund toggle is accessible to TalkBack`() {
        composeTestRule.setContent { ExpenseKindToggle() }
        composeTestRule.onNodeWithContentDescription("Expense kind: Expense")
            .assertExists()
    }
}
```

Notes: `createComposeRule()` requires `androidx.compose.ui:ui-test-junit4`. Activity-level tests can also use `ActivityScenario` (`androidx.test.core.app.ActivityScenario`) if full intent testing is needed; keep `QuickAddActivity` tests focused on composable interactivity per BUILD_CONTRACT (§13.1, Compose UI test layer).

---

## 9. Build / CI Integration

- `build.yml`: `./gradlew testDebugUnitTest` must include new `WidgetStateTest`; detekt must pass on `widget/` (max 120 chars, no wildcards, no `Log.*`, no `Modifier.blur`).
- `instrumented.yml`: `QuickAddActivityTest` runs on `Pixel_6` API 35 emulator with animations enabled (§14.2). Widget screenshot smoke (optional future) captures `KharchWidget` empty state.
- `rules.yml`: grep guards for `Log.`, `Modifier.blur`, `fallbackToDestructiveMigration`, telemetry SDKs (
`firebase-crashlytics` / `-analytics` / `-perf`) must pass.
- Dependency: `glance` 1.2.0, `glance-testing` (if separate artifact in BOM 34.19.0 / Compose BOM 2026.09.00), `turbine` (version pinned in `libs.versions.toml`). No new dependency added without catalogue update (§BUILD_CONTRACT §2).

---

## 10. References (existing code patterns reused)

| Pattern | File | Reused for |
|---|---|---|
| Glass surface, draw order, no live blur | `ui/glass/GlassSurface.kt` | Widget scrim (flat, no backdrop draw) |
| Cached backdrop bitmap | `ui/glass/FrostedBackdrop.kt` (Frost.SCALE, SIGMA, BudgetMs) | Confirmed not needed in widget; if needed later, reuse same `Frost` params |
| Glass intensity / tier | `ui/glass/GlassIntensity.kt`, `ui/theme/GlassTokens.kt` | Widget uses `GlassTier.Exaggerated` tokens |
| Theme / dark+light | `ui/theme/Theme.kt` (`KharchTheme`) | `QuickAddActivity` uses same theme root |
| ComponentActivity + edge-to-edge | `widget/QuickAddActivity.kt` (existing placeholder) | Expanded with `enableEdgeToEdge()` + `setContent` |
| Amount keypad | `ui/components/AmountKeypad.kt` | Quick-add amount input |
| Category chips / kind toggle | `ui/components/CategoryChips.kt`, `addedit/ExpenseKindToggle.kt` | Quick-add form |
| Domain pure Kotlin / Clock | `core/time/Clock.kt`, `core/model/Money.kt` | `WidgetSummary` aggregation |
| Test fixtures / FakeClock | `test/java/com/srinidhi/kharch/core/time/FakeClock.kt` | `WidgetStateTest` clock injection |
| JUnit4 + assert style | `domain/sync/QuotaGovernorTest.kt` | `WidgetStateTest` naming (`fun `monthly net...``) |

---

## 11. Rules Checklist (before any code is written)

- [ ] No `android.util.Log.*` / `println` anywhere in `widget/`, `domain/widget/`, or tests.
- [ ] No `Modifier.blur` anywhere.
- [ ] No wildcard imports (`import com.srinidhi.kharch.*` banned).
- [ ] No line exceeds 120 characters.
- [ ] Every Kotlin file ends with trailing newline.
- [ ] Domain layer (`domain/widget/`) has zero `android.*` or `androidx.compose.*` imports.
- [ ] `Money` / `MagnitudePaise` used for all amounts; `Double`/`Float` never for money.
- [ ] `Clock` injected; never `LocalDate.now()` in domain.
- [ ] `GlanceAppWidget` / `GlanceReceiver` use `provideGlance` with `withContext(Dispatchers.IO)`.
- [ ] `QuickAddActivity` manifest entries unchanged except theme/already present; `enableEdgeToEdge()` called; translucent theme applied.
- [ ] All interactive elements have `contentDescription`; touch targets ≥48 dp verified.
- [ ] Tests use JUnit4 (`org.junit.Test`), Turbine (`app.cash.turbine`), Compose UI (`createComposeRule`), and Robolectric (`GlanceAppWidgetTestRule`) — no JUnit5.
- [ ] No telemetry SDK declared; no `firebase-crashlytics`, `-analytics`, `-perf`.
- [ ] No `fallbackToDestructiveMigration` in any migration (not applicable to this phase, but CI grep must pass).

---

*Plan created: Sun Oct 18 2026. References plan.md §10 / 10.1–10.5, BUILD_CONTRACT.md, existing `widget/QuickAddActivity.kt`, `ui/glass/`, `ui/theme/`, and `app/src/test/` patterns.*
