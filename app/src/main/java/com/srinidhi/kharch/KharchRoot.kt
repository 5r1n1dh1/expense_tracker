package com.srinidhi.kharch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.srinidhi.kharch.ui.BuildInfo
import com.srinidhi.kharch.ui.motion.ProvideHaptics
import com.srinidhi.kharch.ui.theme.KharchTheme

/**
 * The app shell.
 */
@Composable
fun KharchRoot(
    nav: @Composable () -> Unit = { Text(text = "Kharch ${BuildInfo.VERSION_NAME}") },
    hapticsEnabled: Boolean = true,
) {
    KharchTheme {
        ProvideHaptics(enabled = hapticsEnabled) {
            nav()
        }
    }
}
