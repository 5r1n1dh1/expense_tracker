package com.srinidhi.kharch.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.Color
import androidx.glance.unit.dp

class KharchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: android.content.Context,
        id: GlanceId,
    ) {
        provideContent {
            WidgetContent()
        }
    }
}

@Composable
private fun WidgetContent() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Horizontal) {
            Text(
                text = "Kharch",
                style = androidx.glance.text.TextStyle(
                    color = Color.Black,
                ),
            )
            Text(
                text = "Tap + to add",
                style = androidx.glance.text.TextStyle(
                    color = Color.DarkGray,
                ),
            )
        }
    }
}
