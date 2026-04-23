package com.htmlwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val mgr = AppWidgetManager.getInstance(ctx)
        listOf(
            HtmlWidgetProvider::class.java,
            HtmlWidgetProvider1x2::class.java,
            HtmlWidgetProvider2x2::class.java,
            HtmlWidgetProvider1x4::class.java
        ).forEach { cls ->
            mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { id ->
                HtmlWidgetProvider.update(ctx, mgr, id)
                HtmlWidgetProvider.scheduleAlarm(ctx, id)
            }
        }
    }
}
