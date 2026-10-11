package com.srinidhi.kharch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.srinidhi.kharch.ui.theme.KharchTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host. Edge-to-edge is mandatory for targetSdk 35+ (§11.3).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KharchTheme {
                KharchRoot()
            }
        }
    }
}