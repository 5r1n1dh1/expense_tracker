@file:Suppress("ImportOrdering", "NoUnusedImports", "NoWildcardImports", "LongParameterList", "CyclomaticComplexMethod", "MaxLineLength", "UnusedPrivateMember", "ReturnCount", "TooManyFunctions")
package com.srinidhi.kharch.ui.dashboard

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Money
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.domain.dashboard.YearDashboard
import com.srinidhi.kharch.ui.components.EmptyState
import com.srinidhi.kharch.ui.components.GlassCard
import com.srinidhi.kharch.ui.glass.FrostedBackdrop
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.KharchType
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import kotlin.math.abs

/*
 * The dashboard (§7.2A, §9).
 *
 * The chrome is always there. An empty ledger changes what sits *inside* the chart frame —
 * the frosted orb — and nothing else: every bar slot still renders, the frame keeps its
 * height, and the populated dashboard lands on exactly the same bounds (D9).
 */

/** The tag the chart frame is found by in the bounds-parity test. */
const val CHART_FRAME_TAG: String = "dashboard-chart-frame"

/** The tag the category split's frame is found by in UI tests. */
const val CATEGORY_FRAME_TAG: String = "dashboard-category-frame"

/** Insets of the screen's scrolling content. */
private val CONTENT_PADDING = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)

/** Vertical gap between the screen's cards. */
private val CARD_GAP = 16.dp

/** How far the bars translate per pixel of rubber-banded drag. */
private const val PARALLAX_GAIN = 0.35f

/** The chart's alpha floor as a swipe drags further past the threshold. */
private const val PARALLAX_ALPHA_FLOOR = 0.4f

/** How far above the zero line the `−₹` axis label sits. */
private val AXIS_LABEL_OFFSET = (-12).dp

/**
 * The dashboard.
 *
 * @param state everything the screen draws, from [DashboardViewModel].
 * @param onYearChange invoked with the year to step or jump to.
 * @param onMonthChange invoked with the 1-based month of a tapped bar, or `null` to close.
 * @param onSyncRequest invoked by pull-to-refresh; the app root owns the sync itself.
 * @param onAddExpense invoked by the empty state's call to action.
 * @param modifier layout only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int?) -> Unit,
    onSyncRequest: () -> Unit,
    onAddExpense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    var yearSheetVisible by remember { mutableStateOf(false) }
    val swipe = rememberYearSwipeState(
        year = state.year,
        canStepNewer = state.canStepNewer,
        canStepOlder = state.canStepOlder,
        onYearChange = onYearChange,
    )

    FrostedBackdrop(modifier = modifier.fillMaxSize(), overlay = glass.colors.backdropOverlay) {
        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = onSyncRequest,
            modifier = Modifier.fillMaxSize(),
            state = rememberPullToRefreshState(),
        ) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                DashboardHeader(
                    year = state.year,
                    netTotal = state.dashboard.netTotal,
                    grossSpend = state.dashboard.grossSpend,
                    totalRefunds = state.dashboard.totalRefunds,
                    onYearPillClick = { yearSheetVisible = true },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = CONTENT_PADDING,
                    verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                ) {
                    item {
                        MonthlySplitCard(
                            dashboard = state.dashboard,
                            currentMonth = state.currentMonth,
                            selectedMonth = state.selectedMonth,
                            swipeOffset = swipe.visualOffset,
                            thresholdPx = swipe.thresholdPx,
                            swipe = swipe,
                            onMonthChange = onMonthChange,
                            onAddExpense = onAddExpense,
                        )
                    }
                    item {
                        CategorySplitCard(
                            dashboard = state.dashboard,
                            categories = state.displayCategories,
                        )
                    }
                    item {
                        DashboardStatTiles(
                            netTotal = state.dashboard.netTotal,
                            averagePerMonth = state.dashboard.netTotal.averagePerMonth(),
                            highestMonth = highestMonthNet(state.dashboard),
                        )
                    }
                    item {
                        YearOverYearPill(
                            delta = state.dashboard.yoyDelta,
                            previousYear = state.dashboard.year - 1,
                        )
                    }
                }
            }
        }
    }

    if (yearSheetVisible) {
        YearSelectorSheet(
            years = state.selectableYears,
            selectedYear = state.year,
            onYearSelected = { year ->
                onYearChange(year)
                yearSheetVisible = false
            },
            onDismissRequest = { yearSheetVisible = false },
        )
    }

    val detail = state.monthDetail
    if (detail != null) {
        MonthDetailSheet(
            breakdown = detail,
            categories = state.categories,
            onDismissRequest = { onMonthChange(null) },
        )
    }
}

/**
 * The stateful entry point.
 *
 * Collects [DashboardViewModel] and hands its sync requests to the app root, which owns
 * the `SyncCoordinator`. The screen itself never touches the network.
 */
