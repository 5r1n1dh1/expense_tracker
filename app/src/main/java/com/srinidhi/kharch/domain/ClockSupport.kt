package com.srinidhi.kharch.domain

import com.srinidhi.kharch.core.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single point of contact between the pure domain layer and wall-clock time (§13.2).
 *
 * Domain code must never call `LocalDate.now()` or `Instant.now()`: both read the machine
 * directly, which makes "today" untestable and silently device-dependent. Every "now" in this
 * layer therefore arrives through the injected [Clock], and reaches it through exactly one
 * member — [epochMillisNow] — so a unit test can replace time with a fake and so the clock
 * surface the domain compiles against stays small enough to keep honest.
 */

/** Milliseconds since the Unix epoch, as reported by the injected clock. */
val Clock.epochMillisNow: Long
    get() = nowMillis()

/** "Today" in [zone], derived from the injected clock. Never the machine's own date. */
fun Clock.todayIn(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillisNow).atZone(zone).toLocalDate()
