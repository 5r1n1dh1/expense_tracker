package com.srinidhi.kharch.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Milliseconds in one day. Exact: UTC days are always 86 400 000 ms, no leap seconds. */
const val MILLIS_PER_DAY: Long = 86_400_000L

/**
 * The only source of "now" in the app.
 *
 * The domain layer must never call `LocalDate.now()` or `System.currentTimeMillis()` directly —
 * it takes a [Clock] so tests can pin time and assert the leap-year and year-boundary behaviour
 * the plan calls out (plan §13.2). [SystemClock] is the single place the real system clock is
 * read; nothing else in `main` may do so.
 */
interface Clock {
    /** Wall-clock time in epoch millis, UTC. */
    fun nowMillis(): Long

    /** The current calendar date in the device's time zone. */
    fun today(): LocalDate
}

/**
 * The production clock. Reads the real system clock, resolved against [zone].
 *
 * @property zone the zone [today] is reported in. Defaults to the device zone.
 */
class SystemClock(private val zone: ZoneId = ZoneId.systemDefault()) : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun today(): LocalDate = LocalDate.now(zone)
}

/** The calendar date in [zone] at the instant described by [millis]. */
fun localDateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/**
 * The **UTC** epoch day containing [millis].
 *
 * This is the UTC day, which is what `dateEpochDay` stores. Converting a *local* date to an
 * epoch day must go through `LocalDate.toEpochDay()` instead, which already accounts for the
 * zone offset — this helper only exists for reading back an instant that was written as a
 * timestamp.
 */
fun epochDayOf(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_DAY)
