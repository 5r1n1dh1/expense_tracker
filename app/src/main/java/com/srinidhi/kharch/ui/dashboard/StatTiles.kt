package com.srinidhi.kharch.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.R
import com.srinidhi.kharch.core.model.Money
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.domain.PaiseMath
import com.srinidhi.kharch.domain.dashboard.MONTHS_IN_YEAR
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.KharchType
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * The summary row (§7.2A): three glass tiles, and the year-over-year pill.
 *
 * The YoY direction is carried by an icon AND a sentence, never by colour alone — a
 * red/green pill is invisible to a user who cannot separate the two, and "same as last
 * year" has to be readable too.
 */

/** Inner inset of a stat tile. */
private val TILE_PADDING = PaddingValues(horizontal = 14.dp, vertical = 14.dp)

/** Gap between the three tiles. */
private val TILE_GAP = 10.dp

/** Tag prefix a stat tile is found by in UI tests. */
private const val TILE_TAG_PREFIX = "dashboard-stat-"

/** Tag the YoY pill is found by in UI tests. */
private const val YOY_TAG = "dashboard-yoy"

/** Size of the YoY direction icon. */
private val YOY_ICON_SIZE = 16.dp

/** Which of the three figures a tile headlines. */
enum class DashboardStat {
    /** The year's net. */
    Net,

    /** The year's net divided across the twelve months. */
    AveragePerMonth,

    /** The heaviest month's net. */
    HighestMonth,
}

/**
 * The three-tile summary row.
 *
 * @param netTotal the year's net total.
 * @param averagePerMonth the year's net across every [MONTHS_IN_YEAR], divided in integer
 *   paise — never a float average of rupees.
 * @param highestMonth the peak month's net.
 * @param modifier layout only.
 */
@Composable
fun DashboardStatTiles(
    netTotal: Money,
    averagePerMonth: Money,
    highestMonth: Money,
    modifier: Modifier = Modifier,
) {
    val formatter = InrFormatter.Default
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        StatTile(
            label = "Net total",
            value = formatter.format(netTotal),
            stat = DashboardStat.Net,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Average / month",
            value = formatter.format(averagePerMonth),
            stat = DashboardStat.AveragePerMonth,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Highest month",
            value = formatter.format(highestMonth),
            stat = DashboardStat.HighestMonth,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One tile. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    stat: DashboardStat,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    GlassSurface(
        modifier = modifier.testTag(TILE_TAG_PREFIX + stat.name),
        tier = GlassTier.Regular,
        shape = GlassShape.card,
        contentPadding = TILE_PADDING,
    ) {
        Column {
            Text(
                text = label,
                style = KharchType.caption,
                color = glass.colors.onSecondary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = KharchType.amountRow,
                color = glass.colors.onPrimary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The year-over-year pill.
 *
 * @param delta this period's net minus the same period last year. Negative means spending
 *   came down, positive means it went up.
 * @param previousYear the year being compared against, named in the sentence so the pill
 *   never depends on position or colour to be understood.
 * @param modifier layout only.
 */
@Composable
fun YearOverYearPill(
    delta: Money,
    previousYear: Int,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    val rising = delta.paise > 0L
    val icon = when {
        delta.paise < 0L -> R.drawable.ic_yoy_down
        rising -> R.drawable.ic_yoy_up
        else -> R.drawable.ic_yoy_flat
    }
    val tint = when {
        delta.paise < 0L -> glass.colors.credit
        rising -> glass.colors.expenseAccent
        else -> glass.colors.onSecondary
    }
    val sentence = when {
        delta.paise < 0L -> "less than $previousYear"
        rising -> "more than $previousYear"
        else -> "same as $previousYear"
    }

    GlassSurface(
        modifier = modifier.testTag(YOY_TAG),
        tier = GlassTier.Regular,
        shape = GlassShape.chip,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(YOY_ICON_SIZE),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${InrFormatter.Default.format(delta)} $sentence",
                style = MaterialTheme.typography.labelLarge,
                color = glass.colors.onPrimary,
                maxLines = 1,
            )
        }
    }
}

/** A year's net spread across the twelve months, in integer paise (§9). */
fun Money.averagePerMonth(): Money = Money(PaiseMath.roundedDivide(paise, MONTHS_IN_YEAR))
