package com.shashi.shield

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home screen widget: today's thought, the day count, and a tap target that goes straight to the
 * panic screen.
 *
 * This is the closest thing Android still allows to the lock screen widget that was asked for,
 * real lock screen widgets were removed after Android 4 and never returned for phones. The three
 * daily reminders cover the lock screen instead, with VISIBILITY_PUBLIC so the thought is
 * readable without unlocking.
 *
 * Refreshed hourly by the system, and pushed immediately whenever the day state changes so the
 * counter never lags what the app shows.
 */
class ThoughtWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val streak = Store.streak(ctx)
        val views = RemoteViews(ctx.packageName, R.layout.widget_thought).apply {
            setTextViewText(R.id.widget_day, "DAY $streak")
            setTextViewText(R.id.widget_thought, Thoughts.today())
            setTextViewText(
                R.id.widget_action,
                if (Store.needsConfirmation(ctx)) "YESTERDAY IS STILL OPEN"
                else "TAP FOR PANIC"
            )
            // Whole widget is the tap target, at 1am you should not have to aim.
            setOnClickPendingIntent(
                R.id.widget_thought,
                PendingIntent.getActivity(
                    ctx, 0,
                    Intent(ctx, BlockActivity::class.java)
                        .putExtra("reason", "opened from the widget")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            setOnClickPendingIntent(
                R.id.widget_eyebrow,
                PendingIntent.getActivity(
                    ctx, 1,
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        mgr.updateAppWidget(id, views)
    }

    companion object {
        /** Call after anything that changes the day count, so the widget never lags the app. */
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ThoughtWidget::class.java))
            if (ids.isEmpty()) return
            ctx.sendBroadcast(
                Intent(ctx, ThoughtWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
