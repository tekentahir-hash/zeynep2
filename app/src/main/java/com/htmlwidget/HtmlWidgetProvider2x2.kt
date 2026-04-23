package com.htmlwidget
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class HtmlWidgetProvider2x2 : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> HtmlWidgetProvider.update(ctx, mgr, id); HtmlWidgetProvider.scheduleAlarm(ctx, id) }
    }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        super.onAppWidgetOptionsChanged(ctx, mgr, id, options)
        HtmlWidgetProvider.update(ctx, mgr, id)
    }
    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == HtmlWidgetProvider.ACTION_REFRESH) {
            val id = intent.getIntExtra(HtmlWidgetProvider.EXTRA_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID)
                HtmlWidgetProvider.update(ctx, AppWidgetManager.getInstance(ctx), id)
        }
    }
    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { id -> HtmlWidgetProvider.cancelAlarm(ctx, id); Prefs.remove(ctx, id) }
    }
}
