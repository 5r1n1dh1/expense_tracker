package com.srinidhi.kharch.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver for [KharchWidget]. Declared in AndroidManifest.xml with the
 * APPWIDGET_UPDATE intent-filter and kharch_widget_info provider meta.
 * Without it the widget cannot be placed on a home screen and Glance
 * updates are silently dropped.
 */
class GlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: KharchWidget = KharchWidget()
}
