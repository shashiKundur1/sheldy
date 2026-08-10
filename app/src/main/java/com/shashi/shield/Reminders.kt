package com.shashi.shield

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Three check-in reminders a day: 09:00, 17:00, 21:00.
 *
 * The times are not arbitrary. Morning sets the intention, late afternoon catches the post-work
 * dip, and the 21:00 one is the important one, it lands before the window where relapse
 * actually happens, and it is the prompt that turns yesterday into a counted day.
 *
 * Notifications also appear on the lock screen, which is the closest thing Android still offers
 * to a lock screen widget: real lock screen widgets were removed after Android 4 and never came
 * back for phones.
 */
object Reminders {

    const val CHANNEL = "sheldy_checkin"
    private val HOURS = listOf(9, 17, 21)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Check-in reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = "Morning, evening and night check-ins."
                    enableVibration(true)
                    setShowBadge(true)
                }
        )
    }

    fun schedule(ctx: Context) {
        ensureChannel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        HOURS.forEachIndexed { i, hour ->
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            // Inexact repeating: it survives Doze, needs no special permission, and a check-in
            // reminder does not care about being a few minutes late.
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP, next.timeInMillis,
                AlarmManager.INTERVAL_DAY, pending(ctx, i, hour)
            )
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        HOURS.forEachIndexed { i, hour -> am.cancel(pending(ctx, i, hour)) }
        ctx.getSystemService(NotificationManager::class.java)?.cancelAll()
    }

    fun apply(ctx: Context) = if (Store.remindersOn(ctx)) schedule(ctx) else cancel(ctx)

    private fun pending(ctx: Context, id: Int, hour: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 1000 + id,
            Intent(ctx, ReminderReceiver::class.java).putExtra("hour", hour),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        // Re-arm after a reboot, then fall through. BOOT_COMPLETED carries no hour.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.apply(ctx)
            // Re-assert the lock too. Suspension is a device-policy fact so it survives the
            // reboot on its own, the danger is the opposite one, a lock that ended while the
            // phone was off and would otherwise stay applied forever because nothing was running
            // to lift it. Both directions are just apply(), which reads the clock and settles.
            runCatching { Lock.apply(ctx) }
            return
        }
        if (!Store.remindersOn(ctx)) return

        val hour = intent.getIntExtra("hour", 21)
        val streak = Store.streak(ctx)
        val pendingYesterday = Store.needsConfirmation(ctx)

        val (title, text) = when {
            pendingYesterday ->
                "Yesterday is still open" to "Confirm it and it becomes a counted day."
            hour == 9 ->
                "Day ${streak + 1} starts now" to Thoughts.today()
            hour == 17 ->
                "Checking in" to "The dip is normal. It is not a decision."
            else ->
                "Before the hard hours" to Thoughts.today()
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Reminders.ensureChannel(ctx)
        val n = Notification.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            // Visible on the lock screen, which is where it needs to be seen.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        ctx.getSystemService(NotificationManager::class.java)?.notify(2000 + hour, n)
    }
}
