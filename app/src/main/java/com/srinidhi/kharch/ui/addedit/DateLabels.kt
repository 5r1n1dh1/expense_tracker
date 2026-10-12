package com.srinidhi.kharch.ui.addedit

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * The date chip's label.
 *
 * "Today · Tue, 12 Aug 2026" rather than "12 Aug 2026", because the distance from today is the
 * one thing a person scanning a list actually needs and the one thing a bare date hides. The
 * relative word is a prefix, so the absolute date is never lost to it — and a date three weeks
 * out has no relative word at all, which is deliberate: "soon" would be a lie.
 */

/** The absolute part of every label: `12 Aug 2026`. */
private val ABSOLUTE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT)

/** `Today` for the selected date. */
private const val TODAY_WORD = "Today"

/** `Yesterday`. */
private const val YESTERDAY_WORD = "Yesterday"

/** `Tomorrow`. */
private const val TOMORROW_WORD = "Tomorrow"

/** Separator between the relative word and the date. */
private const val WORD_SEPARATOR = " · "

/**
 * The label for the date chip.
 *
 * @param epochDay the selected date, as `LocalDate.toEpochDay()`.
 * @param todayEpochDay today, from the caller's injected
 *   [com.srinidhi.kharch.core.time.Clock].
 */
fun dateChipLabel(epochDay: Long, todayEpochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ROOT)
    val absolute = "$weekday, ${ABSOLUTE_FORMAT.format(date)}"
    val relative = when (epochDay - todayEpochDay) {
        0L -> TODAY_WORD
        -1L -> YESTERDAY_WORD
        1L -> TOMORROW_WORD
        else -> null
    }
    return if (relative == null) absolute else relative + WORD_SEPARATOR + absolute
}
