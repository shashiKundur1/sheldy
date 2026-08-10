package com.shashi.shield

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Calendar

/**
 * The accountability partner.
 *
 * This is the single highest-leverage feature in the whole app and it is barely any code. Free
 * blockers fail because they are self-locked: at 1am you are not the person who installed it, and
 * anything you can undo alone, you will. Paid ones work largely because somebody else is watching.
 *
 * Deliberately *not* automatic background sending. Silently texting someone from a phone would
 * need SEND_SMS, and a blocker that can message people without the user seeing it is a
 * surveillance tool. Instead it composes the message and hands it to the messaging app, so
 * nothing leaves the phone without an explicit tap.
 */
object Partner {

    private const val K_NAME = "partner_name"
    private const val K_NUMBER = "partner_number"
    private const val K_ON_RELAPSE = "partner_on_relapse"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)

    fun name(ctx: Context): String = sp(ctx).getString(K_NAME, "") ?: ""
    fun number(ctx: Context): String = sp(ctx).getString(K_NUMBER, "") ?: ""
    fun configured(ctx: Context) = number(ctx).isNotBlank()

    fun notifyOnRelapse(ctx: Context) = sp(ctx).getBoolean(K_ON_RELAPSE, true)
    fun setNotifyOnRelapse(ctx: Context, on: Boolean) =
        sp(ctx).edit().putBoolean(K_ON_RELAPSE, on).apply()

    fun save(ctx: Context, name: String, number: String) =
        sp(ctx).edit()
            .putString(K_NAME, name.trim())
            .putString(K_NUMBER, number.trim().filter { it.isDigit() || it == '+' })
            .apply()

    fun clear(ctx: Context) =
        sp(ctx).edit().remove(K_NAME).remove(K_NUMBER).apply()

    /** Weekly summary. Honest numbers, no spin, a report you would not want to send is the point. */
    fun weeklyMessage(ctx: Context): String {
        val streak = Store.streak(ctx)
        val best = Store.bestStreak(ctx)
        val relapses = Store.relapseTotal(ctx)
        val urges = Store.panics(ctx)
        return buildString {
            append("Sheldy check-in.\n\n")
            append("Current streak: $streak days\n")
            append("Best so far: $best days\n")
            append("Relapses logged: $relapses\n")
            append("Urges I sat through: $urges\n\n")
            append(if (streak == 0) "Starting again today." else "Still going.")
        }
    }

    fun relapseMessage(ctx: Context, trigger: String): String = buildString {
        append("Sheldy: I relapsed and I'm telling you rather than hiding it.\n")
        if (trigger.isNotBlank()) append("Trigger: $trigger\n")
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        append("Time: ${"%02d".format(h)}:00\n")
        append("\nCounter is back to zero. Not asking for anything, just not keeping it secret.")
    }

    /**
     * Opens the messaging app with the text prefilled. Nothing sends without the user pressing
     * send, which is the correct trade: a blocker must never be able to message people silently.
     */
    fun compose(ctx: Context, text: String) {
        val num = number(ctx)
        val intent = if (num.isBlank()) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        } else {
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).apply {
                putExtra("sms_body", text)
            }
        }
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * Scheduled lockdown: a nightly window where the filter stops being polite.
 *
 * Outside the window the keyword watchdog behaves normally. Inside it, the hours the relapse
 * data keeps pointing at, the browser itself is hidden, so there is no address bar to type into
 * in the first place. Blocking a search is reactive; removing the browser for four hours is not.
 */
object Schedule {

    private const val K_ON = "schedule_on"
    private const val K_FROM = "schedule_from"
    private const val K_TO = "schedule_to"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)

    // OFF by default, and it was a mistake to ship it on.
    //
    // Hiding Chrome does not just remove an address bar. Chrome is the Custom Tabs provider for
    // every other app on the phone, so suspending it silently breaks every in-app login flow,
    // LinkedIn's challenge screen falls back to a bare WebView, which LinkedIn classifies as
    // "Operating system: Unknown, Browser: Unknown" and refuses. Banking and OAuth flows degrade
    // the same way. That is enormous collateral damage for six hours of friction, and it fires at
    // exactly the hours someone is most likely to be doing something ordinary on their phone.
    fun enabled(ctx: Context) = sp(ctx).getBoolean(K_ON, false)
    fun setEnabled(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean(K_ON, on).apply()
        if (!on) runCatching { Lockdown.setBrowserBlackout(ctx, false) }
    }

    /** Defaults to 23:00-05:00, the window nearly every relapse log lands in. */
    fun from(ctx: Context) = sp(ctx).getInt(K_FROM, 23)
    fun to(ctx: Context) = sp(ctx).getInt(K_TO, 5)

    fun setWindow(ctx: Context, from: Int, to: Int) =
        sp(ctx).edit().putInt(K_FROM, from.coerceIn(0, 23)).putInt(K_TO, to.coerceIn(0, 23)).apply()

    /** True when the clock is inside the window. Handles windows that wrap past midnight. */
    fun active(ctx: Context): Boolean {
        if (!enabled(ctx)) return false
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val f = from(ctx); val t = to(ctx)
        return if (f <= t) h in f until t else (h >= f || h < t)
    }

    fun describe(ctx: Context) =
        "%02d:00 - %02d:00".format(from(ctx), to(ctx))
}
