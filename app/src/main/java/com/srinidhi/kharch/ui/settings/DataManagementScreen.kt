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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.data.export.JsonImporter
import com.srinidhi.kharch.ui.components.GlassButton
import com.srinidhi.kharch.ui.components.GlassCard
import com.srinidhi.kharch.ui.glass.FrostedBackdrop
import com.srinidhi.kharch.ui.theme.currentGlassTheme

enum class DateRangeChoice {
    ALL,
    LAST_30_DAYS,
    THIS_YEAR,
    CUSTOM,
}

data class DataManagementUiState(
    val dateRange: DateRangeChoice = DateRangeChoice.ALL,
    val conflictStrategy: JsonImporter.ConflictStrategy =
        JsonImporter.ConflictStrategy.MERGE,
    val importPreview: String? = null,
    val isImporting: Boolean = false,
)

@Composable
fun DataManagementScreen(
    state: DataManagementUiState = DataManagementUiState(),
    onDateRangeSelected: (DateRangeChoice) -> Unit = {},
    onExportJson: (DateRangeChoice) -> Unit = {},
    onExportCsv: (DateRangeChoice) -> Unit = {},
    onImportJson: () -> Unit = {},
    onConflictStrategySelected: (JsonImporter.ConflictStrategy) -> Unit = {},
    onConfirmImport: () -> Unit = {},
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
                text = "Data management",
                style = MaterialTheme.typography.headlineSmall,
                color = glass.colors.onPrimary,
            )

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Export range",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateRangeChoice.values().forEach { choice ->
                            GlassButton(
                                text = choice.name.lowercase().replaceFirstChar { c ->
                                    c.uppercase()
                                },
                                onClick = { onDateRangeSelected(choice) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "Export JSON",
                            onClick = { onExportJson(state.dateRange) },
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            text = "Export CSV",
                            onClick = { onExportCsv(state.dateRange) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Import",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                    )
                    GlassButton(
                        text = "Choose JSON file",
                        onClick = onImportJson,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Conflict strategy",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.colors.onPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JsonImporter.ConflictStrategy.values().forEach { strategy ->
                            GlassButton(
                                text = strategy.name.lowercase().replaceFirstChar { c ->
                                    c.uppercase()
                                },
                                onClick = { onConflictStrategySelected(strategy) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (state.importPreview != null) {
                        Text(
                            text = state.importPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.colors.onSecondary,
                        )
                    }
                    GlassButton(
                        text = "Confirm import",
                        onClick = onConfirmImport,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
