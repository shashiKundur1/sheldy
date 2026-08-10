package com.shashi.shield

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.shashi.shield.Brand.S2
import com.shashi.shield.Brand.S3
import com.shashi.shield.Brand.S4
import com.shashi.shield.Brand.S6
import com.shashi.shield.Brand.S8
import com.shashi.shield.Brand.col
import com.shashi.shield.Brand.dp
import com.shashi.shield.Brand.eyebrow
import com.shashi.shield.Brand.gap

/**
 * Concentric breathing rings. Four seconds expanding, four contracting, box-breathing pace,
 * slow enough to actually drag the heart rate down rather than just look calm.
 *
 * Drawn rather than animated with scale, so the stroke width stays constant as the rings grow.
 * A scaled circle thickens its own outline, which reads as a wobble at this speed.
 */
class BreathRing(ctx: Context) : View(ctx) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Brand.INK
        strokeWidth = dp(ctx, 1.2f).toFloat()
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f            // 0 fully contracted, 1 fully expanded

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        // Breathing is not linear: the turn at each end is slower than the middle.
        interpolator = PathInterpolator(0.45f, 0f, 0.55f, 1f)
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(width, height) / 2f - dp(context, 6f)

        // soft centre glow that breathes with the rings
        val glowR = maxR * (0.35f + phase * 0.30f)
        glow.shader = RadialGradient(
            cx, cy, glowR,
            intArrayOf(0x1FFFFFFF, 0x0AFFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowR, glow)

        // four rings, each lagging the one inside it so the expansion reads as a pulse travelling
        for (i in 0 until 4) {
            val lag = i * 0.09f
            val p = (phase - lag).coerceIn(0f, 1f)
            val r = maxR * (0.34f + 0.16f * i + p * 0.13f)
            if (r > maxR) continue
            ring.alpha = (((1f - i * 0.2f) * (0.30f + 0.55f * p)) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r, ring)
        }
    }
}

/**
 * The block and panic screen. The 90 seconds is the working part, not the blocking, the urge
 * peaks and falls inside that window. It never scolds (theme rule 8): one line, something to
 * breathe with, and a count.
 *
 * Built as a plain View so it serves both the Activity (panic, preview) and the accessibility
 * overlay, which is the real block path since Android 14+ denies background activity launches.
 */
fun buildBlockView(ctx: Context, reason: String, onDone: () -> Unit): View {
    val root = FrameLayout(ctx).apply { setBackgroundColor(Brand.BLACK) }

    root.addView(ImageView(ctx).apply {
        setImageResource(R.drawable.hero_block)
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0.32f
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    })
    // Vignette: pull the eye to the centre and keep the type off the busy water.
    root.addView(View(ctx).apply {
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xF7000000.toInt(), 0xB3000000.toInt(), 0xF7000000.toInt())
        )
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    })

    val ringSize = dp(ctx, 300f)
    root.addView(BreathRing(ctx).apply {
        layoutParams = FrameLayout.LayoutParams(ringSize, ringSize)
            .apply { gravity = Gravity.CENTER }
    })

    val wrap = col(ctx).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(ctx, S8), dp(ctx, S8), dp(ctx, S8), dp(ctx, S6))
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        isClickable = true; isFocusable = true      // swallow taps to whatever is underneath
    }

    wrap.addView(eyebrow(ctx, "Sheldy").apply { gravity = Gravity.CENTER })
    wrap.addView(gap(ctx, S4))
    wrap.addView(Brand.display(ctx, "Not this.", 40f).apply { gravity = Gravity.CENTER })

    // spacer that pushes the counter into the ring
    wrap.addView(View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, 0, 1f)
    })

    val count = Brand.hero(ctx, "90", 96f).apply { gravity = Gravity.CENTER }
    count.layoutParams = Brand.fill()
    wrap.addView(count)
    wrap.addView(gap(ctx, S2))
    val breathLabel = eyebrow(ctx, "breathe in", Brand.INK_45).apply { gravity = Gravity.CENTER }
    wrap.addView(breathLabel)

    wrap.addView(View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, 0, 1f)
    })

    wrap.addView(Brand.body(ctx, Thoughts.today(), Brand.T_BODY, Brand.INK_60).apply {
        gravity = Gravity.CENTER
        setLineSpacing(dp(ctx, 6f).toFloat(), 1f)
    })
    wrap.addView(gap(ctx, S6))

    fun action(label: String, filled: Boolean, onTap: () -> Unit) =
        Brand.title(ctx, label, Brand.T_BODY, if (filled) Brand.BLACK else Brand.INK).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(ctx, Brand.R_TILE,
                if (filled) Brand.INK else 0x00000000,
                if (filled) Brand.INK else Brand.HAIRLINE)
            setPadding(0, dp(ctx, S4), 0, dp(ctx, S4))
            layoutParams = Brand.fill().apply { bottomMargin = dp(ctx, S2) }
            minHeight = dp(ctx, 52f)
            isClickable = true
            Anim.pressable(this)
            setOnClickListener { onTap() }
        }

    wrap.addView(action("Talk it through", true) {
        ctx.startActivity(Intent(ctx, ChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    })

    val done = action("I'm through it", false) { onDone() }.apply {
        visibility = View.GONE
    }
    wrap.addView(done)

    if (reason.isNotEmpty()) {
        wrap.addView(gap(ctx, S3))
        wrap.addView(eyebrow(ctx, reason, Brand.INK_30).apply { gravity = Gravity.CENTER })
    }

    root.addView(wrap)

    object : CountDownTimer(90_000, 1_000) {
        override fun onTick(remaining: Long) {
            val left = (remaining / 1000).toInt()
            count.text = left.toString()
            // 4s in, 4s out, matched to the ring animation.
            breathLabel.text = if ((left / 4) % 2 == 0) "BREATHE IN" else "BREATHE OUT"
        }
        override fun onFinish() {
            count.text = "0"
            breathLabel.text = "IT PASSED"
            done.visibility = View.VISIBLE
            Anim.enter(done)
        }
    }.start()

    Anim.enter(wrap, 60L)
    return root
}

class BlockActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK
        val v = buildBlockView(this, intent.getStringExtra("reason") ?: "") { finish() }
        // Without this the gesture bar clips the bottom of the screen, the reason line, and
        // then the "I'm through it" button once the countdown ends, which is the one control
        // that has to be reachable.
        v.setOnApplyWindowInsetsListener { view, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        setContentView(v)
    }

    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() { /* no escape while the countdown runs */ }
}

