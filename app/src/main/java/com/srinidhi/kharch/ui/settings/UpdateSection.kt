package com.srinidhi.kharch.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.components.GlassButton
import com.srinidhi.kharch.ui.components.GlassCard
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import com.srinidhi.kharch.update.UpdateDialog
import com.srinidhi.kharch.update.UpdateResult

/**
 * "Check for updates" surface for Settings (§7.2F, §18.8).
 *
 * Keeps the network call user-initiated: nothing runs until [onCheckUpdates] is
 * invoked. The dialog is shown only when an [UpdateResult] that carries an update
 * is available.
 */
data class UpdateSectionState(
    val status: String? = null,
    val result: UpdateResult? = null,
)

@Composable
fun UpdateSection(
    state: UpdateSectionState = UpdateSectionState(),
    onCheckUpdates: () -> Unit = {},
    onDownload: (String) -> Unit = {},
    onInstall: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    var showDialog by remember { mutableStateOf(false) }

    if (state.result != null &&
        (state.result is UpdateResult.Available ||
            state.result is UpdateResult.UpdateAvailable ||
            state.result is UpdateResult.Mandatory ||
            state.result is UpdateResult.MandatoryUpdate)
    ) {
        showDialog = true
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Updates",
                style = MaterialTheme.typography.titleMedium,
                color = glass.colors.onPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            GlassButton(
                text = "Check for updates",
                onClick = onCheckUpdates,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.status != null) {
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.colors.onSecondary,
                )
            }
        }
    }

    if (showDialog && state.result != null) {
        UpdateDialog(
            result = state.result,
            onDownload = onDownload,
            onInstall = onInstall,
            onDismiss = {
                showDialog = false
                onDismiss()
            },
        )
    }
}
