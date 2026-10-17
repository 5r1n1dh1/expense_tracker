package com.srinidhi.kharch.ui.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.domain.expense.AmountBuffer
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.motion.KharchHaptic
import com.srinidhi.kharch.ui.motion.LocalHaptics
import com.srinidhi.kharch.ui.theme.KharchType
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * The custom INR keypad (plan §7.2C).
 *
 * There is deliberately no system IME anywhere near an amount. A numeric soft keyboard is a
 * full-height panel that fights the sheet, offers locale-dependent decimal separators, and
 * gives the user no way to cap the fraction — all three of which the [AmountBuffer] already
 * solves. The keys below are the only way an amount is ever typed.
 *
 * Every key carries its own semantics: `Role.Button`, a content description naming the key, and
 * a ≥ 48 dp touch target. TalkBack reads "amount, 12.50", not "1, 2, dot, 5, 0".
 */

/** Minimum touch target for a key (plan §13.2 accessibility). */
private val KEY_MIN_HEIGHT = 56.dp

/** Horizontal gap between keys. */
private val KEY_GUTTER = 8.dp

/** Vertical gap between key rows. */
private val KEY_ROW_GAP = 10.dp

/** The digits, in keypad order. */
private val DIGIT_ROWS = listOf(
    listOf(1, 2, 3),
    listOf(4, 5, 6),
    listOf(7, 8, 9),
)

/**
 * The amount display.
 *
 * The hero shows the digits as typed, and the caption underneath shows the same amount with
 * Indian grouping — `123456` reads as `₹1,23,456`. That is not decoration: the grouping is the
 * single most common way a mistyped amount reveals itself, and it is cheaper to spot a wrong
 * group boundary than a wrong digit.
 *
 * @param accent `credit` in Refund mode (D5), `expenseAccent` otherwise. Decorative only — a
 *   refund is also marked with a `↩` glyph and a label, so the accent is never load-bearing.
 */
@Composable
fun AmountHero(
    buffer: AmountBuffer,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = InrFormatter.RUPEE_SIGN,
                style = KharchType.amountHero,
                color = glass.colors.onSecondary,
            )
            Text(
                modifier = Modifier.testTag(AddEditTestTags.AMOUNT_HERO),
                text = buffer.raw.ifEmpty { "0" },
                style = KharchType.amountHero,
                color = glass.colors.onPrimary,
            )
        }
        Text(
            modifier = Modifier.testTag(AddEditTestTags.AMOUNT_HINT),
            text = InrFormatter.Default.formatPlain(buffer.paise()),
            style = KharchType.caption,
            color = accent,
        )
    }
}

/**
 * The 3 × 4 key grid plus the Done key.
 *
 * Done lives *inside* the keypad rather than beside it: a thumb already resting on the bottom
 * row should not have to travel to the far edge of the sheet to commit.
 */
@Suppress("LongParameterList")
@Composable
fun AmountKeypad(
    buffer: AmountBuffer,
    onDigit: (Int) -> Unit,
    onDecimalPoint: () -> Unit,
    onBackspace: () -> Unit,
    onClearAmount: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String,
    doneEnabled: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KEY_ROW_GAP),
    ) {
        for (row in DIGIT_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KEY_GUTTER),
            ) {
                for (digit in row) {
                    KeypadKey(
                        label = digit.toString(),
                        contentDescription = digit.toString(),
                        testTag = digitTestTag(digit),
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KEY_GUTTER),
        ) {
            KeypadKey(
                label = ".",
                contentDescription = "decimal point",
                testTag = AddEditTestTags.KEY_DECIMAL,
                onClick = onDecimalPoint,
                modifier = Modifier.weight(1f),
            )
            KeypadKey(
                label = "0",
                contentDescription = "zero",
                testTag = AddEditTestTags.KEY_DIGIT_ZERO,
                onClick = { onDigit(0) },
                modifier = Modifier.weight(1f),
            )
            KeypadKey(
                label = null,
                contentDescription = "backspace",
                testTag = AddEditTestTags.KEY_BACKSPACE,
                onClick = onBackspace,
                onLongClick = onClearAmount,
                modifier = Modifier.weight(1f),
            )
        }

        DoneKey(
            label = doneLabel,
            enabled = doneEnabled,
            accent = accent,
            onClick = onDone,
        )
    }
}

/**
 * One key: a glass pill on the Inset tier, because a keypad is an input surface and must never
 * draw the frosted backdrop (plan §8.3).
 *
 * @param label the glyph, or `null` for the backspace key which draws `⌫`.
 * @param onClick fired on tap, preceded by a [KharchHaptic.KeyTap].
 * @param onLongClick fired on long press, preceded by a [KharchHaptic.LongPress].
 */
@Composable
private fun KeypadKey(
    label: String?,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val glass = currentGlassTheme()
    val haptics = LocalHaptics.current
    val longClick = onLongClick
    GlassSurface(
        modifier = modifier
            .heightIn(min = KEY_MIN_HEIGHT)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onLongClickLabel = if (longClick == null) null else "clear amount",
                onClick = {
                    haptics.perform(KharchHaptic.KeyTap)
                    onClick()
                },
                onLongClick = if (longClick == null) {
                    null
                } else {
                    {
                        haptics.perform(KharchHaptic.LongPress)
                        longClick()
                    }
                },
            )
            .testTag(testTag),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label ?: BACKSPACE_GLYPH,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = glass.colors.onPrimary,
            )
        }
    }
}

/**
 * The Done / Save key.
 *
 * The disabled treatment is the point of this composable. The shared `GlassButton` dims its
 * label to 38 % when disabled, which is exactly the "low alpha" the plan forbids for a state
 * that has to be *read* (§7.2C: "disabled state clearly legible"). This key therefore keeps its
 * label at full `onPrimary` contrast on the Inset tier and changes only the fill behind it, so
 * the label stays legible and the reason it is unavailable is named by the sheet's supporting
 * line instead of by making the button harder to read.
 */
@Composable
private fun DoneKey(
    label: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val glass = currentGlassTheme()
    val haptics = LocalHaptics.current
    val fill = if (enabled && accent != Color.Unspecified) accent else Color.Transparent
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = KEY_MIN_HEIGHT)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = {
                    haptics.perform(KharchHaptic.Confirm)
                    onClick()
                },
            )
            .testTag(AddEditTestTags.KEY_DONE),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = glass.colors.onPrimary,
            )
        }
    }
}

private fun digitTestTag(digit: Int): String = when (digit) {
    1 -> AddEditTestTags.KEY_DIGIT_ONE
    2 -> AddEditTestTags.KEY_DIGIT_TWO
    3 -> AddEditTestTags.KEY_DIGIT_THREE
    4 -> AddEditTestTags.KEY_DIGIT_FOUR
    5 -> AddEditTestTags.KEY_DIGIT_FIVE
    6 -> AddEditTestTags.KEY_DIGIT_SIX
    7 -> AddEditTestTags.KEY_DIGIT_SEVEN
    8 -> AddEditTestTags.KEY_DIGIT_EIGHT
    else -> AddEditTestTags.KEY_DIGIT_NINE
}

/** The backspace glyph. U+232B is the "erase to the left" symbol. */
private const val BACKSPACE_GLYPH: String = "⌫"