/**
 * The thin progress line under the lock's clock. Hollow track, filled run, the same language as
 * every other "not yet / held" pair in the app, just laid flat.
 */
class LockBar(ctx: Context) : View(ctx) {

    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.HAIRLINE }
    private val run = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.INK }

    override fun onDraw(canvas: Canvas) {
        // Square ends, not a pill. Rule 4: this app is boxy, and a lozenge here would be the
        // only rounded thing on the screen.
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, width.toFloat(), h, track)
        if (progress > 0f) canvas.drawRect(0f, 0f, width * progress, h, run)
    }
}

/**
 * Slide to start, rather than tap to start.
 *
 * Lifted from Blockit, and it is the right control for this: starting a lock is not undoable, and
 * a tap target is something you can hit by accident with the phone in one hand. A deliberate drag
 * across the whole width cannot happen by accident, and the distance itself is a moment to change
 * your mind.
 */
class SlideToStart(
    ctx: Context,
    private var label: String,
    private val onDone: () -> Unit
) : View(ctx) {

    private var knob = 0f          // 0..1 across the travel
    private var dragging = false
    private var down = 0f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.SURFACE }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Brand.HAIRLINE
        strokeWidth = dp(ctx, 1f).toFloat()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.INK }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.BLACK }
    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Brand.INK_45
        textAlign = Paint.Align.CENTER
        textSize = dp(ctx, 12f).toFloat()
        letterSpacing = 0.16f
        typeface = Brand.medium(ctx)
    }

    fun setLabel(s: String) { label = s; invalidate() }

    private fun knobSize() = height - dp(context, 8f)
    private fun travel() = width - knobSize() - dp(context, 8f)

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                // Anywhere on the left half starts a drag; demanding a hit on the knob itself
                // makes this fiddly one-handed for no benefit.
                dragging = e.x < width * 0.6f
                down = e.x
                parent?.requestDisallowInterceptTouchEvent(true)
                return dragging
            }
            android.view.MotionEvent.ACTION_MOVE -> if (dragging) {
                knob = ((e.x - down) / travel()).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragging) {
                    dragging = false
                    if (knob > 0.82f) { knob = 1f; invalidate(); onDone() }
                    else {
                        // Spring back, so a half-hearted drag visibly refuses rather than sticking.
                        ValueAnimator.ofFloat(knob, 0f).apply {
                            duration = 220
                            addUpdateListener { knob = it.animatedValue as Float; invalidate() }
                        }.start()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(e)
    }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(context, 4f).toFloat()
        // Card radius, not a pill. Borrowed the idea of sliding to start, not the shape.
        val r = dp(context, Brand.R_CARD).toFloat()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, track)
        canvas.drawRoundRect(pad / 2, pad / 2, width - pad / 2, height - pad / 2, r, r, edge)

        // The label fades out as the knob crosses it, so the two never fight for the same pixels.
        words.alpha = (255 * (1f - knob * 1.4f)).toInt().coerceIn(0, 255)
        canvas.drawText(label, width / 2f, height / 2f + words.textSize / 2.8f, words)

        val k = knobSize().toFloat()
        val left = pad + travel() * knob
        val kr = dp(context, Brand.R_TILE).toFloat()
        canvas.drawRoundRect(left, pad, left + k, pad + k, kr, kr, fill)

        // A play triangle, drawn rather than set as text so it stays optically centred.
        val cx = left + k / 2f
        val cy = pad + k / 2f
        val s = k * 0.24f
        val path = android.graphics.Path().apply {
            moveTo(cx - s * 0.7f, cy - s)
            lineTo(cx + s, cy)
            lineTo(cx - s * 0.7f, cy + s)
            close()
        }
        canvas.drawPath(path, glyph)
    }
}

