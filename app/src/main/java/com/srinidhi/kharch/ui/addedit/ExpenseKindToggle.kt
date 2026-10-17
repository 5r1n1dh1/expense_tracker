package com.srinidhi.kharch.ui.addedit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.motion.KharchHaptic
import com.srinidhi.kharch.ui.motion.LocalHaptics
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * The Expense / Refund segmented control (D5, plan §7.2C).
 *
 * The single most consequential control on the sheet: it is the only place `kind` is set, and
 * `kind` is what decides whether an amount adds to the month or subtracts from it (plan §5.1).
 *
 * It sits *above* the keypad, so the direction is chosen before the amount rather than after —
 * the alternative (type the amount, then notice the toggle is on Refund) is how a ₹2,000
 * grocery run becomes a ₹2,000 credit.
 *
 * Selection is expressed semantically, not by colour: the chosen segment carries
 * `Role.RadioButton` plus `selected`, so TalkBack says "Refund, selected" and a test can assert
 * it without reading a pixel.
 */

/** Minimum height of a segment. */
private val SEGMENT_MIN_HEIGHT = 48.dp

/** Inner inset so the two segments read as one pill rather than two boxes. */
private val SEGMENT_PADDING = 4.dp

/** Gap between the two segments. */
private val SEGMENT_GAP = 4.dp

/** Label of the charge segment. */
private const val EXPENSE_LABEL = "Expense"

/** Label of the refund segment. */
private const val REFUND_LABEL = "Refund"

/**
 * The two-state kind control.
 *
 * @param kind the current direction.
 * @param onKindChange fired on change, preceded by a [KharchHaptic.Selection] so the switch is
 *   felt as well as seen.
 * @param modifier layout only.
 */
@Composable
fun ExpenseKindToggle(
    kind: ExpenseKind,
    onKindChange: (ExpenseKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SEGMENT_PADDING),
            horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            Segment(
                label = EXPENSE_LABEL,
                selected = kind == ExpenseKind.EXPENSE,
                testTag = AddEditTestTags.KIND_EXPENSE,
                onClick = {
                    haptics.perform(KharchHaptic.Selection)
                    onKindChange(ExpenseKind.EXPENSE)
                },
                modifier = Modifier.weight(1f),
            )
            Segment(
                label = REFUND_LABEL,
                selected = kind == ExpenseKind.REFUND,
                testTag = AddEditTestTags.KIND_REFUND,
                onClick = {
                    haptics.perform(KharchHaptic.Selection)
                    onKindChange(ExpenseKind.REFUND)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One half of the control.
 *
 * The selected segment lifts onto the accent fill; the unselected one stays on bare Inset glass
 * with an `onSecondary` label. The label never changes colour to near-transparency either way,
 * so an unselected segment is as legible as a selected one.
 */
@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    val background by animateColorAsState(
        targetValue = if (selected) glass.colors.accentFill else Color.Transparent,
        label = "kind-segment-fill-$label",
    )
    val content = if (selected) glass.colors.onAccentFill else glass.colors.onSecondary
    GlassSurface(
        modifier = modifier
            .heightIn(min = SEGMENT_MIN_HEIGHT)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
            )
        }
    }
}
