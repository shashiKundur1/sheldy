package com.shashi.shield

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import com.shashi.shield.Brand.row
import com.shashi.shield.Brand.title

/**
 * The three screens the lock needs, in one activity because they are one idea: deciding what gets
 * through the lock and what it costs you to open it.
 *
 *   apps       pick up to three apps that stay usable
 *   emergency  five minutes, three times, reason required
 *   reason     write why you need the phone; the length of the unlock follows the reason
 */
class LockActivity : Activity() {

    private lateinit var mode: String

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK
        mode = intent.getStringExtra("mode") ?: "apps"

        val page = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@LockActivity, S6), dp(this@LockActivity, 72f),
                dp(this@LockActivity, S6), dp(this@LockActivity, S8))
            minimumHeight = dp(this@LockActivity, Brand.MIN_PAGE)
        }

        when (mode) {
            "apps" -> buildApps(page)
            "emergency" -> buildGrant(page, emergency = true)
            else -> buildGrant(page, emergency = false)
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Brand.BLACK)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(page)
            setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(0, b.top, 0, b.bottom)
                insets
            }
        })
        Anim.enter(page)
    }

    // ---- pick the three apps ----------------------------------------------

    private fun buildApps(p: LinearLayout) {
        val chosen = Lock.allowed(this).toMutableList()

        p.addView(eyebrow(this, "Allowed during a lock"))
        p.addView(gap(this, S3))
        p.addView(Brand.display(this, "Three apps.", 32f))
        p.addView(gap(this, S3))
        p.addView(body(this,
            "Calls and messages always work, they are never suspended, lock or no lock. " +
            "These three are on top of that.",
            Brand.T_BODY, Brand.INK_60))
        p.addView(gap(this, S6))

        val counter = body(this, "", Brand.T_CAPTION, Brand.INK_45)
        p.addView(counter)
        p.addView(gap(this, S4))

        fun paintCounter() { counter.text = "${chosen.size} of 3 chosen" }
        paintCounter()

        val pm = packageManager
        val apps = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
            ).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != packageName }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())

        val list = col(this)
        p.addView(list)

        apps.forEach { (pkg, label) ->
            val rowView = row(this).apply {
                setPadding(0, dp(this@LockActivity, S3), 0, dp(this@LockActivity, S3))
                minimumHeight = dp(this@LockActivity, Brand.MIN_ROW)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                Anim.pressable(this)
            }
            val mark = title(this, "", Brand.T_BODY, Brand.INK).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    dp(this@LockActivity, 26f), dp(this@LockActivity, 26f))
            }
            fun paint() {
                val on = pkg in chosen
                mark.text = if (on) "✓" else ""
                mark.background = Brand.surface(this@LockActivity, Brand.R_TILE,
                    if (on) Brand.INK else 0x00000000, if (on) Brand.INK else Brand.HAIRLINE)
                mark.setTextColor(if (on) Brand.BLACK else Brand.INK_30)
            }
            paint()

            runCatching { pm.getApplicationIcon(pkg) }.getOrNull()?.let { icon ->
                rowView.addView(ImageView(this).apply {
                    setImageDrawable(icon)
                    // Monochrome, like everything else. A grid of colour app icons would be the
                    // one place in the app that breaks its own rule.
                    colorFilter = android.graphics.ColorMatrixColorFilter(
                        android.graphics.ColorMatrix().apply { setSaturation(0f) })
                    layoutParams = LinearLayout.LayoutParams(
                        dp(this@LockActivity, 26f), dp(this@LockActivity, 26f))
                        .apply { marginEnd = dp(this@LockActivity, S3) }
                })
            }
            rowView.addView(title(this, label, Brand.T_BODY, Brand.INK)
                .apply { layoutParams = Brand.grow() })
            rowView.addView(mark)
            rowView.setOnClickListener {
                if (pkg in chosen) chosen.remove(pkg)
                else if (chosen.size < 3) chosen.add(pkg)
                paint(); paintCounter()
            }
            list.addView(rowView)
            list.addView(Brand.rule(this))
        }

        p.addView(gap(this, S6))
        p.addView(filled("Save") { Lock.setAllowed(this, chosen); finish() })
        p.addView(gap(this, S3))
        p.addView(ghost("Cancel") { finish() })
    }

    // ---- emergency, and the reasoned unlock -------------------------------

    private fun buildGrant(p: LinearLayout, emergency: Boolean) {
        val left = Lock.emergenciesLeft(this)

        p.addView(eyebrow(this, if (emergency) "Emergency" else "Unlock with a reason"))
        p.addView(gap(this, S3))
        p.addView(Brand.display(this,
            if (emergency) "Five minutes." else "Why now?", 32f))
        p.addView(gap(this, S3))
        p.addView(body(this,
            if (emergency)
                "$left of ${Lock.EMERGENCY_COUNT} left in this lock. Five minutes each, then it " +
                "closes again on its own."
            else
                "Say what you actually need the phone for. How long you get follows from that, " +
                "up to two hours, and then the lock comes back.",
            Brand.T_BODY, Brand.INK_60))
        p.addView(gap(this, S6))

        val input = EditText(this).apply {
            setHint(if (emergency) "What's happened?" else "What do you need to do?")
            setHintTextColor(Brand.INK_30)
            setTextColor(Brand.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Brand.T_BODY)
            typeface = Brand.regular(this@LockActivity)
            background = Brand.surface(this@LockActivity, Brand.R_TILE, Brand.SURFACE)
            setPadding(dp(this@LockActivity, S4), dp(this@LockActivity, S4),
                dp(this@LockActivity, S4), dp(this@LockActivity, S4))
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minHeight = dp(this@LockActivity, 112f)
            maxHeight = dp(this@LockActivity, 220f)
            gravity = Gravity.TOP or Gravity.START
            layoutParams = Brand.fill()
            // Stop at the same ceiling the ledger stores, so what you type is what gets kept.
            filters = arrayOf(android.text.InputFilter.LengthFilter(Lock.REASON_MAX_CHARS))
        }
        p.addView(input)
        p.addView(gap(this, S3))

        val status = body(this, "", Brand.T_CAPTION, Brand.INK_45)
        p.addView(status)
        p.addView(gap(this, S4))

        val action = filled(if (emergency) "Start five minutes" else "Ask for time") {}
        p.addView(action)
        p.addView(gap(this, S3))
        p.addView(ghost("Back") { finish() })

        if (emergency && left <= 0) {
            action.alpha = 0.35f
            action.isClickable = false
            status.text = "All three used. The reasoned unlock is still open to you."
            return
        }

        action.setOnClickListener {
            val reason = input.text.toString().trim()
            // A reason short enough to type without thinking is not a reason. This is the only
            // gate that matters, everything past it is just a timer.
            if (reason.length < 12) {
                status.text = "Write a bit more than that."
                return@setOnClickListener
            }
            action.isClickable = false
            action.alpha = 0.5f

            if (emergency) {
                if (Lock.useEmergency(this, reason)) {
                    status.text = "Five minutes, starting now."
                    action.postDelayed({ finish() }, 900)
                } else {
                    status.text = "No emergency sessions left in this lock."
                    action.alpha = 1f; action.isClickable = true
                }
                return@setOnClickListener
            }

            status.text = "Reading that…"
            Thread {
                val v = Lock.judge(this, reason)
                runOnUiThread {
                    val where = if (v.offline) " Judged on this phone, no connection needed." else ""
                    if (!v.allowed) {
                        Lock.log(this, reason, 0, "refused")
                        status.text = "${v.note} (genuine ${"%.2f".format(v.genuine)}, " +
                            "needs ${Lock.GENUINE_THRESHOLD})$where"
                        action.alpha = 1f; action.isClickable = true
                    } else {
                        Lock.grantMinutes(this, v.minutes, reason)
                        status.text = "${v.note} ${v.minutes} minutes.$where"
                        action.postDelayed({ finish() }, 1400)
                    }
                }
            }.start()
        }
    }

    // ---- shared bits -------------------------------------------------------

    private fun filled(label: String, onTap: () -> Unit): TextView =
        title(this, label, Brand.T_TITLE, Brand.BLACK).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(this@LockActivity, Brand.R_TILE, Brand.INK, Brand.INK)
            setPadding(0, dp(this@LockActivity, S4), 0, dp(this@LockActivity, S4))
            minHeight = dp(this@LockActivity, 56f)
            layoutParams = Brand.fill()
            isClickable = true
            Anim.pressable(this)
            setOnClickListener { onTap() }
        }

    private fun ghost(label: String, onTap: () -> Unit): TextView =
        title(this, label, Brand.T_BODY, Brand.INK_60).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(this@LockActivity, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
            setPadding(0, dp(this@LockActivity, S4), 0, dp(this@LockActivity, S4))
            minHeight = dp(this@LockActivity, Brand.MIN_TAP)
            layoutParams = Brand.fill()
            isClickable = true
            Anim.pressable(this)
            setOnClickListener { onTap() }
        }
}
