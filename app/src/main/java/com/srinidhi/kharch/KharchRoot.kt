package com.srinidhi.kharch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srinidhi.kharch.ui.BuildInfo
import com.srinidhi.kharch.ui.auth.AuthSetupScreen
import com.srinidhi.kharch.ui.auth.AuthUiState
import com.srinidhi.kharch.ui.auth.AuthViewModel
import com.srinidhi.kharch.ui.motion.ProvideHaptics
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/**
 * The app shell.
 */
@Composable
fun KharchRoot(
    nav: @Composable () -> Unit = { Text(text = "Kharch ${BuildInfo.VERSION_NAME}") },
    hapticsEnabled: Boolean = true,
) {
    com.srinidhi.kharch.ui.theme.KharchTheme {
        ProvideHaptics(enabled = hapticsEnabled) {
            KharchAuthGate(nav = nav)
        }
    }
}

/**
 * Sign-in gate: renders its [nav] content once a Google account is resolved,
 * onboarding and ledger setup otherwise.
 */
@Composable
fun KharchAuthGate(
    nav: @Composable () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val glass = currentGlassTheme()

    when (val state = uiState) {
        is AuthUiState.Authenticated -> {
            nav()
        }
        is AuthUiState.Unauthenticated, is AuthUiState.SetupNeeded -> {
            AuthSetupScreen(
                state = state,
                onSignIn = { launcher -> viewModel.startSignIn(launcher) },
                onSignInResult = { intent -> viewModel.handleSignInResult(intent) },
                onCompleteSetup = { user, name -> viewModel.completeSetup(user, name) },
                onSignOut = { viewModel.signOut() },
            )
        }
        is AuthUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = glass.colors.accentFill)
            }
        }
    }
}
