package com.shashi.shield

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.widget.ScrollView
import com.shashi.shield.Brand.S2
import com.shashi.shield.Brand.S3
import com.shashi.shield.Brand.S4
import com.shashi.shield.Brand.S6
import com.shashi.shield.Brand.S8
import com.shashi.shield.Brand.body
import com.shashi.shield.Brand.col
import com.shashi.shield.Brand.dp
import com.shashi.shield.Brand.eyebrow
import com.shashi.shield.Brand.gap
import com.shashi.shield.Brand.title

/**
 * Editing one day.
 *
 * Tapping a calendar cell used to mark it clean instantly, with no confirmation and no way back
 *, which meant a stray tap silently rewrote your record, and a day you actually relapsed on
 * could only ever be set to clean. Both are worse than leaving the day blank.
 *
 * Now every calendar tap lands here: it says what the day currently is, and offers all three
 * outcomes including clearing it again.
 */
class DayEditActivity : Activity() {

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK

        val key = intent.getStringExtra("dayKey") ?: Store.todayKey()
        val state = Store.rawState(this, key)
        val isToday = key == Store.todayKey()
        val canClean = Store.canConfirm(key)

        val p = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@DayEditActivity, S6), dp(this@DayEditActivity, 88f),
                dp(this@DayEditActivity, S6), dp(this@DayEditActivity, S8))
            minimumHeight = dp(this@DayEditActivity, Brand.MIN_PAGE)
        }

        p.addView(eyebrow(this, if (isToday) "Today" else "Editing a past day"))
        p.addView(gap(this, S3))
        p.addView(Brand.display(this, Store.prettyDay(key), 30f))
        p.addView(gap(this, S3))
        p.addView(body(this, when (state) {
            Day.CLEAN -> "Currently recorded as clean."
            Day.RELAPSE -> "Currently recorded as a relapse."
            else -> "Not recorded yet."
        }, Brand.T_BODY, Brand.INK_60))
        p.addView(gap(this, S8))

        fun action(label: String, sub: String, filled: Boolean, enabled: Boolean = true,
                   onTap: () -> Unit) {
            p.addView(col(this).apply {
                background = Brand.surface(this@DayEditActivity, Brand.R_TILE,
                    if (filled && enabled) Brand.INK else 0x00000000,
                    if (filled && enabled) Brand.INK else Brand.HAIRLINE)
                setPadding(dp(this@DayEditActivity, S4), dp(this@DayEditActivity, S4),
                    dp(this@DayEditActivity, S4), dp(this@DayEditActivity, S4))
                minimumHeight = dp(this@DayEditActivity, 64f)
                layoutParams = Brand.fill()
                    .apply { bottomMargin = dp(this@DayEditActivity, S3) }
                alpha = if (enabled) 1f else 0.4f
                addView(title(this@DayEditActivity, label, Brand.T_TITLE,
                    if (filled && enabled) Brand.BLACK else Brand.INK).apply {
                    gravity = Gravity.CENTER
                })
                addView(gap(this@DayEditActivity, 4f))
                addView(body(this@DayEditActivity, sub, Brand.T_CAPTION,
                    if (filled && enabled) 0x99000000.toInt() else Brand.INK_30).apply {
                    gravity = Gravity.CENTER
                })
                if (enabled) {
                    isClickable = true
                    Anim.pressable(this)
                    setOnClickListener { onTap() }
                }
            })
        }

        action(
            "Stayed clean",
            if (canClean) "Counts this day toward the streak"
            else "Today can only be confirmed after 21:00",
            filled = state != Day.CLEAN, enabled = canClean
        ) { Store.confirmClean(this, key); finish() }

        action("I relapsed", "Log the trigger. No shame, just data.", filled = false) {
            startActivity(Intent(this, RelapseActivity::class.java).putExtra("dayKey", key))
            finish()
        }

        if (state != null) {
            action("Clear this day", "Back to unrecorded", filled = false) {
                Store.clearDay(this, key); finish()
            }
        }

        p.addView(gap(this, S4))
        p.addView(title(this, "Cancel", Brand.T_BODY, Brand.INK_60).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(this@DayEditActivity, Brand.R_TILE, 0x00000000, 0x00000000)
            setPadding(0, dp(this@DayEditActivity, S4), 0, dp(this@DayEditActivity, S4))
            layoutParams = Brand.fill()
            minHeight = dp(this@DayEditActivity, 48f)
            isClickable = true
            setOnClickListener { finish() }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Brand.BLACK)
            addView(p)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        })
        Anim.enter(p)
    }
}
