package com.srinidhi.kharch.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

/**
 * Widget entry point: translucent, edge-to-edge, glass keypad over whatever app is
 * behind it (§10). Saves straight to Room and closes in ~250 ms.
 *
 * Phase 7 replaces the placeholder body with the real quick-add flow.
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { }
    }
}