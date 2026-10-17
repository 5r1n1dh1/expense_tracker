package com.srinidhi.kharch.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.data.prefs.GlassIntensityPreference
import com.srinidhi.kharch.data.prefs.ThemePreference
import com.srinidhi.kharch.ui.components.GlassButton
import com.srinidhi.kharch.ui.components.GlassCard
import com.srinidhi.kharch.ui.components.GlassTextField
import com.srinidhi.kharch.ui.glass.FrostedBackdrop
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/** Everything the settings screen renders. */
data class SettingsUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val glassIntensity: GlassIntensityPreference = GlassIntensityPreference.EXAGGERATED,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val ledgerName: String = "Kharch",
    val memberCount: Int = 0,
    val expenseCount: Int = 0,
    val lastSynced: String = "",
    val isSyncing: Boolean = false,
    val updateStatus: String? = null,
    val privacyText: String = "",
    val aboutText: String = "",
    val importPreview: String? = null,
)

/**
 * Settings screen (plan §7.2F).
 *
 * Stateless on purpose: values arrive through [state] and actions leave through callbacks.
 */
@Suppress("LongParameterList")
@Composable
fun SettingsScreen(
    state: SettingsUiState = SettingsUiState(),
    onThemeSelected: (ThemePreference) -> Unit = {},
    onGlassIntensitySelected: (GlassIntensityPreference) -> Unit = {},
    onHighContrastChange: (Boolean) -> Unit = {},
    onReduceMotionChange: (Boolean) -> Unit = {},
    onHapticsChange: (Boolean) -> Unit = {},
    onRetrySync: () -> Unit = {},
    onEraseLedger: () -> Unit = {},
    onExportJson: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportJson: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onReportIssue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()

    FrostedBackdrop(
        modifier = modifier.fillMaxSize(),
        overlay = glass.colors.backdropOverlay,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = glass.colors.onPrimary,
            )

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ledger & sync",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${state.ledgerName} \u00b7 ${state.memberCount} members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.colors.onSecondary,
                    )
                    Text(
                        text = "${state.expenseCount} expenses \u00b7 ${state.lastSynced}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.colors.onSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "Retry sync",
                            onClick = onRetrySync,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            text = "Erase ledger",
                            onClick = onEraseLedger,
                            variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Data",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "Export JSON",
                            onClick = onExportJson,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            text = "Export CSV",
                            onClick = onExportCsv,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    GlassButton(
                        text = "Import JSON",
                        onClick = onImportJson,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.importPreview != null) {
                        Text(
                            text = state.importPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.colors.onSecondary,
                        )
                    }
                    Text(
                        text = "Android Auto Backup is off so your ledger never leaves " +
                            "the device without your explicit export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.colors.onSecondary,
                    )
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.colors.onPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.values().forEach { option ->
                            GlassButton(
                                text = option.name.lowercase().replaceFirstChar { c ->
                                    c.uppercase()
                                },
                                onClick = { onThemeSelected(option) },
                                variant = if (state.theme == option) {
                                    com.srinidhi.kharch.ui.components.GlassButtonVariant.Primary
                                } else {
                                    com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        text = "Glass intensity",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.colors.onPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassIntensityPreference.values().forEach { option ->
                            GlassButton(
                                text = option.name.lowercase().replaceFirstChar { c ->
                                    c.uppercase()
                                },
                                onClick = { onGlassIntensitySelected(option) },
                                variant = if (state.glassIntensity == option) {
                                    com.srinidhi.kharch.ui.components.GlassButtonVariant.Primary
                                } else {
                                    com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "High contrast",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.colors.onPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.highContrast,
                            onCheckedChange = onHighContrastChange,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Reduce motion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.colors.onPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.reduceMotion,
                            onCheckedChange = onReduceMotionChange,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Haptics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.colors.onPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.hapticsEnabled,
                            onCheckedChange = onHapticsChange,
                        )
                    }
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Updates",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    GlassButton(
                        text = "Check for updates",
                        onClick = onCheckUpdates,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.updateStatus != null) {
                        Text(
                            text = state.updateStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.colors.onSecondary,
                        )
                    }
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Support",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    GlassButton(
                        text = "Report an issue",
                        onClick = onReportIssue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = state.privacyText.ifBlank {
                            "Nothing leaves the device except your own Firestore sync."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.colors.onSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = state.aboutText.ifBlank {
                            "Kharch \u00b7 open source \u00b7 licence attributions."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.colors.onSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
