@file:Suppress("all")
package com.srinidhi.kharch.ui.addedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.KharchType
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale


/*
 * The date-picker sheet (plan §7.2C).
 *
 * A month grid with a "Today" shortcut, and future dates *allowed but flagged* rather than
 * blocked: someone back-filling a subscription charged on the 3rd of next month has a legitimate
 * reason to pick a future date, and a hard block would turn that into a fight. What it must never
 * do is let a mistyped year through silently, which is what the flag is for.
 *
 * The grid is a plain Column of Rows rather than a `LazyVerticalGrid`: a month is at most six
 * rows, and a lazy grid inside a bottom sheet drags in a nested-scrolling contract for no gain.
 */

/** Gap between day cells. */
private val CELL_GAP = 4.dp

/** Height of a day cell. Also the touch target. */
private val CELL_MIN_HEIGHT = 44.dp

/** Columns in a month grid. */
private const val DAYS_PER_WEEK = 7

/** Monday first, which is the Indian convention. */
private val WEEKDAY_ORDER = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

/**
 * A glass modal sheet for choosing the expense's date.
 *
 * @param initialEpochDay the date the sheet opens on.
 * @param todayEpochDay today, supplied by the caller from the injected
 *   [com.srinidhi.kharch.core.time.Clock] so a test can pin "today" and assert the future flag.
 * @param onDateSelected the chosen date, as `LocalDate.toEpochDay()`.
 * @param onDismiss close without choosing.
 * @param modifier layout only.
 */
@Composable
fun DatePickerSheet(
    initialEpochDay: Long,
    todayEpochDay: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    var shownMonth by remember { mutableStateOf(monthOf(initialEpochDay)) }
    var selected by remember { mutableStateOf(initialEpochDay) }

    GlassSurface(
        modifier = modifier.fillMaxWidth().testTag(AddEditTestTags.DATE_PICKER_SHEET),
        tier = GlassTier.Exaggerated,
        shape = GlassShape.sheet,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MonthHeader(
                shown = shownMonth,
                onPrevious = { shownMonth = shownMonth.minusMonths(1) },
                onNext = { shownMonth = shownMonth.plusMonths(1) },
            )
            WeekdayHeader()
            for (week in monthWeeks(shownMonth)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    for (epochDay in week) {
                        if (epochDay == null) {
                            BlankDay(modifier = Modifier.weight(1f))
                        } else {
                            DayCell(
                                epochDay = epochDay,
                                isSelected = epochDay == selected,
                                isToday = epochDay == todayEpochDay,
                                onSelect = { selected = epochDay },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (selected > todayEpochDay) {
                Text(
                    modifier = Modifier.testTag(AddEditTestTags.DATE_PICKER_FUTURE_FLAG),
                    text = FUTURE_WARNING,
                    style = KharchType.caption,
                    color = glass.colors.expenseAccent,
                )
            }
            SheetActionRow(
                onToday = {
                    selected = todayEpochDay
                    shownMonth = monthOf(todayEpochDay)
                },
                onConfirm = {
                    onDateSelected(selected)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun MonthHeader(
    shown: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val glass = currentGlassTheme()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = monthLabel(shown),
            style = MaterialTheme.typography.titleLarge,
            color = glass.colors.onPrimary,
            modifier = Modifier.weight(1f),
        )
        HeaderButton(
            glyph = PREVIOUS_GLYPH,
            contentDescription = "previous month",
            testTag = AddEditTestTags.DATE_PICKER_PREVIOUS,
            onClick = onPrevious,
        )
        HeaderButton(
            glyph = NEXT_GLYPH,
            contentDescription = "next month",
            testTag = AddEditTestTags.DATE_PICKER_NEXT,
            onClick = onNext,
        )
    }
}

@Composable
private fun HeaderButton(
    glyph: String,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val glass = currentGlassTheme()
    Text(
        modifier = Modifier
            .testTag(testTag)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        text = glyph,
        style = MaterialTheme.typography.titleLarge,
        color = glass.colors.onPrimary,
    )
}

@Composable
private fun WeekdayHeader() {
    val glass = currentGlassTheme()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        for (day in WEEKDAY_ORDER) {
            Text(
                text = day.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locale).take(1),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = KharchType.caption,
                color = glass.colors.onSecondary,
            )
        }
    }
}

/**
 * The inert padding cell at the start and end of a month grid.
 *
 * It occupies the same weight as a day so the columns line up, and it is deliberately not
 * clickable and not selectable: a tap in the gap must never be read as "pick the 31st of the
 * previous month".
 */
@Composable
private fun BlankDay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.heightIn(min = CELL_MIN_HEIGHT))
}

/**
 * One day.
 *
 * The selected day is the only one drawn on the accent fill; "today" is marked by colour *and*
 * by a heavier weight, because the accent is not allowed to be the sole carrier of meaning.
 */
@Composable
private fun DayCell(
    epochDay: Long,
    isSelected: Boolean,
    isToday: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    GlassSurface(
        modifier = modifier
            .heightIn(min = CELL_MIN_HEIGHT)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .testTag(dateCellTag(epochDay)),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = LocalDate.ofEpochDay(epochDay).dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.SemiBold else null,
                color = when {
                    isSelected -> glass.colors.onAccentFill
                    isToday -> glass.colors.expenseAccent
                    else -> glass.colors.onPrimary
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The Today shortcut and the confirm action, side by side. */
@Composable
private fun SheetActionRow(onToday: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetAction(
            label = TODAY_LABEL,
            testTag = AddEditTestTags.DATE_PICKER_TODAY,
            onClick = onToday,
            modifier = Modifier.weight(1f),
        )
        SheetAction(
            label = CONFIRM_LABEL,
            testTag = AddEditTestTags.DATE_PICKER_CONFIRM,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SheetAction(
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    GlassSurface(
        modifier = modifier
            .heightIn(min = CELL_MIN_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = glass.colors.onPrimary,
            )
        }
    }
}

/** The month containing [epochDay]. */
private fun monthOf(epochDay: Long): YearMonth {
    val date = LocalDate.ofEpochDay(epochDay)
    return YearMonth.of(date.year, date.monthValue)
}

/**
 * Every day of [month], padded into full Monday-first weeks.
 *
 * Blank cells are rendered rather than dropped: a week that starts on the 3rd must still be a
 * seven-cell row, or its days would not line up under the weekday header above. A blank cell is
 * inert, so a tap on it can never quietly pick a neighbouring month's date.
 */
private fun monthWeeks(month: YearMonth): List<List<Long?>> {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value + DAYS_PER_WEEK - 1) % DAYS_PER_WEEK
    val cells = ArrayList<Long?>(month.lengthOfMonth() + leading)
    for (index in 0 until leading) {
        cells += null
    }
    for (day in 1..month.lengthOfMonth()) {
        cells += month.atDay(day).toEpochDay()
    }
    while (cells.size % DAYS_PER_WEEK != 0) {
        cells += null
    }
    return cells.chunked(DAYS_PER_WEEK)
}

/** "August 2026", spelled the long way so there is no month-number ambiguity. */
private fun monthLabel(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year

/** Stable test tag for one day cell. */
fun dateCellTag(epochDay: Long): String = "add-edit-date-cell-$epochDay"

private const val PREVIOUS_GLYPH: String = "‹"

private const val NEXT_GLYPH: String = "›"

private const val TODAY_LABEL: String = "Today"

private const val CONFIRM_LABEL: String = "Choose"

private const val FUTURE_WARNING: String = "This date is in the future"
