package com.shashi.shield

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
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

/**
 * Finger signature pad.
 *
 * The research kept turning this up as the one onboarding step that measurably raises follow
 * through. QUITTR's "sign to commit" screen. It is not decoration: physically drawing your name
 * converts a vague intention into an act you performed, and acts are much harder to argue away at
 * 1am than intentions are.
 */
class SignatureView(ctx: Context) : View(ctx) {

    private val path = Path()
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Brand.INK
        strokeWidth = ctx.resources.displayMetrics.density * 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val guide = Paint().apply { color = Brand.HAIRLINE; strokeWidth = 2f }

    var signed = false
        private set

    private var lastX = 0f
    private var lastY = 0f

    override fun onDraw(canvas: Canvas) {
        val y = height * 0.78f
        canvas.drawLine(width * 0.08f, y, width * 0.92f, y, guide)
        canvas.drawPath(path, ink)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(e.x, e.y); lastX = e.x; lastY = e.y
                signed = true
                parent?.requestDisallowInterceptTouchEvent(true)   // do not let the ScrollView steal it
            }
            MotionEvent.ACTION_MOVE -> {
                // quadratic smoothing, or a fast stroke renders as visible straight segments
                path.quadTo(lastX, lastY, (e.x + lastX) / 2f, (e.y + lastY) / 2f)
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        invalidate()
        return true
    }

    fun clear() {
        path.reset(); signed = false; invalidate()
    }
}

class PledgeActivity : Activity() {

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK

        val p = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@PledgeActivity, S6), dp(this@PledgeActivity, 88f),
                dp(this@PledgeActivity, S6), dp(this@PledgeActivity, S8))
            minimumHeight = dp(this@PledgeActivity, Brand.MIN_PAGE)
        }

        p.addView(eyebrow(this, "The commitment"))
        p.addView(gap(this, S3))
        p.addView(Brand.display(this, "Sign it."))
        p.addView(gap(this, S4))
        p.addView(body(this,
            "I am not doing this because I think I am broken. I am doing it because this has been " +
            "taking hours and attention I would rather spend elsewhere, and I have decided that is " +
            "no longer a trade I want to make.\n\n" +
            "When the urge comes I will wait ninety seconds before doing anything. If I relapse I " +
            "will log it without contempt for myself, and carry on the same day.",
            Brand.T_BODY, Brand.INK_80))
        p.addView(gap(this, S6))

        val pad = SignatureView(this).apply {
            background = Brand.surface(this@PledgeActivity, Brand.R_TILE, Brand.SURFACE)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(this@PledgeActivity, 190f))
        }
        p.addView(pad)
        p.addView(gap(this, S2))
        p.addView(eyebrow(this, "Draw your name above").apply { gravity = Gravity.CENTER })
        p.addView(gap(this, S6))

        val status = body(this, "", Brand.T_CAPTION, Brand.INK_45).apply {
            gravity = Gravity.CENTER
        }

        p.addView(Brand.title(this, "I commit", 17f, Brand.BLACK).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(this@PledgeActivity, Brand.R_TILE, Brand.INK, Brand.INK)
            setPadding(0, dp(this@PledgeActivity, 20f), 0, dp(this@PledgeActivity, 20f))
            layoutParams = Brand.fill()
            minHeight = dp(this@PledgeActivity, 64f)
            isClickable = true
            Anim.pressable(this)
            setOnClickListener {
                if (!pad.signed) {
                    status.text = "Sign it first. That is the point."
                } else {
                    Store.signPledge(this@PledgeActivity)
                    Store.addNote(this@PledgeActivity, "Signed the commitment.", "pledge")
                    finish()
                }
            }
        })
        p.addView(gap(this, S3))
        p.addView(Brand.title(this, "Clear", Brand.T_BODY, Brand.INK_60).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(this@PledgeActivity, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
            setPadding(0, dp(this@PledgeActivity, S4), 0, dp(this@PledgeActivity, S4))
            layoutParams = Brand.fill()
            minHeight = dp(this@PledgeActivity, 48f)
            isClickable = true
            setOnClickListener { pad.clear(); status.text = "" }
        })
        p.addView(gap(this, S3))
        p.addView(status)

        setContentView(ScrollView(this).apply { setBackgroundColor(Brand.BLACK); addView(p) }.also { sv ->
            sv.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        })
        Anim.enter(p)
    }
}
