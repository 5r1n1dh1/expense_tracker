@file:Suppress("ImportOrdering")

package com.srinidhi.kharch.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srinidhi.kharch.R
import com.srinidhi.kharch.core.model.Money
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.motion.Motion
import com.srinidhi.kharch.ui.motion.rememberReducedMotion
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.KharchType
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import kotlin.math.roundToLong

/*
 * The dashboard header (§7.2A): the year's NET total, a count-up that re-runs on a year
 * change, and the year selector pill.
 *
 * The headline is net, always. "How much did I spend" and "how much came back" are two
 * different questions (§9), so the gross and the refund figure are shown underneath as
 * secondary numbers rather than folded into the hero amount.
 */

/** Length of a hero figure that still fits the hero type size without shrinking. */
private const val HERO_FULL_LENGTH = 11

/** Hero size once the figure runs longer than [HERO_FULL_LENGTH] characters. */
private val HERO_COMPACT_SIZE = 30.sp

/** Tag the year pill is found by in UI tests. */
private const val YEAR_PILL_TAG = "dashboard-year-pill"

/** The net figure's count-up, from zero. */
private const val COUNT_UP_FROM = 0f

/**
 * The glass header.
 *
 * @param year the year on screen.
 * @param netTotal the year's net, shown as the headline.
 * @param grossSpend charges only, shown as a secondary figure.
 * @param totalRefunds refunds only (a negative number, D5), shown as a secondary figure.
 * @param onYearPillClick opens the year selector sheet.
 * @param modifier layout only.
 */
@Composable
fun DashboardHeader(
    year: Int,
    netTotal: Money,
    grossSpend: Money,
    totalRefunds: Money,
    onYearPillClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    val reducedMotion = rememberReducedMotion()
    var revealed by remember(year) { mutableStateOf(false) }
    LaunchedEffect(year) { revealed = true }
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else COUNT_UP_FROM,
        animationSpec = if (reducedMotion) snap() else Motion.Emphasized,
        label = "dashboard-net-count-up",
    )
    val formatter = InrFormatter.Default
    val hero = formatter.format(countUpPaise(netTotal.paise, progress))
    val heroStyle = if (hero.length <= HERO_FULL_LENGTH) {
        KharchType.amountHero
    } else {
        KharchType.amountHero.copy(fontSize = HERO_COMPACT_SIZE)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(
                text = "Net · $year",
                style = KharchType.sectionLabel,
                color = glass.colors.onSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hero,
                style = heroStyle,
                color = glass.colors.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatter.format(grossSpend)} spent · ${formatter.format(totalRefunds)} back",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.colors.onSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        YearPill(year = year, onClick = onYearPillClick)
    }
}

/** The count-up's current value, in paise. */
private fun countUpPaise(target: Long, progress: Float): Long =
    (target.toDouble() * progress.coerceIn(COUNT_UP_FROM, 1f)).roundToLong()

/** The tap and swipe target that opens the year sheet. */
@Composable
private fun YearPill(year: Int, onClick: () -> Unit) {
    val glass = currentGlassTheme()
    GlassSurface(
        modifier = Modifier
            .clip(GlassShape.chip)
            .clickable(role = Role.Button) { onClick() }
            .testTag(YEAR_PILL_TAG),
        tier = GlassTier.Regular,
        shape = GlassShape.chip,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = glass.colors.onPrimary,
            )
            Icon(
                painter = painterResource(R.drawable.ic_year_expand),
                contentDescription = "Change year",
                tint = glass.colors.onSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
