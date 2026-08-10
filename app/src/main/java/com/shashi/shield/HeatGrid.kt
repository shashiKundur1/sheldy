package com.shashi.shield

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Contribution-graph calendar: one small square per day, seven rows, weeks running left to right.
 * The same shape GitHub made familiar, which is why it reads instantly without a legend.
 *
 * Monochrome, so intensity is carried by fill rather than hue:
 *   solid white square, confirmed clean
 *   outlined + struck, relapse logged
 *   half-lit outline, today, still open
 *   barely-there square, day passed unconfirmed, or before you started
 *
 * Squares fade in on a diagonal sweep so the grid assembles rather than snapping into place.
 */
class HeatGrid(
    ctx: Context,
    private val days: List<DayCell>,
    private val animate: Boolean = true
) : View(ctx) {

    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Brand.INK
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Brand.INK; strokeWidth = dp(1.2f)
    }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0x14FFFFFF
    }

    private var t = 0f
    private val r = RectF()

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    init {
        // On a refresh the grid is rebuilt in place; replaying the sweep reads as a flicker.
        if (!animate) t = 1f
        else ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return
        val cols = days.size / 7
        if (cols == 0) return

        val gap = dp(4f)

        // A stroked rect is drawn centred ON its boundary, so half the stroke width falls
        // outside the rect. Without this inset the outlined cells in the first and last column
        //, including today's, had their left and right edges clipped by the view bounds.
        val pad = stroke.strokeWidth / 2f + dp(0.5f)

        val cell = minOf(
            (width - pad * 2 - gap * (cols - 1)) / cols.toFloat(),
            (height - pad * 2 - gap * 6) / 7f
        )
        val gridW = cell * cols + gap * (cols - 1)
        val gridH = cell * 7 + gap * 6
        val ox = (width - gridW) / 2f
        val oy = (height - gridH) / 2f
        val radius = dp(2f)

        days.forEachIndexed { i, d ->
            val col = i / 7
            val row = i % 7
            // diagonal sweep: later columns and lower rows arrive slightly after
            val delay = (col / cols.toFloat()) * 0.55f + (row / 7f) * 0.15f
            val a = ((t - delay) / 0.45f).coerceIn(0f, 1f)
            if (a <= 0f) return@forEachIndexed

            val x = ox + col * (cell + gap)
            val y = oy + row * (cell + gap)
            r.set(x, y, x + cell, y + cell)

            when (d.state) {
                Day.CLEAN -> {
                    solid.alpha = (255 * a).toInt()
                    canvas.drawRoundRect(r, radius, radius, solid)
                }
                Day.RELAPSE -> {
                    stroke.alpha = (255 * a).toInt()
                    canvas.drawRoundRect(r, radius, radius, stroke)
                    canvas.drawLine(x + cell * 0.22f, y + cell * 0.5f,
                        x + cell * 0.78f, y + cell * 0.5f, stroke)
                }
                Day.PENDING -> {
                    stroke.alpha = (150 * a).toInt()
                    canvas.drawRoundRect(r, radius, radius, stroke)
                }
                else -> {
                    empty.alpha = (20 * a).toInt()
                    canvas.drawRoundRect(r, radius, radius, empty)
                }
            }
        }
    }
}
