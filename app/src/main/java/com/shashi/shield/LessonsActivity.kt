package com.shashi.shield

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shashi.shield.Brand.S2
import com.shashi.shield.Brand.S3
import com.shashi.shield.Brand.S4
import com.shashi.shield.Brand.S6
import com.shashi.shield.Brand.S8
import com.shashi.shield.Brand.body
import com.shashi.shield.Brand.card
import com.shashi.shield.Brand.col
import com.shashi.shield.Brand.dp
import com.shashi.shield.Brand.eyebrow
import com.shashi.shield.Brand.gap
import com.shashi.shield.Brand.grow
import com.shashi.shield.Brand.row
import com.shashi.shield.Brand.title

/**
 * The lesson library.
 *
 * "Read it" used to mark a counter and re-render the same screen, which made it a button that
 * visibly did nothing, the lesson text was already fully on show above it. Now it opens
 * somewhere: today's lesson in full, then every other lesson underneath.
 *
 * Opening this is what counts as reading, so the MINOS badge tracks something real rather than
 * a tap on a card you never looked at.
 */
class LessonsActivity : Activity() {

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK

        // Opening the library is the read event. Store guards it to once per day.
        Store.markLessonRead(this)

        val today = Lessons.today()
        val p = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@LessonsActivity, S6), dp(this@LessonsActivity, S8),
                dp(this@LessonsActivity, S6), dp(this@LessonsActivity, S8))
            minimumHeight = dp(this@LessonsActivity, Brand.MIN_PAGE)
        }

        p.addView(eyebrow(this, "Lesson ${Store.lessonsRead(this)} of ${Lessons.all().size}"))
        p.addView(gap(this, S3))
        p.addView(Brand.display(this, "Today."))
        p.addView(gap(this, S6))

        // today's lesson, in full
        val head = card(this, Brand.SURFACE_HI)
        head.addView(row(this).apply {
            addView(FrameLayout(this@LessonsActivity).apply {
                background = Brand.surface(this@LessonsActivity, 2f, Brand.INK, Brand.INK)
                layoutParams = LinearLayout.LayoutParams(
                    dp(this@LessonsActivity, 20f), dp(this@LessonsActivity, 20f)
                ).apply { rightMargin = dp(this@LessonsActivity, S3) }
                addView(ImageView(this@LessonsActivity).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = android.content.res.ColorStateList.valueOf(Brand.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        dp(this@LessonsActivity, 14f), dp(this@LessonsActivity, 14f)
                    ).apply { gravity = Gravity.CENTER }
                })
            })
            addView(eyebrow(this@LessonsActivity, "Marked as read", Brand.INK_45))
        })
        head.addView(gap(this, S4))
        head.addView(title(this, today.title, Brand.T_SECTION))
        head.addView(gap(this, S3))
        head.addView(body(this, today.body, Brand.T_BODY, Brand.INK_80))
        p.addView(head)
        p.addView(gap(this, S8))

        p.addView(eyebrow(this, "Every lesson"))
        p.addView(gap(this, S3))

        val rows = mutableListOf<View>()
        Lessons.all().forEach { l ->
            if (l.title == today.title) return@forEach
            val c = card(this)
            c.addView(title(this, l.title, Brand.T_TITLE))
            c.addView(gap(this, S2))
            c.addView(body(this, l.body, Brand.T_BODY, Brand.INK_60))
            rows.add(c)
            p.addView(c, Brand.fill().apply { bottomMargin = dp(this@LessonsActivity, S3) })
        }

        p.addView(gap(this, S4))
        p.addView(title(this, "Back", Brand.T_BODY, Brand.INK_60).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(this@LessonsActivity, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
            setPadding(0, dp(this@LessonsActivity, S4), 0, dp(this@LessonsActivity, S4))
            layoutParams = Brand.fill()
            minHeight = dp(this@LessonsActivity, 48f)
            isClickable = true
            setOnClickListener { finish() }
        })

        setContentView(Nav.scaffold(this, ScrollView(this).apply {
            setBackgroundColor(Brand.BLACK); addView(p)
        }, active = 0))
        Anim.enter(p)
        Anim.stagger(rows.take(8), 34L, 140L)
    }
}
