package com.srinidhi.kharch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.srinidhi.kharch.ui.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host. Edge-to-edge is mandatory for targetSdk 35+ (§11.3).
 *
 * This activity is deliberately nearly empty. It exists to host the composition and
 * guarantee `enableEdgeToEdge()` runs before anything draws; everything else lives in
 * [KharchRoot], which is a fixed seam shared by the feature agents. Editing this class
 * is almost always the wrong change — edit KharchRoot instead.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KharchRoot(nav = { AppNavHost() })
        }
    }
}
