package com.srinidhi.kharch.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * Frosted inset text field (§8.3, Inset tier).
 *
 * Inset surfaces deliberately do NOT draw the frosted backdrop: they sit on top
 * of a sheet, they get the strongest scrim, and they stay legible over anything
 * scrolling underneath.
 *
 * The Material container and indicator colours are forced transparent because
 * the GlassSurface *is* the container. Anything else would stack two surfaces.
 */

/** Inset between the Inset panel and the field. */
private val FIELD_PADDING = PaddingValues(horizontal = 4.dp, vertical = 4.dp)

/**
 * A frosted inset text field.
 *
 * @param value current text.
 * @param onValueChange fired on every edit.
 * @param modifier layout only.
 * @param label field label, always visible above the text.
 * @param keyboardOptions pass `KeyboardOptions(keyboardType = KeyboardType.Number)`
 *   for the amount field.
 * @param leadingIcon optional leading slot.
 * @param trailingIcon optional trailing slot (clear, visibility, …).
 * @param enabled disabled state; the field keeps its shape and stays in the tree.
 *
 * For `isError`, custom `supportingText`, or `keyboardActions`, call Material 3's
 * `TextField` directly inside `GlassSurface(tier = GlassTier.Inset, shape =
 * GlassShape.field)` and pass [glassTextFieldColors]. That is the documented
 * escape hatch, not an oversight.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val glass = currentGlassTheme()
    val labelText = label
    val labelSlot: (@Composable () -> Unit)? = if (labelText == null) {
        null
    } else {
        {
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium,
                color = glass.colors.onSecondary,
            )
        }
    }

    GlassSurface(
        modifier = modifier,
        tier = GlassTier.Inset,
        shape = GlassShape.field,
        contentPadding = FIELD_PADDING,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = labelSlot,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = glass.colors.onPrimary),
            colors = glassTextFieldColors(),
            shape = GlassShape.field,
        )
    }
}

/**
 * Transparent container and indicator colours on top of glass.
 *
 * @param isError only used to keep the error cursor in step with the label.
 */
@Composable
fun glassTextFieldColors(isError: Boolean = false): TextFieldColors {
    val glass = currentGlassTheme()
    val text = glass.colors.onPrimary
    return TextFieldDefaults.colors(
        focusedTextColor = text,
        unfocusedTextColor = text,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = if (isError) text else glass.colors.accentFill,
    )
}
