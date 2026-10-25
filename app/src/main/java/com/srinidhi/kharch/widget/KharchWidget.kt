package com.srinidhi.kharch.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as ComposeAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

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
            .padding(16),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Kharch",
                style = TextStyle(
                    color = ColorProvider(Color.Black.value.toLong()),
                ),
            )
            Text(
                text = "Tap + to add",
                style = TextStyle(
                    color = ColorProvider(Color.DarkGray.value.toLong()),
                ),
            )
        }
    }
}
