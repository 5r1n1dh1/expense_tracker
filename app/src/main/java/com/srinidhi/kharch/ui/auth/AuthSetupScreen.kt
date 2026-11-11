package com.srinidhi.kharch.ui.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srinidhi.kharch.R
import com.srinidhi.kharch.data.auth.GoogleUser
import com.srinidhi.kharch.data.local.SYSTEM_CATEGORIES
import com.srinidhi.kharch.ui.components.GlassButton
import com.srinidhi.kharch.ui.components.GlassButtonVariant
import com.srinidhi.kharch.ui.components.GlassCard
import com.srinidhi.kharch.ui.components.GlassTextField
import com.srinidhi.kharch.ui.components.categoryIcon
import com.srinidhi.kharch.ui.glass.FrostedBackdrop
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.currentGlassTheme

@Composable
fun AuthSetupScreen(
    state: AuthUiState,
    onSignIn: (ActivityResultLauncher<IntentSenderRequest>) -> Unit,
    onSignInResult: (Intent) -> Unit,
    onCompleteSetup: (user: GoogleUser, ledgerName: String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            onSignInResult(result.data!!)
        }
    }

    FrostedBackdrop(
        modifier = modifier.fillMaxSize(),
        overlay = glass.colors.backdropOverlay,
    ) {
        when (state) {
            is AuthUiState.Unauthenticated -> {
                WelcomeSignInContent(
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onSignInClicked = { onSignIn(launcher) },
                )
            }
            is AuthUiState.SetupNeeded -> {
                SetupProcessContent(
                    user = state.user,
                    defaultLedgerName = state.defaultLedgerName,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onComplete = { name -> onCompleteSetup(state.user, name) },
                    onSignOut = onSignOut,
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = glass.colors.accentFill)
                }
            }
        }
    }
}

@Composable
private fun WelcomeSignInContent(
    isLoading: Boolean,
    errorMessage: String?,
    onSignInClicked: () -> Unit,
) {
    val glass = currentGlassTheme()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(glass.colors.accentFill.copy(alpha = 0.25f))
                .border(1.dp, glass.colors.accentFill.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cat_payments),
                contentDescription = "Kharch Logo",
                tint = glass.colors.onPrimary,
                modifier = Modifier.size(36.dp),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Kharch",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = glass.colors.onPrimary,
                letterSpacing = 0.5.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Fast, offline-first expense management\nwith shared ledger sync",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = glass.colors.onSecondary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureRow(
                    iconRes = R.drawable.ic_cat_payments,
                    title = "Indian Rupee Math",
                    description = "Exact paise precision and native Lakh/Crore grouping.",
                )
                FeatureRow(
                    iconRes = R.drawable.ic_cat_home,
                    title = "Multi-Account Ledger",
                    description = "Share one family ledger with up to 5 Google accounts.",
                )
                FeatureRow(
                    iconRes = R.drawable.ic_cat_receipt_long,
                    title = "Offline-First & Private",
                    description = "Works seamlessly offline with instant cloud sync.",
                )
            }
        }

        if (errorMessage != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.colors.expenseAccent,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            CircularProgressIndicator(color = glass.colors.accentFill)
        } else {
            GoogleSignInButton(
                onClick = onSignInClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_sign_in_button"),
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .clip(GlassShape.chip)
            .border(1.dp, glass.colors.accentFill.copy(alpha = 0.5f), GlassShape.chip),
        color = glass.colors.accentFill.copy(alpha = 0.15f),
        shape = GlassShape.chip,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "G",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = glass.colors.accentFill,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = glass.colors.onPrimary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupProcessContent(
    user: GoogleUser,
    defaultLedgerName: String,
    isLoading: Boolean,
    errorMessage: String?,
    onComplete: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val glass = currentGlassTheme()
    val scrollState = rememberScrollState()
    var ledgerName by remember { mutableStateOf(defaultLedgerName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text(
                text = "Setup Your Ledger",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = glass.colors.onPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Welcome, ${user.displayName ?: user.label}! Let's customize your ledger.",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.colors.onSecondary,
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(glass.colors.accentFill.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (user.displayName ?: user.label).take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName ?: "Google Account",
                        style = MaterialTheme.typography.titleSmall,
                        color = glass.colors.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = user.email ?: user.uid,
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.colors.onSecondary,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "1. Name Your Ledger",
                style = MaterialTheme.typography.titleSmall,
                color = glass.colors.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            GlassTextField(
                value = ledgerName,
                onValueChange = { ledgerName = it },
                label = "Ledger Name",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ledger_name_input"),
            )

            val suggestions = listOf("Personal Expenses", "Family Ledger", "Home & Utilities", "Shared Flat")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                suggestions.forEach { suggestion ->
                    val isSelected = ledgerName == suggestion
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) glass.colors.accentFill.copy(alpha = 0.3f)
                                else glass.colors.accentFill.copy(alpha = 0.08f),
                            )
                            .border(
                                1.dp,
                                if (isSelected) glass.colors.accentFill else glass.colors.accentFill.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { ledgerName = suggestion }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) glass.colors.onPrimary else glass.colors.onSecondary,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "2. Currency & Numbering",
                style = MaterialTheme.typography.titleSmall,
                color = glass.colors.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "₹",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = glass.colors.credit,
                    )
                    Column {
                        Text(
                            text = "Indian Rupee (INR)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = glass.colors.onPrimary,
                        )
                        Text(
                            text = "Formatted with Lakh / Crore notation (e.g. ₹1,25,000)",
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.colors.onSecondary,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "3. Starter Categories (${SYSTEM_CATEGORIES.size})",
                style = MaterialTheme.typography.titleSmall,
                color = glass.colors.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SYSTEM_CATEGORIES.take(8).forEach { cat ->
                    val iconRes = categoryIcon(cat.iconKey)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(glass.colors.accentFill.copy(alpha = 0.1f))
                            .border(
                                1.dp,
                                glass.colors.accentFill.copy(alpha = 0.25f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = cat.name,
                            tint = glass.colors.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.colors.onPrimary,
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.colors.expenseAccent,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = glass.colors.accentFill)
            }
        } else {
            GlassButton(
                text = "Complete Setup & Start",
                onClick = { onComplete(ledgerName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("complete_setup_button"),
            )
            GlassButton(
                text = "Sign out",
                onClick = onSignOut,
                variant = GlassButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FeatureRow(
    iconRes: Int,
    title: String,
    description: String,
) {
    val glass = currentGlassTheme()

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(glass.colors.accentFill.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = glass.colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = glass.colors.onPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = glass.colors.onSecondary,
            )
        }
    }
}
