# Build Contract for Subagents

Read this fully before writing code. It encodes decisions already made and CI rules that
will **fail your work** if ignored. There is no local build — CI is the only compiler, so
every rule here was learned from a real CI failure.

## Repository

- Path: `/home/srinidhi/Documents/Google_AI_Studio/expense_tracker`
- Package root: `com.srinidhi.kharch`
- Source roots:
  - `app/src/main/java/com/srinidhi/kharch/...`
  - `app/src/test/java/com/srinidhi/kharch/...` (JVM unit tests)
  - `app/src/androidTest/java/com/srinidhi/kharch/...` (instrumented — emulator)

## Toolchain (already green — do NOT change)

| Thing | Value | Note |
|---|---|---|
| AGP | 9.3.1 | has **built-in Kotlin** |
| Kotlin | 2.2.10 | AGP bundles it; matches KSP 2.2.10-2.0.2 |
| Gradle | 9.5.0 | |
| JDK | 17 | |
| compileSdk | **37** | AndroidX requires it |
| targetSdk | 36 | Play requirement |
| minSdk | 29 | |
| Firebase BoM | **34.19.0** | 35.0.0 is Kotlin-2.4 compiled → unreadable |

### Hard toolchain rules

1. **There is no `kotlin-android` plugin.** AGP 9 has built-in Kotlin. Do not add
   `alias(libs.plugins.kotlin.android)` anywhere.
2. **Do not add dependencies** unless listed below. `gradle/libs.versions.toml` is the only
   source of dependency coordinates. If you truly need something new, add it to the
   catalogue AND tell me why — CI cannot be debugged locally.
3. **Do not use `androidx.compose.material:material-icons-extended`** — deliberately removed
   (≈20 MB). If you need an icon, create a vector drawable in `res/drawable/`.
4. **Do not use `Modifier.blur`** — detekt fails the build on it (§8.1 forbids live blur).
5. **Every Kotlin file MUST end with a trailing newline.** CI has a script that fixes this
   automatically, but write it correctly anyway.

## CI gates that will fail you

| Gate | Rule |
|---|---|
| Detekt | max 120 char lines; **no wildcard imports**; no `TODO:`/`FIXME:`; no trailing whitespace; `@Composable` naming ignored |
| Detekt `ForbiddenMethodCall` | **Banned: `android.util.Log.v/d/i/w/e/println`** — user financial data must never reach Logcat. Use nothing, or a non-logging trace channel. |
| Detekt `ForbiddenComment` | `TODO:` and `FIXME:` are banned. Do not leave them. |
| CI grep guard | `fallbackToDestructiveMigration` anywhere in `app/src/` fails the build. |
| CI grep guard | any real `firebase-crashlytics` / `-analytics` / `-perf` **declaration** fails the build (P1: no telemetry). Mentioning them in comments is fine. |
| Android lint | must pass. Watch: `UnusedResources`, missing content descriptions are not enforced here, but obvious lint issues will fail. |
| Release R8 | `assembleRelease` must succeed. Any reflection/serialization needs a keep rule in `app/proguard-rules.pro`. |

## Style (detekt formatting is on)

- 4-space indent, max line length **120**.
- Prefer expression bodies, `val` over `var`.
- No `!!` (`UnsafeCallOnNullableType`) and no unchecked casts without `@Suppress` + comment.
- KDoc on public API is welcome but not enforced.

## Architecture rules (§4 of plan.md)

```
ui/        -> may import domain + data
domain/    -> pure Kotlin. NO android.* imports. NO Firebase/Room types.
data/      -> may import domain. Must not import ui.
```

- Domain layer is **pure Kotlin + coroutines/Flow**. It gets dates via an injected `Clock`,
  never `LocalDate.now()` (untestable, and required by §13).
- Room entities live in `data/local`; domain has its own clean models.
- All money is **integer paise** as `Long`. Never `Double`/`Float` for amounts.

## Decisions that constrain the design

- **D1**: ONE shared workspace/ledger. Google accounts are *members*, not data containers.
  No query filters by account. There is exactly one `WorkspaceEntity` row.
- **D5**: amounts are a **positive magnitude** + a separate `kind` (EXPENSE|REFUND).
  Net = charges − refunds. A month can be net NEGATIVE.
- **D4**: 12 seeded categories; user can create/rename/reorder/hide. A category in use is
  hidden, never deleted.
- **D2**: Firebase Spark, free tier. See §6.8 — no live listeners (they cost money).
- **P1**: nothing leaves the device. No analytics/crash/perf SDKs, ever.
- **P9**: Room migrations are hand-written and tested. Never destructive.
- Indian digit grouping is required: `₹1,23,456.78`, never `₹123,456.78`.

## Money model (already decided — reuse exactly)

```kotlin
@JvmInline value class MagnitudePaise(val paise: Long) {
    init { require(paise >= 0) }
    fun toRupees(): BigDecimal = BigDecimal.valueOf(paise, 2)
}

@JvmInline value class Money(val paise: Long) {
    val isCredit: Boolean get() = paise < 0
    val magnitude: MagnitudePaise get() = MagnitudePaise(kotlin.math.abs(paise))
    companion object {
        fun of(m: MagnitudePaise, kind: ExpenseKind): Money =
            Money(if (kind == ExpenseKind.REFUND) -m.paise else m.paise)
    }
}

enum class ExpenseKind { EXPENSE, REFUND }
```

If you need to change this, stop and tell me first — it ripples everywhere.

## Testing expectations

- Unit tests for every money/aggregation/conflict function (plan.md §13.2 is the checklist).
- Use JUnit4 + `kotlinx-coroutines-test` + Turbine. MockK only if genuinely needed.
- Robolectric for anything touching Room/DataStore (`unitTests.isIncludeAndroidResources = true`).
- Test names read as sentences: ``fun `net month subtracts refunds from charges`() { }``
- Include the edge cases the plan calls out (leap year, year boundary, zero months, net-negative).

## Before you finish

1. `grep` your own files for `Log.`, `TODO:`, `FIXME:`, `fallbackToDestructiveMigration`.
2. Confirm every file ends with a newline.
3. Confirm no wildcard imports and no line > 120 chars.
4. Do **not** run gradle (not installed). Do **not** commit. The orchestrator handles git.