@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onYearChange = viewModel::onYearSelected,
        onMonthChange = viewModel::onMonthSelected,
        onSyncRequest = viewModel::requestSync,
        onAddExpense = { /* The app root opens the add sheet. */ },
        modifier = modifier,
    )
}

/** The monthly split card: the swipe target, the frame and the chart. */
@Suppress("LongParameterList")
@Composable
private fun MonthlySplitCard(
    dashboard: YearDashboard,
    currentMonth: Int?,
    selectedMonth: Int?,
    swipeOffset: Float,
    thresholdPx: Float,
    swipe: YearSwipeState,
    onMonthChange: (Int?) -> Unit,
    onAddExpense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    val axis = BarChartMath.axis(dashboard.hasNegativeMonth)
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CHART_FRAME_TAG)
            .chartSwipeGesture(swipe),
        tier = GlassTier.Exaggerated,
        shape = GlassShape.panel,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = "Monthly split",
            style = KharchType.sectionLabel,
            color = glass.colors.onSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(CHART_TRACK_HEIGHT)) {
            MonthlyBarChart(
                dashboard = dashboard,
                currentMonth = currentMonth,
                selectedMonth = selectedMonth,
                onMonthSelected = { month -> onMonthChange(month) },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = swipeOffset * PARALLAX_GAIN
                        val fade = 1f - abs(swipeOffset) / (thresholdPx * 4f)
                        alpha = fade.coerceAtLeast(PARALLAX_ALPHA_FLOOR)
                    },
            )
            if (dashboard.hasNegativeMonth) {
                Text(
                    text = "−₹",
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.colors.credit,
                    modifier = Modifier
                        .align(if (axis.centred) Alignment.CenterEnd else Alignment.BottomEnd)
                        .offset(y = AXIS_LABEL_OFFSET)
                        .padding(horizontal = 6.dp),
                )
            }
            if (dashboard.isEmpty) {
                EmptyChartOverlay(onAddExpense = onAddExpense)
            }
        }
    }
}

/** The category split card: donut over legend. */
@Composable
private fun CategorySplitCard(
    dashboard: YearDashboard,
    categories: List<Category>,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    val labels = remember(categories) { categories.associate { category -> category.id to category.name } }
    val colours = remember(categories) {
        categories.associate { category -> category.id to categoryColour(category.colorToken) }
    }
    val slices = remember(dashboard.categoryNet, labels, colours) {
        donutSlices(categoryNet = dashboard.categoryNet, labels = labels, colours = colours)
    }

    GlassCard(
        modifier = modifier.fillMaxWidth().testTag(CATEGORY_FRAME_TAG),
        tier = GlassTier.Exaggerated,
        shape = GlassShape.panel,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = "By category",
            style = KharchType.sectionLabel,
            color = glass.colors.onSecondary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        CategoryDonut(
            slices = slices,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            centreContent = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Net",
                        style = KharchType.caption,
                        color = glass.colors.onSecondary,
                    )
                    Text(
                        text = InrFormatter.Default.format(dashboard.netTotal),
                        style = KharchType.amountRow,
                        color = glass.colors.onPrimary,
                        maxLines = 1,
                    )
                }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (slices.isEmpty()) {
            Text(
                text = "Nothing tracked yet",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.colors.onSecondary,
            )
        } else {
            CategoryLegend(slices = slices)
        }
    }
}

/** The designed empty state that replaces the chart's contents, never its bounds (D9). */
@Composable
private fun BoxScope.EmptyChartOverlay(onAddExpense: () -> Unit) {
    val glass = currentGlassTheme()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            headline = "Nothing tracked yet",
            subline = "Tap + to add your first expense",
            actionLabel = "Add expense",
            onAction = onAddExpense,
            orbSize = 56.dp,
            orbContent = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "₹",
                        style = MaterialTheme.typography.headlineSmall,
                        color = glass.colors.onPrimary,
                    )
                }
            },
        )
    }
}

/** The heaviest month's net, or ₹0 when nothing was tracked. */
private fun highestMonthNet(dashboard: YearDashboard): Money {
    val peak = BarChartMath.peakMonth(dashboard) ?: return Money(0L)
    return dashboard.netForMonth(peak)
}

/** Attaches the year swipe to a surface (§7.3). */
private fun Modifier.chartSwipeGesture(swipe: YearSwipeState): Modifier =
    this.then(
        Modifier.pointerInput(swipe) {
            detectHorizontalDragGestures(
                onDragStart = { swipe.reset() },
                onDragEnd = { swipe.commit() },
                onDragCancel = { swipe.reset() },
            ) { _, delta -> swipe.drag(delta) }
        },
    )
