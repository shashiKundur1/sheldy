package com.shashi.shield

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView

/**
 * Motion, per theme rule 7: slow, ease-out, never excited. No spring, no bounce, no celebration.
 * The app should feel like something steady sitting with you.
 *
 * Everything animates transform and alpha only, so nothing triggers layout.
 */
object Anim {

    /** Material's standard decelerate. Entering motion should arrive and settle, not overshoot. */
    private val EASE_OUT = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val EASE = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    const val ENTER = 420L
    const val QUICK = 200L

    /** Fade and rise. The default entrance for any block of content. */
    fun enter(v: View, delay: Long = 0, rise: Float = 18f) {
        v.alpha = 0f
        v.translationY = Brand.dp(v.context, rise).toFloat()
        v.animate()
            .alpha(1f).translationY(0f)
            .setDuration(ENTER).setStartDelay(delay)
            .setInterpolator(EASE_OUT)
            .start()
    }

    /**
     * Stagger a list in at 34ms apart. Material puts the useful range at 30-50ms: below that the
     * eye reads it as simultaneous, above it starts to feel slow.
     */
    fun stagger(views: List<View>, step: Long = 34L, from: Long = 0L) {
        views.forEachIndexed { i, v -> enter(v, from + i * step, 14f) }
    }

    /** Count a number up to its value. The one place motion carries information. */
    fun countTo(tv: TextView, target: Int, duration: Long = 900L) {
        if (target <= 0) { tv.text = "0"; return }
        ValueAnimator.ofInt(0, target).apply {
            this.duration = duration
            interpolator = EASE_OUT
            addUpdateListener { tv.text = it.animatedValue.toString() }
        }.start()
    }

    /** Fill a progress rail by animating its weight. */
    fun growRail(fill: View, done: Float, total: Float, duration: Long = 800L) {
        val lp = fill.layoutParams as android.widget.LinearLayout.LayoutParams
        ValueAnimator.ofFloat(0.001f, done.coerceAtLeast(0.001f)).apply {
            this.duration = duration
            interpolator = EASE_OUT
            addUpdateListener {
                lp.weight = it.animatedValue as Float
                fill.requestLayout()
            }
        }.start()
    }

    /**
     * Press feedback that does not move anything around it (rule: stable interaction states).
     * Scale only, so no sibling reflows.
     */
    fun pressable(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.97f).scaleY(0.97f)
                        .setDuration(90).setInterpolator(EASE).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(160).setInterpolator(EASE_OUT).start()
            }
            false   // never consume; the click listener still fires
        }
    }

    /** Slow breathing pulse for the panic screen. Four seconds in, four out. */
    fun breathe(v: View) {
        ValueAnimator.ofFloat(0.86f, 1.06f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = EASE
            addUpdateListener {
                val s = it.animatedValue as Float
                v.scaleX = s; v.scaleY = s
                v.alpha = 0.25f + (s - 0.86f) * 1.8f
            }
        }.start()
    }

    /** Parallax the hero image as the page scrolls. Subtle: half speed, never more. */
    fun parallax(scroll: android.widget.ScrollView, image: View) {
        scroll.viewTreeObserver.addOnScrollChangedListener {
            image.translationY = scroll.scrollY * 0.5f
            image.alpha = (1f - scroll.scrollY / 900f).coerceIn(0.15f, 1f)
        }
    }
}
