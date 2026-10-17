package com.srinidhi.kharch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.components.GlassButton
import com.srinidhi.kharch.ui.components.GlassCard
import com.srinidhi.kharch.ui.glass.FrostedBackdrop
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/** A linked Google account as rendered on the Accounts screen. */
data class AccountMember(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUri: String? = null,
    val isActive: Boolean = false,
)

/** Everything the Accounts screen renders. */
data class AccountsUiState(
    val ledgerName: String = "Kharch",
    val memberCount: Int = 0,
    val expenseCount: Int = 0,
    val lastSynced: String = "",
    val members: List<AccountMember> = emptyList(),
    val showChoiceSheet: Boolean = false,
    val availableLedgers: Int = 1,
    val removeBlockedReason: String? = null,
)

/**
 * Accounts & ledger screen (plan §7.2D, decision D1).
 *
 * Stateless: values arrive through [state] and actions leave through callbacks.
 */
@Suppress("LongParameterList")
@Composable
fun AccountsScreen(
    state: AccountsUiState = AccountsUiState(),
    onLinkAccount: () -> Unit = {},
    onRemoveAccount: (String) -> Unit = {},
    onSwitchLedger: () -> Unit = {},
    onChoiceJoin: () -> Unit = {},
    onChoiceStartSeparate: () -> Unit = {},
    onChoiceDismiss: () -> Unit = {},
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.headlineSmall,
                color = glass.colors.onPrimary,
            )

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.ledgerName,
                        style = MaterialTheme.typography.titleLarge,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${state.memberCount} members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.colors.onSecondary,
                    )
                    Text(
                        text = "${state.expenseCount} expenses \u00b7 ${state.lastSynced}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.colors.onSecondary,
                    )
                }
            }

            state.members.forEach { member ->
                MemberRow(
                    member = member,
                    canRemove = state.members.size > 1,
                    onRemove = { onRemoveAccount(member.uid) },
                )
            }

            if (state.removeBlockedReason != null) {
                Text(
                    text = state.removeBlockedReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.colors.onSecondary,
                )
            }

            GlassButton(
                text = "Link another Google account",
                onClick = onLinkAccount,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.availableLedgers > 1) {
                GlassButton(
                    text = "Switch ledger",
                    onClick = onSwitchLedger,
                    variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.showChoiceSheet) {
            ChoiceSheet(
                onJoin = onChoiceJoin,
                onStartSeparate = onChoiceStartSeparate,
                onDismiss = onChoiceDismiss,
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: AccountMember,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    val glass = currentGlassTheme()

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(glass.colors.accentFill.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials(member),
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.colors.onPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.email ?: member.uid,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.colors.onPrimary,
                )
                if (member.displayName != null) {
                    Text(
                        text = member.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.colors.onSecondary,
                    )
                }
                if (member.isActive) {
                    Text(
                        text = "Active now",
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.colors.credit,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (canRemove) {
                GlassButton(
                    text = "Remove",
                    onClick = onRemove,
                    variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    onJoin: () -> Unit,
    onStartSeparate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val glass = currentGlassTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Join or start a new ledger?",
                style = MaterialTheme.typography.titleLarge,
                color = glass.colors.onPrimary,
            )
            Text(
                text = "Join this ledger to share expenses, or start a separate one.",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.colors.onSecondary,
            )
            GlassButton(
                text = "Join this ledger",
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
            )
            GlassButton(
                text = "Start a separate ledger",
                onClick = onStartSeparate,
                variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun initials(member: AccountMember): String {
    val source = member.displayName ?: member.email ?: member.uid
    return source.trim().take(1).uppercase()
}
