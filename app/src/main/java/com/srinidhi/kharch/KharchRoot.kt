package com.srinidhi.kharch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.srinidhi.kharch.ui.BuildInfo

/**
 * Root composable. Named `KharchRoot` (not `KharchApp`) to avoid colliding with
 * the Hilt @HiltAndroidApp Application class `KharchApp` when both are imported
 * into MainActivity.
 */
@Composable
fun KharchRoot() {
    Text(text = "Kharch ${BuildInfo.VERSION_NAME}")
}