/**
 * The screen you get when you open a locked app.
 *
 * Android's own answer is a grey dialog saying the app is paused. It tells you nothing you need:
 * not how long is left, not how to get out in a real emergency, and not what put it there. This
 * replaces it with the same clock the lock page shows, and the two ways out underneath.
 *
 * No dismiss control. It goes when the lock goes, or when you use one of the ways out.
 */
fun buildLockView(ctx: Context, onGone: () -> Unit): View {
    val root = FrameLayout(ctx).apply { setBackgroundColor(Brand.BLACK) }

    val page = col(ctx).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(ctx, S6), dp(ctx, S8), dp(ctx, S6), dp(ctx, S8))
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }
    }

    page.addView(eyebrow(ctx, "Locked").apply { gravity = Gravity.CENTER })
    page.addView(gap(ctx, S4))

    val clock = Brand.mono(ctx, "0:00:00", 56f, Brand.INK).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
    }
    page.addView(clock)
    page.addView(gap(ctx, S3))

    val ends = Brand.body(ctx, "", Brand.T_CAPTION, Brand.INK_45).apply {
        gravity = Gravity.CENTER
    }
    page.addView(ends)
    page.addView(gap(ctx, S3))
    page.addView(Brand.body(ctx,
        "Calls and messages still work.", Brand.T_CAPTION, Brand.INK_30).apply {
        gravity = Gravity.CENTER
    })
    page.addView(gap(ctx, S8))

    fun way(label: String, mode: String) {
        page.addView(Brand.title(ctx, label, Brand.T_BODY, Brand.INK_60).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(ctx, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
            setPadding(dp(ctx, Brand.S5), dp(ctx, S4), dp(ctx, Brand.S5), dp(ctx, S4))
            minHeight = dp(ctx, Brand.MIN_TAP)
            layoutParams = Brand.fill().apply { bottomMargin = dp(ctx, S3) }
            isClickable = true
            Anim.pressable(this)
            setOnClickListener {
                onGone()
                runCatching {
                    ctx.startActivity(Intent(ctx, LockActivity::class.java)
                        .putExtra("mode", mode)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        })
    }
    way("Emergency, five minutes", "emergency")
    way("Unlock with a reason", "reason")

    root.addView(page)

    // The clock ticks itself and takes the overlay down the moment the lock ends, so nothing is
    // left covering the screen if the window closes while you are looking at it.
    val ui = android.os.Handler(android.os.Looper.getMainLooper())
    val tick = object : Runnable {
        override fun run() {
            if (!Lock.active(ctx)) { onGone(); return }
            val end = when {
                Lock.focusActive(ctx) -> Lock.focusUntil(ctx)
                else -> Lock.sleepEndsAt(ctx)
            }
            val left = ((end - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            clock.text = "%d:%02d:%02d".format(left / 3600, (left / 60) % 60, left % 60)
            ends.text = "Ends at " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK)
                .format(java.util.Date(end))
            ui.postDelayed(this, 1000)
        }
    }
    root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { ui.post(tick) }
        override fun onViewDetachedFromWindow(v: View) { ui.removeCallbacks(tick) }
    })
    return root
}
