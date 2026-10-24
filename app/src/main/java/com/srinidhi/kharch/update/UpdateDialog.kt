package com.srinidhi.kharch.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.components.GlassButton
import com.srinidhi.kharch.ui.components.GlassButtonVariant

/**
 * Dialog surfacing an available (or mandatory) update (§18.8).
 *
 * The signing-certificate fingerprint is shown so a sideloaded user can verify
 * that the APK they are about to install matches the one they already have. A
 * mandatory update cannot be dismissed.
 */
@Composable
fun UpdateDialog(
    result: UpdateResult,
    onDownload: (String) -> Unit = {},
    onInstall: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val manifest = when (result) {
        is UpdateResult.Available -> result.manifest
        is UpdateResult.UpdateAvailable -> result.manifest
        is UpdateResult.Mandatory -> result.manifest
        is UpdateResult.MandatoryUpdate -> result.manifest
        else -> null
    } ?: return

    val fingerprint = when (result) {
        is UpdateResult.Available -> result.certFingerprint
        is UpdateResult.UpdateAvailable -> result.certFingerprint
        is UpdateResult.Mandatory -> result.certFingerprint
        is UpdateResult.MandatoryUpdate -> result.certFingerprint
        else -> ""
    }

    val mandatory = result is UpdateResult.Mandatory || result is UpdateResult.MandatoryUpdate

    AlertDialog(
        onDismissRequest = { if (!mandatory) onDismiss() },
        title = {
            Text(
                text = if (mandatory) "Update required" else "Update available",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Version ${manifest.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (manifest.notes.isNotBlank()) {
                    Text(
                        text = manifest.notes,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (fingerprint.isNotBlank()) {
                    Text(
                        text = "Certificate fingerprint: $fingerprint",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "Android may still ask you to confirm the install.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            GlassButton(
                text = "Download",
                onClick = { onDownload(manifest.url) },
            )
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!mandatory) {
                    GlassButton(
                        text = "Later",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.Ghost,
                    )
                }
                GlassButton(
                    text = "Install",
                    onClick = { onInstall(manifest.url) },
                    variant = GlassButtonVariant.Ghost,
                )
            }
        },
    )
}
