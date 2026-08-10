package com.shashi.shield

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.PathInterpolator

/**
 * The last N days as big vertical bars, filling the space a decorative photograph used to hold.
 *
 * Same fill-versus-outline language as the rest of the app, so it survives greyscale and needs
 * no colour to be read:
 *   full solid bar, confirmed clean
 *   outlined stub, relapse logged
 *   faint stub, day passed unconfirmed
 *   half-height ring, today, still open
 *
 * Bars grow on first draw, staggered left to right, so the eye tracks the trend rather than
 * reading it as static decoration.
 */
class DayBars(ctx: Context, private val days: List<DayCell>) : View(ctx) {

    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Brand.INK
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Brand.INK
        strokeWidth = dp(1.4f)
    }
    private val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Brand.HAIRLINE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Brand.INK_30
        textSize = dp(9.5f)
        textAlign = Paint.Align.CENTER
        typeface = Brand.medium(ctx)
    }

    private var t = 0f
    private val rect = RectF()

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    init {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return

        val labelBand = dp(20f)
        val usable = height - labelBand
        val gap = dp(6f)
        // Same stroked-boundary problem as the heat grid: outlined bars at either end would
        // otherwise lose half their stroke to the view edge.
        val pad = outline.strokeWidth / 2f + dp(0.5f)
        val bw = (width - pad * 2 - gap * (days.size - 1)) / days.size
        val radius = dp(2f)
        val minStub = dp(10f)

        days.forEachIndexed { i, d ->
            val x = pad + i * (bw + gap)
            // stagger: each bar starts a little after the one to its left
            val local = ((t - i * 0.045f) / (1f - 0.045f * days.size).coerceAtLeast(0.3f))
                .coerceIn(0f, 1f)

            val targetH = when (d.state) {
                Day.CLEAN -> usable
                Day.PENDING -> usable * 0.45f
                Day.RELAPSE -> minStub * 2.2f
                else -> minStub
            }
            val h = targetH * local
            rect.set(x, usable - h, x + bw, usable)

            when (d.state) {
                Day.CLEAN -> canvas.drawRoundRect(rect, radius, radius, solid)
                Day.RELAPSE -> {
                    canvas.drawRoundRect(rect, radius, radius, outline)
                    // strike through, so a relapse never reads as a small win
                    canvas.drawLine(x, usable - h / 2f, x + bw, usable - h / 2f, outline)
                }
                Day.PENDING -> canvas.drawRoundRect(rect, radius, radius, outline)
                else -> canvas.drawRoundRect(rect, radius, radius, faint)
            }

            // weekday initial under every other bar, so the axis never crowds
            if (i % 2 == 0 || days.size <= 8) {
                canvas.drawText(
                    d.label, x + bw / 2f, height - dp(4f), label
                )
            }
        }
    }
}
