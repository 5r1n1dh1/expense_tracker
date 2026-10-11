package com.srinidhi.kharch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.srinidhi.kharch.ui.BuildInfo

@Composable
fun KharchApp() {
    Text(text = "Kharch ${BuildInfo.VERSION_NAME}")
}