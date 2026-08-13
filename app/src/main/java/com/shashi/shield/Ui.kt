package com.shashi.shield

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shashi.shield.Brand.S1
import com.shashi.shield.Brand.S2
import com.shashi.shield.Brand.S3
import com.shashi.shield.Brand.S4
import com.shashi.shield.Brand.S5
import com.shashi.shield.Brand.S6
import com.shashi.shield.Brand.S8
import com.shashi.shield.Brand.body
import com.shashi.shield.Brand.card
import com.shashi.shield.Brand.col
import com.shashi.shield.Brand.dp
import com.shashi.shield.Brand.eyebrow
import com.shashi.shield.Brand.gap
import com.shashi.shield.Brand.grow
import com.shashi.shield.Brand.hero
import com.shashi.shield.Brand.mono
import com.shashi.shield.Brand.row
import com.shashi.shield.Brand.section
import com.shashi.shield.Brand.title
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- primitives

/** Hollow versus filled is the whole state language (theme rule 3). No colour involved. */
private fun Context.stateMark(on: Boolean, size: Float = 9f): View = View(this).apply {
    background = if (on) Brand.pill(this@stateMark, Brand.INK)
    else Brand.pill(this@stateMark, 0x00000000, Brand.INK_30)
    layoutParams = LinearLayout.LayoutParams(dp(this@stateMark, size), dp(this@stateMark, size))
        .apply { rightMargin = dp(this@stateMark, S4) }
}

private fun Context.ghost(label: String, onTap: () -> Unit): View =
    title(this, label, Brand.T_BODY).apply {
        gravity = Gravity.CENTER
        background = Brand.tappable(this@ghost, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
        setPadding(0, dp(this@ghost, S4), 0, dp(this@ghost, S4))
        layoutParams = Brand.fill()
        minHeight = dp(this@ghost, 48f)
        isClickable = true
        Anim.pressable(this)
        setOnClickListener { onTap() }
    }

/** The single filled element per screen (theme rule 4). */
private fun Context.primary(label: String, onTap: () -> Unit): View =
    TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Brand.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        typeface = Brand.black(this@primary)
        letterSpacing = 0.06f
        background = Brand.surface(this@primary, Brand.R_TILE, Brand.INK, Brand.INK)
        setPadding(0, dp(this@primary, S5), 0, dp(this@primary, S5))
        layoutParams = Brand.fill()
        minHeight = dp(this@primary, 64f)
        isClickable = true
        Anim.pressable(this)
        setOnClickListener { onTap() }
    }

private fun Context.field(hint: String, lines: Int = 3): EditText = EditText(this).apply {
    setHint(hint)
    setHintTextColor(Brand.INK_30)
    setTextColor(Brand.INK)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, Brand.T_BODY)
    background = Brand.surface(this@field, Brand.R_TILE, Brand.SURFACE)
    setPadding(dp(this@field, S4), dp(this@field, S4), dp(this@field, S4), dp(this@field, S4))
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    minLines = lines
    gravity = Gravity.TOP
    layoutParams = Brand.fill()
}


/** Boxy switch. A filled square slides in a hard track, no colour, no rounded thumb. */
private fun Context.toggleRow(
    label: String, sub: String, on: Boolean, onToggle: () -> Unit
): View {
    val r = row(this).apply {
        setPadding(0, dp(this@toggleRow, S3), 0, dp(this@toggleRow, S3))
        minimumHeight = dp(this@toggleRow, Brand.MIN_ROW)
        isClickable = true
        Anim.pressable(this)
        setOnClickListener { onToggle() }
    }
    r.addView(col(this).apply {
        layoutParams = grow()
        addView(title(this@toggleRow, label, Brand.T_BODY))
        addView(gap(this@toggleRow, 3f))
        addView(body(this@toggleRow, sub, Brand.T_CAPTION, Brand.INK_30))
    })
    val track = FrameLayout(this).apply {
        background = Brand.surface(this@toggleRow, Brand.R_TILE,
            if (on) Brand.INK else 0x00000000, if (on) Brand.INK else Brand.HAIRLINE)
        layoutParams = LinearLayout.LayoutParams(dp(this@toggleRow, 52f), dp(this@toggleRow, 30f))
    }
    track.addView(View(this).apply {
        background = Brand.surface(this@toggleRow, 1f,
            if (on) Brand.BLACK else Brand.INK_45, 0x00000000)
        layoutParams = FrameLayout.LayoutParams(
            dp(this@toggleRow, 22f), dp(this@toggleRow, 22f)).apply {
            gravity = if (on) Gravity.END or Gravity.CENTER_VERTICAL
                      else Gravity.START or Gravity.CENTER_VERTICAL
            marginStart = dp(this@toggleRow, 4f); marginEnd = dp(this@toggleRow, 4f)
        }
    })
    r.addView(track)
    return r
}

/** One line of a data table: label left, value right, hairline underneath. */
private fun Context.tableRow(left: String, right: String, last: Boolean = false): View =
    col(this).apply {
        addView(row(this@tableRow).apply {
            setPadding(0, dp(this@tableRow, S3), 0, dp(this@tableRow, S3))
            minimumHeight = dp(this@tableRow, Brand.MIN_TAP)
            addView(body(this@tableRow, left, Brand.T_BODY, Brand.INK_60)
                .apply { layoutParams = grow() })
            addView(Brand.mono(this@tableRow, right, 15f, Brand.INK))
        })
        if (!last) addView(Brand.rule(this@tableRow))
    }

/**
 * Scrim so overlaid type stays legible over any photograph. Four stops, not three: the wave has
 * blazing white foam right where the headline sits, and a simple transparent-to-black ramp left
 * the date unreadable. Dark at the very top for the status bar, a window of clear image through
 * the middle, then a hard ramp to solid black under the text.
 */
private fun scrim(top: Int, bottom: Int) = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(top, 0x30000000, 0xCC000000.toInt(), bottom)
)

/**
 * Full-bleed artwork header. The image sits behind the type with a scrim above and below, which
 * is why every hero was generated with a black upper third.
 */
private fun Activity.heroHeader(
    imageRes: Int, eyebrowText: String, headline: View, sub: String?, focusY: Float = 0.5f
): FrameLayout {
    val h = dp(this, 268f)
    val frame = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, h)
    }
    // CENTER_CROP always takes the middle band, which cut the concrete wall through its
    // flattest section. Scale to fill, then anchor at a per-image focal point instead.
    val img = ImageView(this).apply {
        setImageResource(imageRes)
        scaleType = ImageView.ScaleType.MATRIX
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, h)
        post {
            val d = drawable ?: return@post
            val vw = width.toFloat(); val vh = height.toFloat()
            val dw = d.intrinsicWidth.toFloat(); val dh = d.intrinsicHeight.toFloat()
            if (dw <= 0f || dh <= 0f) return@post
            val k = maxOf(vw / dw, vh / dh)
            imageMatrix = android.graphics.Matrix().apply {
                setScale(k, k)
                postTranslate((vw - dw * k) / 2f, (vh - dh * k) * focusY)
            }
        }
    }
    frame.addView(img)
    frame.addView(View(this).apply {
        background = scrim(0xE6000000.toInt(), 0xFF000000.toInt())
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, h)
    })
    val stack = col(this).apply {
        setPadding(dp(this@heroHeader, S6), dp(this@heroHeader, S6),
            dp(this@heroHeader, S6), dp(this@heroHeader, S8))
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
        }
    }
    stack.addView(eyebrow(this, eyebrowText))
    stack.addView(gap(this, S3))
    stack.addView(headline)
    if (sub != null) {
        stack.addView(gap(this, S2))
        stack.addView(body(this, sub, Brand.T_BODY, Brand.INK_60))
    }
    frame.addView(stack)
    frame.tag = img
    return frame
}

// ------------------------------------------------------------------ activity

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var bar: LinearLayout
    private var tab = 0

    /**
     * Toggling a setting rebuilds the page, which used to slam the scroll position back to the
     * top, so flipping a switch halfway down Settings threw you to the header. Keep a handle on
     * the live ScrollView and restore its offset when re-rendering the same tab. Switching tabs
     * still starts at the top, which is what you want there.
     */
    private var scroller: ScrollView? = null

    /**
     * True while re-rendering the tab you are already on (a toggle, a saved note). The entrance
     * animation fades every block up from alpha 0, which on a refresh reads as the whole screen
     * blacking out and coming back. Motion should mark arriving somewhere, not repainting.
     */
    private var isRefresh = false

    /** Guards against rebuild storms when a control is tapped repeatedly. */
    private var rebuildPending = false

    /**
     * Sub-screen within the Progress tab: null, "collection", or "badge:<id>".
     *
     * The collection used to be its own Activity, which meant it carried its own copy of the tab
     * bar and read as a second navigation stacked on the first. Rendering it inside the tab keeps
     * exactly one bar and one navigation model.
     */
    private var sub: String? = null

    /** Where you were on each screen, keyed "<tab>/<sub>". See show(). */
    private val scrollMemory = HashMap<String, Int>()
    private var screenKey = ""

    /** Focus-lock duration being dialled in, before it is started. */
    private var focusHours = 2

    /**
     * The one-second tick behind the lock countdown. Held as a field so leaving the page can stop
     * it, a Runnable reposting itself into a view that is no longer on screen is a leak that
     * keeps the whole activity alive.
     */
    private var lockTicker: Runnable = Runnable {}

    /**
     * Re-render the current tab, but not from inside the click handler.
     *
     * Rebuilding synchronously destroys the very view being pressed while its ripple and scale
     * are still running, and two quick taps stack two full rebuilds on top of each other, which
     * is what the glitching was. Coalesce to one rebuild on the next frame instead.
     */
    private fun refresh() {
        if (rebuildPending) return
        rebuildPending = true
        content.postDelayed({ rebuildPending = false; show(tab) }, 90L)
    }

    private val tabs = listOf(
        Triple("Today", R.drawable.ic_today, 0),
        Triple("Shield", R.drawable.ic_shield, 1),
        Triple("Notes", R.drawable.ic_journal, 2),
        Triple("Progress", R.drawable.ic_progress, 3),
        Triple("Settings", R.drawable.ic_settings, 4)
    )

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK

        val rootCol = col(this).apply { setBackgroundColor(Brand.BLACK) }
        content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        bar = buildTabBar()
        rootCol.addView(content)
        rootCol.addView(bar)

        // Android 15+ is edge to edge by default; without this the tab bar sits under the
        // gesture bar and the hero image gets clipped by the status bar.
        rootCol.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(0, 0, 0, b.bottom)
            topInset = b.top
            insets
        }
        setContentView(rootCol)
        refresh() }

    private var topInset = 0

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            sub?.startsWith("badge:") == true -> { sub = "collection"; show(3) }
            // Sub-screens live on different tabs now, so back has to return to the tab the
            // sub-screen actually belongs to rather than always to Progress.
            sub == "lock" -> { sub = null; show(1) }
            sub != null -> { sub = null; show(3) }
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getIntExtra(Nav.EXTRA_TAB, -1)?.takeIf { it >= 0 }?.let { setIntent(intent); show(it) }
    }

    override fun onResume() {
        super.onResume()
        runCatching { Achievements.syncEarned(this) }
        if (::content.isInitialized) show(tab)
    }

    // ---- chrome ----------------------------------------------------------

    private fun buildTabBar(): LinearLayout {
        val strip = row(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@MainActivity, S2), dp(this@MainActivity, S2),
                dp(this@MainActivity, S2), dp(this@MainActivity, S2))
        }
        return col(this).apply {
            setBackgroundColor(Brand.BLACK)
            addView(Brand.rule(this@MainActivity))
            addView(strip)
        }.also { it.tag = strip }
    }

    private fun paintTabBar() {
        val strip = bar.tag as LinearLayout
        strip.removeAllViews()
        tabs.forEach { (label, icon, index) ->
            val active = index == tab
            val item = col(this).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                minimumHeight = dp(this@MainActivity, 52f)
                background = Brand.tappable(this@MainActivity, Brand.R_TILE, 0x00000000, 0x00000000)
                isClickable = true
                setPadding(0, dp(this@MainActivity, S3), 0, dp(this@MainActivity, S3))
                setOnClickListener { if (index != tab) show(index) }
            }
            item.addView(ImageView(this).apply {
                setImageResource(icon)
                alpha = if (active) 1f else 0.3f
                layoutParams = LinearLayout.LayoutParams(
                    dp(this@MainActivity, 21f), dp(this@MainActivity, 21f))
            })
            item.addView(gap(this, 5f))
            item.addView(TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                setTextColor(if (active) Brand.INK else Brand.INK_30)
                typeface = if (active) Brand.semibold(this@MainActivity)
                           else Brand.regular(this@MainActivity)
                includeFontPadding = false
                gravity = Gravity.CENTER
            })
            strip.addView(item)
        }
    }

    private fun show(index: Int) {
        // "Same tab" is not the same thing as "same screen". The collection and a badge detail are
        // both tab 3, so opening a badge counted as a refresh and inherited the collection's raw
        // scroll offset, tap a badge near the bottom of the list and its detail opened halfway
        // down. Coming back did the mirror image: the detail's offset (0) was carried into the
        // collection, dumping you at the top of the list instead of next to the badge you tapped.
        //
        // Key by screen, not by tab, and remember an offset per screen. A screen you have never
        // opened starts at the top; a screen you are returning to starts where you left it.
        val prevKey = screenKey
        val nextKey = "$index/${sub ?: ""}"
        if (prevKey.isNotEmpty()) scrollMemory[prevKey] = scroller?.scrollY ?: 0
        // Leaving the lock page stops its countdown. Rebuilt fresh if we come back.
        content.removeCallbacks(lockTicker)
        isRefresh = nextKey == prevKey
        // A badge detail always opens at the top; it is a page you read, not a list you resume.
        val keepY = if (sub?.startsWith("badge:") == true) 0 else scrollMemory[nextKey] ?: 0
        screenKey = nextKey
        tab = index
        paintTabBar()
        val page = when (index) {
            0 -> todayTab(); 1 -> shieldTab(); 2 -> notesTab()
            3 -> progressTab(); else -> settingsTab()
        }
        content.removeAllViews()
        // A note field inside the page would otherwise take focus on layout and scroll the
        // ScrollView down to it, so every tab opened halfway through its own content.
        // BEFORE_DESCENDANTS means the container claims focus first.
        page.minimumHeight = dp(this, Brand.MIN_PAGE)
        page.isFocusableInTouchMode = true
        page.descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
        val sv = ScrollView(this).apply {
            setBackgroundColor(Brand.BLACK)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(page)
        }
        page.requestFocus()
        // Land where the user was, not at the top, except on a genuine tab switch.
        sv.post { sv.scrollTo(0, keepY) }
        content.addView(sv)
        scroller = sv
        // Parallax the hero if this page has one.
        (page.getChildAt(0) as? FrameLayout)?.tag?.let { img ->
            if (img is View) Anim.parallax(sv, img)
        }
    }

    /**
     * Content below the hero. One gutter everywhere (theme rule 9), a minimum height so a
     * sparse tab still fills the screen instead of floating in the middle, and a bottom inset
     * larger than the tab bar so the last row is never half-hidden behind it.
     */
    private fun contentCol(): LinearLayout = col(this).apply {
        setPadding(dp(this@MainActivity, S6), dp(this@MainActivity, S6),
            dp(this@MainActivity, S6), dp(this@MainActivity, 112f))
        minimumHeight = dp(this@MainActivity, Brand.MIN_PAGE)
    }

    // ---- Today -----------------------------------------------------------

    private fun todayTab(): LinearLayout {
        val p = col(this).apply { setBackgroundColor(Brand.BLACK) }
        val streak = Store.streak(this)
        val stamp = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date())

        val counter = hero(this, "0")
        val head = row(this).apply {
            addView(counter)
            addView(col(this@MainActivity).apply {
                setPadding(dp(this@MainActivity, S3), dp(this@MainActivity, 26f), 0, 0)
                addView(section(this@MainActivity, if (streak == 1) "day" else "days"))
                addView(body(this@MainActivity, "clean", Brand.T_BODY, Brand.INK_45))
            })
        }
        val hh = heroHeader(R.drawable.hero_today, stamp, head, focusY = 0.62f, sub =
            // The counter is days since the last relapse, so day zero is the day it happened and
            // tomorrow is day one. Say that plainly rather than describing the old confirm rule.
            if (streak == 0) "Today is day zero. Tomorrow is day one."
            else "Since the last reset, one day at a time.")
        p.addView(hh)
        if (isRefresh) counter.text = "$streak" else Anim.countTo(counter, streak)

        val b = contentCol()
        val blocks = mutableListOf<View>()

        // Yesterday still unresolved, the nightly confirmation.
        if (Store.needsConfirmation(this)) {
            val c = card(this, Brand.SURFACE_HI)
            c.addView(eyebrow(this, "Yesterday"))
            c.addView(gap(this, S3))
            c.addView(title(this, "Did you stay clean yesterday?", Brand.T_SECTION))
            c.addView(gap(this, S2))
            c.addView(body(this, "This is what turns a day into a counted day.",
                Brand.T_CAPTION, Brand.INK_45))
            c.addView(gap(this, S4))
            c.addView(row(this).apply {
                addView(ghost("Yes") {
                    Store.confirmClean(this@MainActivity, Store.yesterdayKey()); refresh() }.apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
                addView(View(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(this@MainActivity, S2), 1)
                })
                addView(ghost("No") {
                    startActivity(Intent(this@MainActivity, RelapseActivity::class.java)
                        .putExtra("dayKey", Store.yesterdayKey()))
                }.apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
            })
            blocks.add(c)
        }

        // Thought of the day
        val t = card(this)
        t.addView(eyebrow(this, "Thought for today"))
        t.addView(gap(this, S3))
        t.addView(title(this, Thoughts.today(), Brand.T_SECTION).apply {
            setLineSpacing(dp(this@MainActivity, 7f).toFloat(), 1f)
        })
        blocks.add(t)

        // A second door into the companion. Reaching it only through the panic button means you
        // have to be in crisis to talk, which is exactly backwards.
        val talk = card(this)
        talk.addView(row(this).apply {
            addView(col(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(this@MainActivity, S4) }
                addView(title(this@MainActivity, "Talk to the companion", Brand.T_TITLE))
                addView(gap(this@MainActivity, 5f))
                addView(body(this@MainActivity,
                    "You do not have to be in crisis to open this.",
                    Brand.T_CAPTION, Brand.INK_30))
            })
            addView(title(this@MainActivity, "\u203A", 19f, Brand.INK_30))
        })
        talk.isClickable = true
        Anim.pressable(talk)
        talk.setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        blocks.add(talk)

        blocks.add(primary("PANIC") {
            Store.logPanic(this)
            startActivity(Intent(this, BlockActivity::class.java)
                .putExtra("reason", "you pressed panic"))
        })

        // Catch-up sits above everything. A day you forgot to close breaks the streak and leaves
        // a hole with no way to say what happened, so filling gaps has to be the first thing on
        // the screen, not something buried in a calendar you have to know is tappable.
        val missed = Store.missedDays(this)
        if (missed.isNotEmpty()) {
            val cu = card(this, Brand.SURFACE_HI)
            cu.addView(row(this).apply {
                addView(eyebrow(this@MainActivity, "Catch up").apply { layoutParams = grow() })
                addView(eyebrow(this@MainActivity,
                    if (missed.size == 1) "1 day open" else "${missed.size} days open",
                    Brand.INK_45))
            })
            cu.addView(gap(this, S3))
            cu.addView(body(this,
                "These finished without being confirmed. Fill them in and the record is honest.",
                Brand.T_CAPTION, Brand.INK_45))
            cu.addView(gap(this, S4))

            missed.take(7).forEachIndexed { i, key ->
                cu.addView(row(this).apply {
                    setPadding(0, dp(this@MainActivity, S2), 0, dp(this@MainActivity, S2))
                    minimumHeight = dp(this@MainActivity, Brand.MIN_TAP)
                    addView(body(this@MainActivity, Store.prettyDay(key), Brand.T_BODY, Brand.INK_80)
                        .apply { layoutParams = grow() })
                    addView(title(this@MainActivity, "Clean", Brand.T_CAPTION, Brand.BLACK).apply {
                        gravity = Gravity.CENTER
                        background = Brand.surface(this@MainActivity, Brand.R_TILE,
                            Brand.INK, Brand.INK)
                        setPadding(dp(this@MainActivity, S3), dp(this@MainActivity, S2),
                            dp(this@MainActivity, S3), dp(this@MainActivity, S2))
                        minHeight = dp(this@MainActivity, 40f)
                        isClickable = true
                        Anim.pressable(this)
                        setOnClickListener { Store.confirmClean(this@MainActivity, key); refresh() }
                    })
                    addView(title(this@MainActivity, "Relapsed", Brand.T_CAPTION, Brand.INK_60).apply {
                        gravity = Gravity.CENTER
                        background = Brand.surface(this@MainActivity, Brand.R_TILE,
                            0x00000000, Brand.HAIRLINE)
                        setPadding(dp(this@MainActivity, S3), dp(this@MainActivity, S2),
                            dp(this@MainActivity, S3), dp(this@MainActivity, S2))
                        minHeight = dp(this@MainActivity, 40f)
                        isClickable = true
                        Anim.pressable(this)
                        setOnClickListener {
                            startActivity(Intent(this@MainActivity, RelapseActivity::class.java)
                                .putExtra("dayKey", key))
                        }
                        (layoutParams as? LinearLayout.LayoutParams)?.marginStart =
                            dp(this@MainActivity, S2)
                    })
                })
                if (i != minOf(missed.size, 7) - 1) cu.addView(Brand.rule(this))
            }
            blocks.add(cu)
        }

        // Closing the day. This is the core daily loop and it was only reachable from Settings,
        // which is the wrong place for the one thing you do every evening.
        //
        // Relapse stays available all day, you might slip at 2pm, but "clean" cannot be
        // claimed until 21:00, because a day you have not finished is not a day you can confirm.
        val todayState = Store.rawState(this, Store.todayKey())
        val canClose = Store.canConfirm(Store.todayKey())
        val dc = card(this, if (todayState != null) Brand.SURFACE_HI else Brand.SURFACE)
        dc.addView(row(this).apply {
            addView(eyebrow(this@MainActivity, "Close the day").apply { layoutParams = grow() })
            if (todayState == null && !canClose)
                addView(eyebrow(this@MainActivity, "unlocks 21:00", Brand.INK_30))
        })
        dc.addView(gap(this, S3))
        when (todayState) {
            Day.CLEAN -> {
                dc.addView(row(this).apply {
                    addView(FrameLayout(this@MainActivity).apply {
                        background = Brand.surface(this@MainActivity, 2f, Brand.INK, Brand.INK)
                        layoutParams = LinearLayout.LayoutParams(
                            dp(this@MainActivity, 22f), dp(this@MainActivity, 22f)
                        ).apply { rightMargin = dp(this@MainActivity, S3) }
                        addView(ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.ic_check)
                            imageTintList =
                                android.content.res.ColorStateList.valueOf(Brand.BLACK)
                            layoutParams = FrameLayout.LayoutParams(
                                dp(this@MainActivity, 15f), dp(this@MainActivity, 15f)
                            ).apply { gravity = Gravity.CENTER }
                        })
                    })
                    addView(title(this@MainActivity, "Today is counted", Brand.T_SECTION))
                })
                dc.addView(gap(this, S2))
                dc.addView(body(this, "Confirmed clean. Nothing else to do tonight.",
                    Brand.T_CAPTION, Brand.INK_45))
            }
            Day.RELAPSE -> {
                dc.addView(title(this, "Logged.", Brand.T_SECTION))
                dc.addView(gap(this, S2))
                dc.addView(body(this,
                    "Back to zero, and that is all it means. The comeback is the number that " +
                    "actually predicts how this goes.", Brand.T_CAPTION, Brand.INK_45))
            }
            else -> {
                dc.addView(title(this,
                    if (canClose) "Did you stay clean today?" else "Come back at 21:00",
                    Brand.T_SECTION))
                dc.addView(gap(this, S2))
                dc.addView(body(this,
                    if (canClose) "This is what turns today into a counted day."
                    else "A day has to be finished before it can be confirmed. " +
                         "If you slipped, log it now, that does not wait.",
                    Brand.T_CAPTION, Brand.INK_45))
                dc.addView(gap(this, S4))
                if (canClose) {
                    dc.addView(primary("I stayed clean today") {
                        Store.confirmClean(this, Store.todayKey()); refresh() })
                    dc.addView(gap(this, S2))
                }
                dc.addView(ghost("I relapsed") {
                    startActivity(Intent(this, RelapseActivity::class.java)
                        .putExtra("dayKey", Store.todayKey()))
                })
            }
        }
        blocks.add(dc)

        // Lesson of the day
        val lesson = Lessons.today()
        val lc = card(this)
        lc.addView(eyebrow(this, "Today's lesson"))
        lc.addView(gap(this, S3))
        lc.addView(title(this, lesson.title, Brand.T_TITLE))
        lc.addView(gap(this, S2))
        lc.addView(body(this, lesson.body, Brand.T_BODY, Brand.INK_60))
        lc.addView(gap(this, S3))
        // Opens the library rather than silently incrementing a counter and re-rendering the
        // same screen, which is what it used to do.
        lc.addView(ghost(
            if (Store.lessonReadToday(this)) "Read all lessons" else "Read it and mark it done"
        ) { startActivity(Intent(this, LessonsActivity::class.java)) })
        blocks.add(lc)

        // What is coming next. A static list of six milestones with checkboxes was noise in
        // Settings, the badge collection already tracks the same thresholds far better. What
        // the milestones uniquely carry is the warning, and a warning is only useful just
        // before it applies, so only the next one is ever shown.
        Milestones.next(streak)?.let { m ->
            val away = m.days - streak
            val mc = card(this)
            mc.addView(row(this).apply {
                addView(eyebrow(this@MainActivity, "Next milestone")
                    .apply { layoutParams = grow() })
                addView(eyebrow(this@MainActivity,
                    if (away == 1) "1 day away" else "$away days away", Brand.INK_45))
            })
            mc.addView(gap(this, S3))
            mc.addView(title(this, m.name, Brand.T_SECTION))
            mc.addView(gap(this, S2))
            mc.addView(body(this, m.why, Brand.T_BODY, Brand.INK_60))
            blocks.add(mc)
        }

        // Commitment, only while unsigned, once signed it stops nagging.
        if (!Store.pledgeSigned(this)) {
            val pc = card(this, Brand.SURFACE_HI)
            pc.addView(row(this).apply {
                addView(col(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        .apply { marginEnd = dp(this@MainActivity, S4) }
                    addView(title(this@MainActivity, "Sign the commitment", Brand.T_TITLE))
                    addView(gap(this@MainActivity, 5f))
                    addView(body(this@MainActivity,
                        "Takes ten seconds. It is the step that measurably sticks.",
                        Brand.T_CAPTION, Brand.INK_45))
                })
                addView(title(this@MainActivity, "\u203A", 19f, Brand.INK_30))
            })
            pc.isClickable = true
            Anim.pressable(pc)
            pc.setOnClickListener { startActivity(Intent(this, PledgeActivity::class.java)) }
            blocks.add(pc)
        }

        // Calendar
        val cal = card(this)
        cal.addView(row(this).apply {
            addView(eyebrow(this@MainActivity, SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(Date())).apply { layoutParams = grow() })
            addView(eyebrow(this@MainActivity, "${Store.cleanDaysTotal(this@MainActivity)} clean"))
        })
        cal.addView(gap(this, S4))
        cal.addView(calendarGrid())
        blocks.add(cal)

        // Quick note
        val nc = card(this)
        nc.addView(eyebrow(this, "Quick note"))
        nc.addView(gap(this, S3))
        val f = field("What is going on right now?")
        nc.addView(f)
        nc.addView(gap(this, S3))
        nc.addView(ghost("Save note") {
            Store.addNote(this, f.text.toString()); f.setText(""); refresh() })
        blocks.add(nc)

        blocks.distinct().forEach { v ->
            if (v.parent == null) {
                b.addView(v, Brand.fill().apply { bottomMargin = dp(this@MainActivity, S4) })
            }
        }
        p.addView(b)
        if (!isRefresh) Anim.stagger(blocks, 40L, 120L)
        return p
    }

    /** Monday-first month grid. Fill = clean, ring = today, hollow dim = missed or future. */
    private fun calendarGrid(): LinearLayout {
        val now = Calendar.getInstance()
        val cells = Store.monthGrid(this, now.get(Calendar.YEAR), now.get(Calendar.MONTH))
        val grid = col(this)

        grid.addView(row(this).apply {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                addView(eyebrow(this@MainActivity, d).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                })
            }
        })
        grid.addView(gap(this, S3))

        cells.chunked(7).forEach { week ->
            val r = row(this).apply {
                layoutParams = Brand.fill().apply { bottomMargin = dp(this@MainActivity, 6f) }
            }
            week.forEach { c -> r.addView(dayCell(c)) }
            repeat(7 - week.size) {
                r.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            grid.addView(r)
        }
        return grid
    }

    private fun dayCell(c: DayCell): View {
        val box = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(this@MainActivity, 38f), 1f)
        }
        if (c.dayOfMonth == 0) return box

        // Squares, not rings. A hard grid reads as a record sheet, and squares tile the
        // month with even gutters where circles leave ragged optical gaps.
        val disc = View(this).apply {
            background = when (c.state) {
                Day.CLEAN -> Brand.surface(this@MainActivity, Brand.R_TILE, Brand.INK, Brand.INK)
                Day.RELAPSE -> Brand.surface(this@MainActivity, Brand.R_TILE, 0x00000000, Brand.INK)
                Day.PENDING -> Brand.surface(this@MainActivity, Brand.R_TILE, Brand.SURFACE_HI, Brand.INK_60)
                Day.MISSED -> Brand.surface(this@MainActivity, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
                else -> Brand.surface(this@MainActivity, Brand.R_TILE, 0x00000000, 0x00000000)
            }
            layoutParams = FrameLayout.LayoutParams(
                dp(this@MainActivity, 34f), dp(this@MainActivity, 34f)).apply {
                gravity = Gravity.CENTER
            }
        }
        val num = TextView(this).apply {
            text = c.dayOfMonth.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = if (c.isToday) Brand.semibold(this@MainActivity)
                       else Brand.regular(this@MainActivity)
            setTextColor(
                when (c.state) {
                    Day.CLEAN -> Brand.BLACK
                    Day.RELAPSE -> Brand.INK
                    Day.PENDING -> Brand.INK
                    Day.MISSED -> Brand.INK_30
                    else -> 0x33FFFFFF
                }
            )
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        box.addView(disc); box.addView(num)

        // Past or finished days can be resolved by tapping them. canConfirm() refuses today
        // before 21:00 and refuses the future outright, so nothing can be pre-marked.
        // Open the editor rather than marking the day clean on the spot. A single stray tap
        // used to rewrite the record with no confirmation, and a day you actually relapsed on
        // could only ever be set to clean.
        if (c.key.isNotEmpty() && c.key <= Store.todayKey()) {
            box.isClickable = true
            Anim.pressable(box)
            box.setOnClickListener {
                startActivity(Intent(this, DayEditActivity::class.java)
                    .putExtra("dayKey", c.key))
            }
        }

        // A badge earned that day gets a small notch in the corner, so the calendar doubles as
        // a record of when things were won, not only when they were survived.
        // No badge dot on calendar cells. It collided with today's outline and added a second
        // meaning to a grid that already says everything it needs to: filled = clean,
        // struck = relapse, outlined = today. Badges live in the collection, not here.

        // A relapse day gets a strike through the square so it reads without colour.
        if (c.state == Day.RELAPSE) {
            box.addView(View(this).apply {
                setBackgroundColor(Brand.INK)
                layoutParams = FrameLayout.LayoutParams(
                    dp(this@MainActivity, 18f), dp(this@MainActivity, 1f)).apply {
                    gravity = Gravity.CENTER
                }
                rotation = -45f
            })
        }
        return box
    }

    // ---- Shield ----------------------------------------------------------

    private fun shieldTab(): LinearLayout {
        if (sub == "lock") return lockView()
        val p = col(this).apply { setBackgroundColor(Brand.BLACK) }
        val live = Features.all(this).filter { it.live }
        val allOn = live.all { it.done }

        p.addView(heroHeader(R.drawable.hero_shield, "Protection",
            Brand.display(this, if (allOn) "Held." else "Partly held."),
            "${live.count { it.done }} of ${live.size} defences active.", focusY = 0.28f))

        val b = contentCol()
        val blocks = mutableListOf<View>()

        // The lock, first thing on the tab. It is the heaviest thing this app can do to the
        // phone, so it does not belong four screens deep in a settings list.
        val lockLive = Lock.active(this)
        val lc = card(this, if (lockLive) Brand.SURFACE_HI else Brand.SURFACE)
        lc.addView(row(this).apply {
            isClickable = true
            Anim.pressable(this)
            setOnClickListener { sub = "lock"; show(1) }
            addView(col(this@MainActivity).apply {
                layoutParams = grow()
                addView(eyebrow(this@MainActivity,
                    if (lockLive) "Locked now" else "Phone lock"))
                addView(gap(this@MainActivity, S2))
                addView(title(this@MainActivity,
                    if (lockLive) Lock.reason(this@MainActivity) else "Lock the phone",
                    Brand.T_SECTION))
                addView(gap(this@MainActivity, S1))
                addView(body(this@MainActivity,
                    if (lockLive)
                        "Calls still work. ${Lock.emergenciesLeft(this@MainActivity)} emergency " +
                        "sessions left."
                    else "Everything suspended except calls and three apps you pick.",
                    Brand.T_CAPTION))
            })
            addView(title(this@MainActivity, "›", 22f, Brand.INK_30).apply {
                setPadding(dp(this@MainActivity, S3), 0, 0, 0)
            })
        })
        blocks.add(lc)

        // The greyscale switch is refused while a lock or an emergency is running. Decided
        // before, not during, a switch you can flip mid-lock is a button, not a rule.
        val greyHeld = Lock.greyscaleHeld(this)
        val grey = Monochrome.isOn(this)
        val g = card(this, if (grey) Brand.SURFACE_HI else Brand.SURFACE)
        g.addView(row(this).apply {
            addView(col(this@MainActivity).apply {
                layoutParams = grow()
                addView(title(this@MainActivity, "Kill all colour", Brand.T_SECTION))
                addView(gap(this@MainActivity, S1))
                addView(body(this@MainActivity,
                    if (greyHeld) "Held by the lock. Cannot be switched off until it ends."
                    else "Drains colour from the entire phone. Every app.", Brand.T_CAPTION))
            })
            addView(TextView(this@MainActivity).apply {
                text = if (grey) "ON" else "OFF"
                setTextColor(if (grey) Brand.BLACK else Brand.INK_60)
                background = Brand.surface(this@MainActivity, Brand.R_TILE,
                    if (grey) Brand.INK else 0x00000000, Brand.HAIRLINE)
                setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S3),
                    dp(this@MainActivity, S5), dp(this@MainActivity, S3))
                textSize = 12f
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
                minHeight = dp(this@MainActivity, 48f)
            })
        })
        g.isClickable = true
        Anim.pressable(g)
        g.setOnClickListener {
            if (greyHeld) {
                android.widget.Toast.makeText(this,
                    "The lock is holding this. Not until it ends.",
                    android.widget.Toast.LENGTH_SHORT).show()
            } else { Monochrome.toggle(this); refresh() }
        }
        blocks.add(g)

        val list = card(this)
        list.addView(eyebrow(this, "Active protections"))
        list.addView(gap(this, S2))
        live.forEachIndexed { i, f -> list.addView(featureLine(f, i == live.lastIndex)) }
        blocks.add(list)

        blocks.distinct().forEach { v ->
            if (v.parent == null) {
                b.addView(v, Brand.fill().apply { bottomMargin = dp(this@MainActivity, S4) })
            }
        }
        p.addView(b)
        if (!isRefresh) Anim.stagger(blocks, 40L, 120L)
        return p
    }

    /**
     * A checklist line reads as a checklist: a filled box with a tick when done, an empty box
     * when not. The previous dot carried the same information but did not look like something
     * you could ever finish.
     */
    private fun featureLine(f: Feature, last: Boolean = false): View = col(this).apply {
        addView(row(this@MainActivity).apply {
            setPadding(0, dp(this@MainActivity, S4), 0, dp(this@MainActivity, S4))
            minimumHeight = dp(this@MainActivity, Brand.MIN_ROW)

            addView(FrameLayout(this@MainActivity).apply {
                background = Brand.surface(this@MainActivity, 2f,
                    if (f.done) Brand.INK else 0x00000000,
                    if (f.done) Brand.INK else Brand.INK_30)
                layoutParams = LinearLayout.LayoutParams(
                    dp(this@MainActivity, 20f), dp(this@MainActivity, 20f)
                ).apply { rightMargin = dp(this@MainActivity, S4) }
                if (f.done) addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = android.content.res.ColorStateList.valueOf(Brand.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        dp(this@MainActivity, 14f), dp(this@MainActivity, 14f)
                    ).apply { gravity = Gravity.CENTER }
                })
            })

            addView(col(this@MainActivity).apply {
                layoutParams = grow()
                addView(title(this@MainActivity, f.name, Brand.T_BODY,
                    if (f.done) Brand.INK else Brand.INK_45))
                addView(gap(this@MainActivity, 4f))
                addView(body(this@MainActivity, f.note, Brand.T_CAPTION, Brand.INK_30))
            })
        })
        // Inset the divider to the text column. A full-width rule under a 36dp-indented title
        // is what made the list read as misaligned.
        if (!last) addView(View(this@MainActivity).apply {
            setBackgroundColor(Brand.HAIRLINE)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(this@MainActivity, 1f))
                .apply { marginStart = dp(this@MainActivity, 36f) }
        })
    }

    // ---- Notes -----------------------------------------------------------

    private fun notesTab(): LinearLayout {
        val p = col(this).apply { setBackgroundColor(Brand.BLACK) }
        val notes = Store.notes(this)
        p.addView(heroHeader(R.drawable.hero_notes, "Journal",
            Brand.display(this, "Notes."),
            "What you write at 1am is data you can use at 1pm.", focusY = 0.45f))

        val b = contentCol()
        val blocks = mutableListOf<View>()

        val nc = card(this)
        val f = field("Write it down.", 4)
        nc.addView(f)
        nc.addView(gap(this, S3))
        nc.addView(ghost("Save") { Store.addNote(this, f.text.toString()); f.setText(""); refresh() })
        blocks.add(nc)

        if (notes.isEmpty()) {
            val e = card(this)
            e.addView(title(this, "Nothing yet.", Brand.T_BODY, Brand.INK_60))
            e.addView(gap(this, S1))
            e.addView(body(this, "The first note is usually the useful one.",
                Brand.T_CAPTION, Brand.INK_30))
            blocks.add(e)
        } else {
            val fmt = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
            notes.take(80).forEach { n ->
                val c = card(this, Brand.SURFACE, Brand.R_TILE)
                c.addView(row(this).apply {
                    addView(eyebrow(this@MainActivity, fmt.format(Date(n.at)))
                        .apply { layoutParams = grow() })
                    if (n.kind == "relapse")
                        addView(eyebrow(this@MainActivity, "reset", Brand.INK_60))
                })
                c.addView(gap(this, S2))
                c.addView(body(this, n.text, Brand.T_BODY, Brand.INK_80))
                blocks.add(c)
            }
        }
        blocks.distinct().forEach { v ->
            if (v.parent == null) {
                b.addView(v, Brand.fill().apply { bottomMargin = dp(this@MainActivity, S2) })
            }
        }
        p.addView(b)
        if (!isRefresh) Anim.stagger(blocks.take(12), 34L, 120L)
        return p
    }

    // ---- the lock, its own page -------------------------------------------

    /**
     * The lock screen proper. One ring, one number, and the two ways out underneath.
     *
     * The ring is the page. A lock you can only read as a row of settings does not feel like
     * anything; a clock face counting down does, and knowing the wall-clock time it ends is more
     * use at 3am than knowing how many minutes are left, "until 06:00" is a fact about the world,
     * "312 minutes" is arithmetic you have to do while half asleep.
     */
    private fun lockView(): LinearLayout {
        val p = col(this).apply { setBackgroundColor(Brand.BLACK) }
        val live = Lock.active(this)
        val open = Lock.granted(this)

        val b = contentCol().apply {
            setPadding(dp(this@MainActivity, S6), topInset + dp(this@MainActivity, S8),
                dp(this@MainActivity, S6), dp(this@MainActivity, 112f))
        }
        val blocks = mutableListOf<View>()

        // Back, matching the collection page exactly. Two different back controls in one app is
        // one more than there should be.
        b.addView(row(this).apply {
            addView(eyebrow(this@MainActivity, "Phone lock").apply { layoutParams = grow() })
            addView(title(this@MainActivity, "Back", Brand.T_CAPTION, Brand.INK_45).apply {
                background = Brand.surface(this@MainActivity, Brand.R_TILE, 0x00000000,
                    Brand.HAIRLINE)
                setPadding(dp(this@MainActivity, S4), dp(this@MainActivity, S2),
                    dp(this@MainActivity, S4), dp(this@MainActivity, S2))
                minHeight = dp(this@MainActivity, 40f)
                gravity = Gravity.CENTER
                isClickable = true
                Anim.pressable(this)
                setOnClickListener { sub = null; show(1) }
            })
        })
        b.addView(gap(this, S6))

        // --- the clock ---
        // Blockit puts the time in a big soft filled slab. The idea is right, the number is the
        // screen, but filled-and-rounded is not this app's language, so it is a hairline frame
        // on pure black with the type doing the work. Rule 3: one filled element per screen, and
        // on this screen that is the slider at the bottom, not the clock.
        val clockCard = col(this).apply {
            background = Brand.surface(this@MainActivity, Brand.R_CARD, 0x00000000, Brand.HAIRLINE)
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S6),
                dp(this@MainActivity, S5), dp(this@MainActivity, S5))
            layoutParams = Brand.fill()
        }
        val state = eyebrow(this, "", Brand.INK_30).apply { gravity = Gravity.CENTER }
        val clock = Brand.mono(this, "0:00:00", 58f, Brand.INK).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val endsAt = body(this, "", Brand.T_CAPTION, Brand.INK_45).apply {
            gravity = Gravity.CENTER
        }
        val bar = LockBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(this@MainActivity, 3f))
        }
        clockCard.addView(state)
        clockCard.addView(gap(this, S3))
        clockCard.addView(clock)
        clockCard.addView(gap(this, S3))
        clockCard.addView(bar)
        clockCard.addView(gap(this, S3))
        clockCard.addView(endsAt)
        blocks.add(clockCard)

        // One ticker, cancelled whenever the page goes away. See show().
        content.removeCallbacks(lockTicker)
        lockTicker = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val on = Lock.active(this@MainActivity) || Lock.granted(this@MainActivity)
                if (!on) {
                    state.text = "NOT LOCKED"
                    clock.text = "%d:00:00".format(focusHours)
                    endsAt.text = "Slide to start"
                    bar.progress = 0f
                } else {
                    val endMs = when {
                        Lock.granted(this@MainActivity) -> Lock.grantUntil(this@MainActivity)
                        Lock.focusActive(this@MainActivity) -> Lock.focusUntil(this@MainActivity)
                        else -> sleepEndMillis()
                    }
                    val left = ((endMs - now) / 1000L).coerceAtLeast(0L)
                    clock.text = "%d:%02d:%02d".format(left / 3600, (left / 60) % 60, left % 60)
                    state.text = when {
                        Lock.granted(this@MainActivity) -> "OPEN"
                        Lock.focusActive(this@MainActivity) -> "FOCUS LOCK"
                        else -> "SLEEP LOCK"
                    }
                    endsAt.text = "Ends at " + java.text.SimpleDateFormat("HH:mm",
                        java.util.Locale.UK).format(java.util.Date(endMs))
                    val span = when {
                        Lock.granted(this@MainActivity) -> Lock.EMERGENCY_MINUTES * 60_000L
                        Lock.focusActive(this@MainActivity) -> focusHours * 3_600_000L
                        else -> 8 * 3_600_000L
                    }.coerceAtLeast(1L)
                    bar.progress = 1f - (left * 1000f / span).coerceIn(0f, 1f)
                }
                content.postDelayed(this, 1000)
            }
        }
        content.post(lockTicker)

        // --- presets and the slider ---
        if (!live && !open) {
            // Preset tiles instead of a stepper. Tapping "+" eight times to reach eight hours is
            // not a decision, it is data entry, and the four durations that matter are known.
            val grid = col(this)
            listOf(1 to 4, 2 to 8).forEach { _ -> }
            val choices = listOf(1, 2, 4, Lock.FOCUS_MAX_HOURS)
            choices.chunked(2).forEach { pair ->
                grid.addView(row(this).apply {
                    layoutParams = Brand.fill().apply {
                        bottomMargin = dp(this@MainActivity, S3)
                    }
                    pair.forEachIndexed { i, h ->
                        val on = focusHours == h
                        addView(col(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                                if (i == 0) marginEnd = dp(this@MainActivity, S3)
                            }
                            background = Brand.tappable(this@MainActivity, Brand.R_TILE,
                                if (on) Brand.INK else 0x00000000,
                                if (on) Brand.INK else Brand.HAIRLINE)
                            setPadding(dp(this@MainActivity, S4), dp(this@MainActivity, S4),
                                dp(this@MainActivity, S4), dp(this@MainActivity, S4))
                            minimumHeight = dp(this@MainActivity, 84f)
                            gravity = Gravity.CENTER
                            isClickable = true
                            Anim.pressable(this)
                            setOnClickListener { focusHours = h; refresh() }
                            addView(Brand.mono(this@MainActivity, "$h", 30f,
                                if (on) Brand.BLACK else Brand.INK).apply {
                                gravity = Gravity.CENTER
                            })
                            addView(gap(this@MainActivity, 2f))
                            addView(body(this@MainActivity,
                                if (h == 1) "hour" else "hours", Brand.T_CAPTION,
                                if (on) 0x99000000.toInt() else Brand.INK_30).apply {
                                gravity = Gravity.CENTER
                            })
                        })
                    }
                })
            }
            blocks.add(grid)

            blocks.add(SlideToStart(this, "SLIDE TO LOCK") {
                Lock.startFocus(this, focusHours * 60); refresh()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(this@MainActivity, 68f))
            })
        }


        // --- ways out, only while something is actually locked ---
        if (live || open) {
            val esc = group("Ways out")
            esc.addView(actionRow("Emergency, five minutes",
                "${Lock.emergenciesLeft(this)} of ${Lock.EMERGENCY_COUNT} left in this lock") {
                startActivity(Intent(this, LockActivity::class.java)
                    .putExtra("mode", "emergency"))
            })
            esc.addView(actionRow("Unlock with a reason",
                "Read on this phone. Genuine gets you up to two hours.", last = true) {
                startActivity(Intent(this, LockActivity::class.java).putExtra("mode", "reason"))
            })
            blocks.add(esc)
        }

        // --- how the lock behaves ---
        val hb = group("While it is locked")
        val colourFixed = Lock.colourSettingLocked(this)
        hb.addView(padded(toggleRow("Drain the colour",
            when {
                colourFixed && Lock.greyOnLock(this) ->
                    "On and held. Cannot be changed until the lock ends."
                colourFixed -> "Off for this lock. Cannot be changed until it ends."
                Lock.greyOnLock(this) ->
                    "On. Stays grey through an emergency and through a reasoned unlock."
                else -> "Off. The phone keeps its colour."
            },
            Lock.greyOnLock(this)) {
            if (!Lock.setGreyOnLock(this, !Lock.greyOnLock(this))) {
                android.widget.Toast.makeText(this,
                    "Not while it is running. That was the point.",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
            refresh() }).apply { alpha = if (colourFixed) 0.45f else 1f })
        hb.addView(Brand.rule(this))
        hb.addView(padded(toggleRow("Show Sheldy's lock screen",
            if (Lock.ownScreen(this)) "On. Our screen, with the clock and the ways out."
            else "Off. Android's grey \"app paused\" dialog instead.",
            Lock.ownScreen(this)) {
            Lock.setOwnScreen(this, !Lock.ownScreen(this)); refresh() }))
        hb.addView(Brand.rule(this))
        hb.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(body(this@MainActivity,
                "Colour is the layer nothing you type can undo. Every other one can be argued " +
                "past, write a good enough sentence and the phone opens, but no reason brings " +
                "the colour back, so a won argument still gets you a grey phone.\n\n" +
                "The power menu is also refused while a lock runs. Holding power and volume down " +
                "to force a restart is not something any app on Android can stop, and this one " +
                "does not pretend otherwise.",
                Brand.T_CAPTION, Brand.INK_30))
        })
        blocks.add(hb)

        // --- allowed apps ---
        val aa = group("Gets through the lock")
        aa.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(body(this@MainActivity,
                "Calls and messages are never suspended, lock or no lock. Nothing here can stop " +
                "the phone being a phone.", Brand.T_CAPTION, Brand.INK_30))
        })
        aa.addView(Brand.rule(this))
        aa.addView(actionRow("Choose three apps",
            Lock.allowed(this).let { a ->
                if (a.isEmpty()) "None chosen yet" else "${a.size} of 3 chosen"
            }, last = true) {
            startActivity(Intent(this, LockActivity::class.java).putExtra("mode", "apps"))
        })
        blocks.add(aa)

        // --- sleep lock ---
        val sl = group("Sleep lock")
        val committed = !Lock.canDisableSleep(this)
        val daysLeft = ((Lock.commitUntil(this) - System.currentTimeMillis()) / 86_400_000L) + 1
        sl.addView(padded(toggleRow("Lock overnight, every night",
            when {
                !Lock.sleepOn(this) -> "Off"
                committed -> "On. Committed for $daysLeft more nights"
                else -> "On. %02d:00 - %02d:00".format(Lock.sleepFrom(this), Lock.sleepTo(this))
            }, Lock.sleepOn(this)) {
            if (!Lock.setSleepOn(this, !Lock.sleepOn(this))) {
                android.widget.Toast.makeText(this,
                    "Committed for $daysLeft more nights. That was the point.",
                    android.widget.Toast.LENGTH_LONG).show()
            }
            refresh() }))
        sl.addView(Brand.rule(this))
        sl.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(body(this@MainActivity,
                "Anyone can set a bedtime. The useful part is not being able to unset it at " +
                "midnight, when the version of you deciding is the one the rule exists for.",
                Brand.T_CAPTION, Brand.INK_30))
            addView(gap(this@MainActivity, S4))
            addView(row(this@MainActivity).apply {
                addView(col(this@MainActivity).apply {
                    layoutParams = grow()
                    addView(eyebrow(this@MainActivity, "From"))
                    addView(gap(this@MainActivity, S2))
                    addView(stepper(Lock.sleepFrom(this@MainActivity)) { n ->
                        Lock.setSleepWindow(this@MainActivity, (n + 24) % 24,
                            Lock.sleepTo(this@MainActivity)); refresh() })
                })
                addView(col(this@MainActivity).apply {
                    layoutParams = grow()
                    addView(eyebrow(this@MainActivity, "Until"))
                    addView(gap(this@MainActivity, S2))
                    addView(stepper(Lock.sleepTo(this@MainActivity)) { n ->
                        Lock.setSleepWindow(this@MainActivity,
                            Lock.sleepFrom(this@MainActivity), (n + 24) % 24); refresh() })
                })
            })
            if (!Lock.sleepOn(this@MainActivity)) {
                addView(gap(this@MainActivity, S4))
                addView(eyebrow(this@MainActivity, "Nights committed"))
                addView(gap(this@MainActivity, S2))
                addView(stepper(Lock.commitDays(this@MainActivity)) { n ->
                    Lock.setCommitDays(this@MainActivity, n); refresh() })
            }
        })
        blocks.add(sl)

        // --- the ledger ---
        val led = Lock.ledger(this)
        if (led.isNotEmpty()) {
            val granted = led.filter { it.minutes > 0 }
            val lg = group("Every unlock")
            lg.addView(col(this).apply {
                setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                    dp(this@MainActivity, S5), dp(this@MainActivity, S4))
                // The count and the total are the whole point. One unlock is a Tuesday; nine
                // unlocks and four hours is a pattern, and you only see it if it is added up
                // for you.
                addView(row(this@MainActivity).apply {
                    addView(col(this@MainActivity).apply {
                        layoutParams = grow()
                        addView(Brand.mono(this@MainActivity, "${led.size}", 26f))
                        addView(gap(this@MainActivity, 2f))
                        addView(eyebrow(this@MainActivity,
                            if (led.size == 1) "asked" else "asked"))
                    })
                    addView(col(this@MainActivity).apply {
                        layoutParams = grow()
                        addView(Brand.mono(this@MainActivity, "${granted.size}", 26f))
                        addView(gap(this@MainActivity, 2f))
                        addView(eyebrow(this@MainActivity, "opened"))
                    })
                    addView(col(this@MainActivity).apply {
                        layoutParams = grow()
                        addView(Brand.mono(this@MainActivity,
                            "${granted.sumOf { it.minutes }}", 26f))
                        addView(gap(this@MainActivity, 2f))
                        addView(eyebrow(this@MainActivity, "minutes"))
                    })
                })
                addView(gap(this@MainActivity, S4))
                addView(body(this@MainActivity,
                    "The lock can always be opened, a phone you cannot unlock in a real " +
                    "emergency is dangerous. The friction is not the barrier, it is writing " +
                    "down why and then reading back every other time you said the same thing.",
                    Brand.T_CAPTION, Brand.INK_30))
            })
            lg.addView(Brand.rule(this))

            val shown = led.take(12)
            shown.forEachIndexed { i, e ->
                val ok = e.minutes > 0
                lg.addView(row(this).apply {
                    setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                        dp(this@MainActivity, S5), dp(this@MainActivity, S4))
                    addView(col(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                            .apply { marginEnd = dp(this@MainActivity, S4) }
                        addView(eyebrow(this@MainActivity,
                            java.text.SimpleDateFormat("d MMM · HH:mm", java.util.Locale.UK)
                                .format(java.util.Date(e.at))))
                        addView(gap(this@MainActivity, S2))
                        addView(body(this@MainActivity, e.reason, Brand.T_CAPTION,
                            if (ok) Brand.INK_80 else Brand.INK_45).apply {
                            maxLines = 3
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setLineSpacing(dp(this@MainActivity, 4f).toFloat(), 1f)
                        })
                    })
                    // Filled when it opened, hollow when it did not. Same language as everywhere.
                    addView(title(this@MainActivity,
                        if (ok) "${e.minutes} MIN" else "NO", Brand.T_EYEBROW,
                        if (ok) Brand.BLACK else Brand.INK_30).apply {
                        gravity = Gravity.CENTER
                        letterSpacing = 0.1f
                        background = Brand.surface(this@MainActivity, Brand.R_TILE,
                            if (ok) Brand.INK else 0x00000000,
                            if (ok) Brand.INK else Brand.HAIRLINE)
                        setPadding(dp(this@MainActivity, S3), dp(this@MainActivity, S2),
                            dp(this@MainActivity, S3), dp(this@MainActivity, S2))
                        minWidth = dp(this@MainActivity, 62f)
                    })
                })
                if (i < shown.size - 1) lg.addView(View(this).apply {
                    setBackgroundColor(Brand.HAIRLINE)
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT,
                        dp(this@MainActivity, 1f))
                        .apply { marginStart = dp(this@MainActivity, S5) }
                })
            }
            blocks.add(lg)
        }

        blocks.forEach { v ->
            if (v.parent == null) {
                b.addView(v)
                b.addView(gap(this, S4))
            }
        }
        p.addView(b)
        if (!isRefresh) Anim.stagger(blocks)
        return p
    }

    /** When tonight's sleep window closes, as an absolute time. */
    private fun sleepEndMillis(): Long {
        val c = java.util.Calendar.getInstance()
        val to = Lock.sleepTo(this)
        if (c.get(java.util.Calendar.HOUR_OF_DAY) >= to) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
        c.set(java.util.Calendar.HOUR_OF_DAY, to)
        c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0)
        return c.timeInMillis
    }

    // ---- Progress --------------------------------------------------------

    private fun progressTab(): LinearLayout {
        sub?.let { key ->
            if (key == "collection") return collectionView()
            val id = key.removePrefix("badge:")
            Achievements.ALL.firstOrNull { it.id == id }?.let { return badgeDetailView(it) }
            sub = null
        }
        val p = col(this).apply { setBackgroundColor(Brand.BLACK) }
        val features = Features.all(this)
        val done = Features.done(features)

        // A contribution calendar, not a photograph and not a bar chart. Every day since you
        // started, at a glance, solid means clean, struck means relapse, faint means the day
        // went unconfirmed.
        //
        // Stacked, not overlaid: the numbers sat on top of the grid and collided with it, and no
        // amount of scrim rescues type printed over a field of squares.
        p.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S6), topInset + dp(this@MainActivity, S8),
                dp(this@MainActivity, S6), 0)
            addView(eyebrow(this@MainActivity, "Every day since you started"))
            addView(gap(this@MainActivity, S3))
            addView(Brand.display(this@MainActivity, "The record."))
            addView(gap(this@MainActivity, S6))
            addView(row(this@MainActivity).apply {
                addView(inlineStat("${Store.streak(this@MainActivity)}", "current"))
                addView(inlineStat("${Store.bestStreak(this@MainActivity)}", "best"))
                addView(inlineStat("${Store.relapseTotal(this@MainActivity)}", "relapses"))
                addView(inlineStat("${Store.panics(this@MainActivity)}", "urges held"))
            })
            addView(gap(this@MainActivity, S6))
            addView(HeatGrid(this@MainActivity, Store.heatDays(this@MainActivity, 18),
                animate = !isRefresh).apply {
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT, dp(this@MainActivity, 132f))
            })
            addView(gap(this@MainActivity, S8))
        })

        val b = contentCol()
        val blocks = mutableListOf<View>()

        // The collection replaces the old feature checklist. A build checklist is a developer
        // artefact; a wall of medallions you are slowly filling in is the thing that actually
        // pulls someone back tomorrow.
        val held = Achievements.tiersHeld(this)
        val coll = card(this)
        coll.addView(row(this).apply {
            addView(col(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(this@MainActivity, S4) }
                addView(eyebrow(this@MainActivity, "Kings of Greece"))
                addView(gap(this@MainActivity, S2))
                addView(title(this@MainActivity, "The collection", Brand.T_SECTION))
                addView(gap(this@MainActivity, 5f))
                addView(body(this@MainActivity,
                    "$held of ${Achievements.tiersTotal()} tiers across ${Achievements.ALL.size} kings",
                    Brand.T_CAPTION, Brand.INK_30))
            })
            addView(title(this@MainActivity, "\u203A", 19f, Brand.INK_30))
        })
        coll.addView(gap(this, S4))
        // A strip of the nearest medals, earned ones first.
        coll.addView(row(this).apply {
            Achievements.ALL
                .sortedByDescending { it.tier(this@MainActivity) * 10 + it.fraction(this@MainActivity) }
                .take(4)
                .forEach { bd ->
                    addView(badgeMedal(bd, 62f).apply {
                        (layoutParams as LinearLayout.LayoutParams).rightMargin =
                            dp(this@MainActivity, S3)
                    })
                }
        })
        coll.isClickable = true
        Anim.pressable(coll)
        coll.setOnClickListener { sub = "collection"; show(3) }
        blocks.add(coll)

        val prog = card(this)
        prog.addView(row(this).apply {
            addView(eyebrow(this@MainActivity, "Build progress").apply { layoutParams = grow() })
            addView(title(this@MainActivity, "$done / ${features.size}", Brand.T_BODY))
        })
        prog.addView(gap(this, S4))
        val railFill = View(this).apply { background = Brand.pill(this@MainActivity, Brand.INK) }
        prog.addView(row(this).apply {
            background = Brand.pill(this@MainActivity, Brand.HAIRLINE)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(this@MainActivity, 6f))
            addView(railFill, LinearLayout.LayoutParams(0, MATCH_PARENT, 0.001f))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(
                0, MATCH_PARENT, (features.size - done).toFloat()))
        })
        blocks.add(prog)
        if (isRefresh) {
            (railFill.layoutParams as LinearLayout.LayoutParams).weight = done.toFloat()
            railFill.requestLayout()
        } else Anim.growRail(railFill, done.toFloat(), features.size.toFloat())

        // Patterns, refuses to draw conclusions from fewer than three relapses.
        val pat = card(this)
        pat.addView(eyebrow(this, "Your patterns"))
        pat.addView(gap(this, S3))
        if (!Patterns.enoughData(this)) {
            pat.addView(title(this, "Not enough data yet.", Brand.T_BODY, Brand.INK_60))
            pat.addView(gap(this, S2))
            pat.addView(body(this,
                "After ${Patterns.MIN_DATA} logged relapses this shows your own hours and " +
                "triggers. Drawing a pattern from one is guesswork dressed up as insight.",
                Brand.T_CAPTION, Brand.INK_30))
        } else {
            Patterns.headline(this)?.let {
                pat.addView(title(this, it, Brand.T_BODY))
                pat.addView(gap(this, S4))
            }
            pat.addView(eyebrow(this, "By time of day"))
            pat.addView(gap(this, S2))
            Patterns.byTimeOfDay(this).forEach { pat.addView(patternRow(it)) }
            pat.addView(gap(this, S4))
            pat.addView(eyebrow(this, "By trigger"))
            pat.addView(gap(this, S2))
            Patterns.byTrigger(this).take(5).forEach { pat.addView(patternRow(it)) }
        }
        blocks.add(pat)

        blocks.distinct().forEach { v ->
            if (v.parent == null) {
                b.addView(v, Brand.fill().apply { bottomMargin = dp(this@MainActivity, S4) })
            }
        }
        p.addView(b)
        if (!isRefresh) Anim.stagger(blocks, 40L, 120L)
        return p
    }

    /** Label, proportional bar, count. Bars are fill-only, so greyscale changes nothing. */
    private fun patternRow(p: Pattern): View = col(this).apply {
        setPadding(0, dp(this@MainActivity, S2), 0, dp(this@MainActivity, S2))
        addView(row(this@MainActivity).apply {
            addView(body(this@MainActivity, p.label, Brand.T_CAPTION, Brand.INK_60)
                .apply { layoutParams = grow() })
            addView(Brand.mono(this@MainActivity, "${p.count}", 13f, Brand.INK))
        })
        addView(gap(this@MainActivity, 6f))
        addView(row(this@MainActivity).apply {
            background = Brand.surface(this@MainActivity, 2f, Brand.HAIRLINE, 0x00000000)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(this@MainActivity, 5f))
            addView(View(this@MainActivity).apply {
                background = Brand.surface(this@MainActivity, 2f, Brand.INK, 0x00000000)
                layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT,
                    p.share.coerceAtLeast(0.02f))
            })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, MATCH_PARENT,
                (1f - p.share).coerceAtLeast(0.02f)))
        })
    }

    // ---- collection, hosted inside the Progress tab -----------------------

    private fun collectionView(): LinearLayout {
        val p = col(this).apply { setBackgroundColor(Brand.BLACK) }
        val body = contentCol().apply {
            setPadding(dp(this@MainActivity, S6), topInset + dp(this@MainActivity, S8),
                dp(this@MainActivity, S6), dp(this@MainActivity, 112f))
        }
        val held = Achievements.tiersHeld(this)

        body.addView(row(this).apply {
            addView(eyebrow(this@MainActivity, "Kings of Greece").apply { layoutParams = grow() })
            addView(title(this@MainActivity, "Back", Brand.T_CAPTION, Brand.INK_45).apply {
                background = Brand.surface(this@MainActivity, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
                setPadding(dp(this@MainActivity, S4), dp(this@MainActivity, S2),
                    dp(this@MainActivity, S4), dp(this@MainActivity, S2))
                minHeight = dp(this@MainActivity, 40f)
                gravity = Gravity.CENTER
                isClickable = true
                Anim.pressable(this)
                setOnClickListener { sub = null; show(3) }
            })
        })
        body.addView(gap(this, S3))
        body.addView(Brand.display(this, "The collection."))
        body.addView(gap(this, S4))
        body.addView(row(this).apply {
            addView(inlineStat("$held", "of ${Achievements.tiersTotal()} tiers"))
            addView(inlineStat("${Achievements.earned(this@MainActivity)}", "of ${Achievements.ALL.size} kings"))
        })
        body.addView(gap(this, S4))
        body.addView(primary("Share the collection") { shareCollection() })
        body.addView(gap(this, S6))

        // Held badges first, then the rest. Declaration order put a locked king above one you had
        // actually earned, so the thing you came to look at was never at the top.
        //
        // Held sort by tier, best first. Locked sort by how close they are, so the next one within
        // reach leads that section rather than whichever happens to be defined first.
        val (heldBadges, lockedBadges) = Achievements.ALL.partition { it.tier(this) > 0 }
        val ordered = heldBadges.sortedWith(
            compareByDescending<Badge> { it.tier(this) }.thenByDescending { it.fraction(this) }
        ) + lockedBadges.sortedByDescending { it.fraction(this) }

        val rows = mutableListOf<View>()
        var headed = false
        ordered.forEachIndexed { i, b ->
            if (i == 0 && heldBadges.isNotEmpty()) {
                body.addView(eyebrow(this, "HELD  ·  ${heldBadges.size}", Brand.INK_45))
                body.addView(gap(this, S3))
            }
            if (!headed && b.tier(this) == 0 && heldBadges.isNotEmpty()) {
                headed = true
                body.addView(gap(this, S4))
                body.addView(eyebrow(this, "STILL TO EARN  ·  ${lockedBadges.size}", Brand.INK_45))
                body.addView(gap(this, S3))
            }
            val c = card(this)
            c.addView(badgeRow(b) { sub = "badge:${b.id}"; show(3) })
            rows.add(c)
            body.addView(c, Brand.fill().apply { bottomMargin = dp(this@MainActivity, S2) })
        }
        p.addView(body)
        if (!isRefresh) Anim.stagger(rows.take(8), 34L, 100L)
        return p
    }

    private fun badgeDetailView(badge: Badge): LinearLayout {
        val p = col(this).apply { setBackgroundColor(Brand.BLACK) }
        val body = contentCol().apply {
            setPadding(dp(this@MainActivity, S6), topInset + dp(this@MainActivity, S8),
                dp(this@MainActivity, S6), dp(this@MainActivity, 112f))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        body.addView(badgeMedal(badge, 170f))
        body.addView(gap(this, S6))
        body.addView(eyebrow(this, badge.tierName(this), Brand.INK_60)
            .apply { gravity = Gravity.CENTER })
        body.addView(gap(this, S2))
        body.addView(Brand.display(this, badge.king, 34f).apply { gravity = Gravity.CENTER })
        body.addView(gap(this, S2))
        body.addView(title(this, badge.title, Brand.T_TITLE, Brand.INK_60)
            .apply { gravity = Gravity.CENTER })
        body.addView(gap(this, S6))
        body.addView(body(this, badge.myth, Brand.T_BODY, Brand.INK_80)
            .apply { gravity = Gravity.CENTER })
        body.addView(gap(this, S6))
        body.addView(Brand.rule(this))
        body.addView(gap(this, S4))
        body.addView(eyebrow(this, "How it is earned"))
        body.addView(gap(this, S2))
        body.addView(body(this, badge.how, Brand.T_CAPTION, Brand.INK_60))
        body.addView(gap(this, S4))
        badge.tiers.forEachIndexed { i, t ->
            val got = badge.value(this) >= t
            body.addView(row(this).apply {
                setPadding(0, dp(this@MainActivity, S2), 0, dp(this@MainActivity, S2))
                addView(eyebrow(this@MainActivity, Badge.TIERS[i + 1],
                    if (got) Brand.INK else Brand.INK_30).apply { layoutParams = grow() })
                addView(Brand.mono(this@MainActivity, "$t ${badge.unitFor(t)}", 13f,
                    if (got) Brand.INK else Brand.INK_30))
            })
        }
        body.addView(gap(this, S6))
        body.addView(ghost("Back to the collection") { sub = "collection"; show(3) })
        p.addView(body)
        if (!isRefresh) Anim.enter(body)
        return p
    }

    /** Renders a card off-screen and publishes it through MediaStore. */
    private fun shareCollection() {
        val card = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@MainActivity, S8), dp(this@MainActivity, S8),
                dp(this@MainActivity, S8), dp(this@MainActivity, S8))
            addView(eyebrow(this@MainActivity, "Sheldy"))
            addView(gap(this@MainActivity, S3))
            addView(Brand.hero(this@MainActivity, "${Store.streak(this@MainActivity)}", 76f))
            addView(gap(this@MainActivity, S1))
            addView(eyebrow(this@MainActivity, "days clean", Brand.INK_45))
            addView(gap(this@MainActivity, S6))
            val earned = Achievements.ALL.filter { it.tier(this@MainActivity) > 0 }.take(12)
            earned.chunked(4).forEach { r ->
                addView(row(this@MainActivity).apply {
                    layoutParams = Brand.fill()
                        .apply { bottomMargin = dp(this@MainActivity, S2) }
                    r.forEach { bd ->
                        addView(badgeMedal(bd, 64f).apply {
                            (layoutParams as LinearLayout.LayoutParams).rightMargin =
                                dp(this@MainActivity, S2)
                        })
                    }
                })
            }
            addView(gap(this@MainActivity, S4))
            addView(Brand.rule(this@MainActivity))
            addView(gap(this@MainActivity, S4))
            addView(title(this@MainActivity,
                "${Achievements.tiersHeld(this@MainActivity)} of " +
                    "${Achievements.tiersTotal()} tiers · " +
                    "${Achievements.earned(this@MainActivity)} of ${Achievements.ALL.size} kings",
                Brand.T_BODY, Brand.INK_60))
        }
        val w = dp(this, 380f)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        card.layout(0, 0, w, card.measuredHeight)
        val bmp = android.graphics.Bitmap.createBitmap(
            w, card.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.BLACK)
        card.draw(canvas)

        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "sheldy-collection.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sheldy")
        }
        val uri = contentResolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        runCatching {
            contentResolver.openOutputStream(uri)?.use {
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT,
                    "${Store.streak(this@MainActivity)} days clean. " +
                        "${Achievements.tiersHeld(this@MainActivity)} of " +
                        "${Achievements.tiersTotal()} tiers earned.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share your collection"))
        }.onFailure { runCatching { contentResolver.delete(uri, null, null) } }
    }

    /** Headline numeral over a small-caps label. Black weight, tight tracking, tabular figures. */
    /**
     * Headline numeral over a small-caps label, centred inside its column.
     *
     * Left alignment across four equal-width columns left a visible gap after short labels
     * (BEST) and none after long ones (URGES HELD), so the numbers stopped reading as a row.
     */
    private fun inlineStat(value: String, label: String): View =
        col(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER_HORIZONTAL
            minimumHeight = dp(this@MainActivity, 86f)
            addView(Brand.hero(this@MainActivity, value, 36f).apply {
                gravity = Gravity.CENTER
                layoutParams = Brand.fill()
            })
            addView(gap(this@MainActivity, 6f))
            addView(eyebrow(this@MainActivity, label, Brand.INK_45).apply {
                gravity = Gravity.CENTER
                layoutParams = Brand.fill()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                letterSpacing = 0.1f
            })
        }

    private fun statTile(value: String, label: String): View =
        card(this, Brand.SURFACE, Brand.R_TILE).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(mono(this@MainActivity, value, 30f))
            addView(gap(this@MainActivity, S1))
            addView(eyebrow(this@MainActivity, label))
        }

    // ---- Settings --------------------------------------------------------

    /** A settings card: one eyebrow, a rule, then rows. Every group is built this way. */
    private fun group(label: String): LinearLayout = card(this).apply {
        setPadding(0, 0, 0, 0)
        addView(eyebrow(this@MainActivity, label).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
        })
        addView(Brand.rule(this@MainActivity))
    }

    /** Tappable settings row. Reads as a list, not a stack of buttons. */
    private fun actionRow(
        label: String, sub: String, last: Boolean = false, quiet: Boolean = false,
        onTap: () -> Unit
    ): View = col(this).apply {
        addView(row(this@MainActivity).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            minimumHeight = dp(this@MainActivity, Brand.MIN_ROW)
            isClickable = true
            Anim.pressable(this)
            setOnClickListener { onTap() }
            addView(col(this@MainActivity).apply {
                // End margin so the label never crowds the chevron. Text touching a glyph is
                // what made these rows feel cramped.
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(this@MainActivity, S4) }
                addView(title(this@MainActivity, label, Brand.T_BODY,
                    if (quiet) Brand.INK_60 else Brand.INK))
                if (sub.isNotEmpty()) {
                    addView(gap(this@MainActivity, 5f))
                    addView(body(this@MainActivity, sub, Brand.T_CAPTION, Brand.INK_30)
                        .apply { setLineSpacing(dp(this@MainActivity, 4f).toFloat(), 1f) })
                }
            })
            addView(title(this@MainActivity, "\u203A", 19f, Brand.INK_30).apply {
                setPadding(dp(this@MainActivity, S2), 0, 0, 0)
            })
        })
        if (!last) addView(View(this@MainActivity).apply {
            setBackgroundColor(Brand.HAIRLINE)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(this@MainActivity, 1f))
                .apply { marginStart = dp(this@MainActivity, S5) }
        })
    }

    private fun padded(v: View): View = col(this).apply {
        setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
            dp(this@MainActivity, S5), dp(this@MainActivity, S4))
        addView(v)
    }

    private fun settingsTab(): LinearLayout {
        val p = col(this).apply { setBackgroundColor(Brand.BLACK) }
        val out = body(this, "", Brand.T_CAPTION, Brand.INK_45)
        val owner = Lockdown.status(this).endsWith("YES")

        p.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S6), topInset + dp(this@MainActivity, S8),
                dp(this@MainActivity, S6), 0)
            addView(eyebrow(this@MainActivity, "Settings"))
            addView(gap(this@MainActivity, S3))
            addView(Brand.display(this@MainActivity, "Controls."))
            addView(gap(this@MainActivity, S2))
            addView(body(this@MainActivity,
                if (owner) "Device Owner. Policies are enforced by the system."
                else "Not Device Owner. Run the adb command.",
                Brand.T_CAPTION, Brand.INK_45))
        })

        val b = contentCol()
        val blocks = mutableListOf<View>()

        val partnerName = EditText(this).apply {
            setText(Partner.name(this@MainActivity))
            setHint("Their name")
            setHintTextColor(Brand.INK_30); setTextColor(Brand.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Brand.T_BODY)
            background = Brand.surface(this@MainActivity, Brand.R_TILE, Brand.SURFACE)
            setPadding(dp(this@MainActivity, S4), dp(this@MainActivity, S3),
                dp(this@MainActivity, S4), dp(this@MainActivity, S3))
            minHeight = dp(this@MainActivity, Brand.MIN_TAP)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            layoutParams = Brand.fill()
        }
        val partnerNumber = EditText(this).apply {
            setText(Partner.number(this@MainActivity))
            setHint("Their number")
            setHintTextColor(Brand.INK_30); setTextColor(Brand.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Brand.T_BODY)
            background = Brand.surface(this@MainActivity, Brand.R_TILE, Brand.SURFACE)
            setPadding(dp(this@MainActivity, S4), dp(this@MainActivity, S3),
                dp(this@MainActivity, S4), dp(this@MainActivity, S3))
            minHeight = dp(this@MainActivity, Brand.MIN_TAP)
            inputType = InputType.TYPE_CLASS_PHONE
            layoutParams = Brand.fill()
        }

        // --- check-ins ---
        val rem = group("Check-ins")
        rem.addView(padded(toggleRow("Daily reminders",
            if (Store.remindersOn(this)) "Three a day, shown on the lock screen"
            else "Off. Nothing will prompt you.",
            Store.remindersOn(this)) {
            Store.setReminders(this, !Store.remindersOn(this)); refresh() }))
        rem.addView(Brand.rule(this))
        rem.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S3),
                dp(this@MainActivity, S5), dp(this@MainActivity, S3))
            addView(tableRow("Morning · sets the intention", "09:00"))
            addView(tableRow("Evening · the post-work dip", "17:00"))
            addView(tableRow("Night · before the hard hours", "21:00", last = true))
        })
        blocks.add(rem)

        // --- the day ---
        val day = group("The day")
        day.addView(padded(row(this).apply {
            addView(col(this@MainActivity).apply {
                layoutParams = grow()
                addView(title(this@MainActivity, "Thoughts per day", Brand.T_BODY))
                addView(gap(this@MainActivity, 3f))
                addView(body(this@MainActivity, "More on the harder days.",
                    Brand.T_CAPTION, Brand.INK_30))
            })
            addView(stepper(Store.thoughtsPerDay(this@MainActivity)) { n ->
                Store.setThoughtsPerDay(this@MainActivity, n); refresh() })
        }))
        day.addView(Brand.rule(this))
        val canMark = Store.canConfirm(Store.todayKey())
        day.addView(actionRow(
            "Mark today clean",
            if (canMark) "Confirms today as a counted day"
            else "Unlocks at 21:00, a day has to be finished first",
            quiet = !canMark
        ) {
            if (Store.confirmClean(this, Store.todayKey())) show(0)
            else out.text = "Not yet. Today can be confirmed after 21:00."
        })
        day.addView(actionRow("I relapsed", "Log the trigger, reset the count", last = true) {
            startActivity(Intent(this, RelapseActivity::class.java)
                .putExtra("dayKey", Store.todayKey()))
        })
        blocks.add(day)

        // --- companion ---
        val ai = group("Companion")
        ai.addView(actionRow("Open the chat", "Talk it through right now") {
            startActivity(Intent(this, ChatActivity::class.java))
        })
        val keyField = EditText(this).apply {
            setText(Coach.key(this@MainActivity).take(10) + "…")
            setHint("Paste a Gemini API key")
            setHintTextColor(Brand.INK_30)
            setTextColor(Brand.INK_60)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Brand.T_CAPTION)
            background = Brand.surface(this@MainActivity, Brand.R_TILE, Brand.SURFACE)
            setPadding(dp(this@MainActivity, S4), dp(this@MainActivity, S3),
                dp(this@MainActivity, S4), dp(this@MainActivity, S3))
            minHeight = dp(this@MainActivity, 48f)
            layoutParams = Brand.fill()
        }
        ai.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(body(this@MainActivity,
                "A key starting \"AIza\" never expires. One starting \"AQ.\" is a temporary " +
                "token and dies after about an hour.", Brand.T_CAPTION, Brand.INK_30))
            addView(gap(this@MainActivity, S3))
            addView(keyField)
            addView(gap(this@MainActivity, S3))
            addView(ghost("Save key") {
                val k = keyField.text.toString().trim()
                if (k.length > 20) { Coach.setKey(this@MainActivity, k); out.text = "Key saved." }
                else out.text = "That does not look like a key."
            })
        })
        blocks.add(ai)

        // --- accountability partner ---
        val pt = group("Accountability")
        pt.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(body(this@MainActivity,
                "Secrecy is the condition this needs. One person knowing shifts the odds more " +
                "than anything technical on this phone.", Brand.T_CAPTION, Brand.INK_30))
            addView(gap(this@MainActivity, S3))
            addView(partnerName)
            addView(gap(this@MainActivity, S2))
            addView(partnerNumber)
            addView(gap(this@MainActivity, S3))
            addView(ghost("Save partner") {
                Partner.save(this@MainActivity,
                    partnerName.text.toString(), partnerNumber.text.toString())
                out.text = if (Partner.configured(this@MainActivity))
                    "Saved. Sheldy will offer to message them." else "Enter a number."
            })
        })
        if (Partner.configured(this)) {
            pt.addView(Brand.rule(this))
            pt.addView(padded(toggleRow("Tell them when I relapse",
                "Opens your messaging app with the text ready. Nothing sends by itself.",
                Partner.notifyOnRelapse(this)) {
                Partner.setNotifyOnRelapse(this, !Partner.notifyOnRelapse(this)); refresh() }))
            pt.addView(actionRow("Send a check-in now",
                "Streak, best, relapses, urges held", last = true) {
                Partner.compose(this, Partner.weeklyMessage(this))
            })
        }
        blocks.add(pt)

        // The focus and sleep locks used to be duplicated here. They now live on their own
        // page under Shield, because a lock that suspends the whole phone is not a setting.

        // --- scheduled blackout ---
        val sc = group("Nightly blackout")
        sc.addView(padded(toggleRow("Hide the browser overnight",
            if (Schedule.enabled(this)) "Active ${Schedule.describe(this)}"
            else "Off. The watchdog still runs.",
            Schedule.enabled(this)) {
            Schedule.setEnabled(this, !Schedule.enabled(this)); refresh() }))
        sc.addView(Brand.rule(this))
        sc.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(body(this@MainActivity,
                "Blocking a search is reactive, you still wanted it and typed it. During these " +
                "hours the browser is removed outright, so there is nothing to type into.\n\n" +
                "Know the cost before turning this on. Chrome is also the in-app browser every " +
                "other app borrows, so hiding it breaks logins elsewhere. LinkedIn falls back to " +
                "a bare WebView, reports your device as an unknown browser on an unknown " +
                "operating system, and refuses the login. Banking and sign-in-with-Google flows " +
                "degrade the same way. Off by default for that reason.",
                Brand.T_CAPTION, Brand.INK_30))
            addView(gap(this@MainActivity, S4))
            addView(row(this@MainActivity).apply {
                addView(col(this@MainActivity).apply {
                    layoutParams = grow()
                    addView(eyebrow(this@MainActivity, "From"))
                    addView(gap(this@MainActivity, S2))
                    addView(stepper(Schedule.from(this@MainActivity)) { n ->
                        Schedule.setWindow(this@MainActivity, n,
                            Schedule.to(this@MainActivity)); refresh() })
                })
                addView(col(this@MainActivity).apply {
                    layoutParams = grow()
                    addView(eyebrow(this@MainActivity, "Until"))
                    addView(gap(this@MainActivity, S2))
                    addView(stepper(Schedule.to(this@MainActivity)) { n ->
                        Schedule.setWindow(this@MainActivity,
                            Schedule.from(this@MainActivity), n); refresh() })
                })
            })
        })
        blocks.add(sc)

        // --- commitment ---
        val pl = group("Commitment")
        pl.addView(actionRow(
            if (Store.pledgeSigned(this)) "Signed" else "Sign the commitment",
            if (Store.pledgeSigned(this)) "Read it again, or sign it fresh"
            else "Ten seconds, and it measurably sticks",
            last = true
        ) { startActivity(Intent(this, PledgeActivity::class.java)) })
        blocks.add(pl)

        val streak = Store.streak(this)

        // --- backup ---
        val bk = group("Device backup")
        bk.addView(padded(toggleRow("Allow backup",
            if (Lockdown.backupEnabled(this)) "On. Backup and restore work normally."
            else "Blocked by Device Owner, this is why backup says \"security policy\".",
            Lockdown.backupEnabled(this)) {
            val now = Lockdown.setBackupEnabled(this, !Lockdown.backupEnabled(this))
            out.text = if (now) "Backup enabled. Restore from Settings or Smart Switch."
                       else "Backup disabled."
            refresh()
        }))
        bk.addView(Brand.rule(this))
        bk.addView(actionRow("Export my data", "Streak, calendar, notes and badges to a file") {
            exportData()
        })
        bk.addView(actionRow("Restore from a file", "Pick an export to merge back in",
            last = true) {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                }, REQ_IMPORT)
        })
        bk.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(body(this@MainActivity,
                "Turning the toggle on lifts Android's own block. Google's backup transport " +
                "still refuses separately, because provisioning any Device Owner flags the phone " +
                "as organisation-owned and Google will not sync those to a personal account. " +
                "That is Google's rule and no app can override it.\n\n" +
                "Unaffected: Google Photos, WhatsApp's own Drive backup, contacts and calendar " +
                "sync, and Smart Switch to a PC or SD card. Only app-settings sync is lost.\n\n" +
                "Export writes everything this app knows to a file you keep.",
                Brand.T_CAPTION, Brand.INK_30))
        })
        blocks.add(bk)

        // --- screen scanning ---
        val sg = group("Screen scanning")
        sg.addView(padded(toggleRow("Watch the screen",
            if (FrameScan.enabled(this))
                "On for ${FrameScan.daysLeft(this)} more " +
                    (if (FrameScan.daysLeft(this) == 1) "day" else "days") +
                    ", image-feed apps only."
            else "Off. DNS and keywords cannot see inside image feeds.",
            FrameScan.enabled(this)) {
            if (FrameScan.enabled(this)) {
                FrameScan.setEnabled(this, false)
                stopService(Intent(this, ScreenGuardService::class.java))
                refresh() } else {
                startActivity(Intent(this, ScreenGuardActivity::class.java))
            }
        }))
        sg.addView(Brand.rule(this))
        sg.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S4),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(body(this@MainActivity,
                "The only layer that can see inside Instagram, Reddit or Telegram, where the " +
                "domain is legitimate and there is no text to read.\n\n" +
                "It does not watch your whole phone. Frames are only read while one of " +
                "${FrameScan.WATCHED.size} image-feed apps is in front, banking, messages, " +
                "maps and the camera are never analysed at all.\n\n" +
                "And be clear what it is: a skin-area heuristic, not a trained classifier. It " +
                "needs three consecutive positive frames before it acts, so a face or a beach " +
                "photo scrolling past is ignored. Everything runs on this phone; nothing is " +
                "ever uploaded.\n\n" +
                "It runs on a timer and switches itself off, 21 days maximum, which is roughly " +
                "how long it takes to unpick a daily habit loop. A protection with no end date " +
                "stops being a decision and becomes surveillance you forgot you agreed to.",
                Brand.T_CAPTION, Brand.INK_30))
            addView(gap(this@MainActivity, S3))
            addView(row(this@MainActivity).apply {
                addView(col(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        .apply { marginEnd = dp(this@MainActivity, S3) }
                    addView(title(this@MainActivity, "Run for", Brand.T_BODY))
                    addView(gap(this@MainActivity, 4f))
                    addView(body(this@MainActivity,
                        if (FrameScan.enabled(this@MainActivity))
                            "Switches itself off automatically."
                        else "Days, then it turns itself off. 21 is the cap.",
                        Brand.T_CAPTION, Brand.INK_30))
                })
                addView(stepper(FrameScan.days(this@MainActivity)) { n ->
                    FrameScan.setDays(this@MainActivity, n); refresh() })
            })
            addView(gap(this@MainActivity, S3))
            if (FrameScan.enabled(this@MainActivity)) {
                addView(tableRow("Days remaining", "${FrameScan.daysLeft(this@MainActivity)}"))
            }
            addView(tableRow("Frame threshold",
                "${(FrameScan.AREA_THRESHOLD * 100).toInt()}%"))
            addView(tableRow("Frames to confirm", "${FrameScan.CONFIRM_FRAMES}"))
            addView(tableRow("Apps watched", "${FrameScan.WATCHED.size}", last = true))
            addView(gap(this@MainActivity, S2))
            addView(body(this@MainActivity, FrameScan.selfTest(), Brand.T_EYEBROW, Brand.INK_30))
        })
        blocks.add(sg)

        // --- protection ---
        val prot = group("Protection")
        prot.addView(actionRow("Apply lockdown", "Re-assert every policy") {
            runOff(out) { Lockdown.apply(this) }
        })
        prot.addView(actionRow("Preview the block screen", "See what an urge looks like",
            last = true) {
            startActivity(Intent(this, BlockActivity::class.java).putExtra("reason", "preview"))
        })
        blocks.add(prot)

        // There is deliberately no "release everything" control.
        //
        // A one-tap unlock inside the app is the reason free blockers fail: at 1am you are not
        // the same person who installed it, and a button you can reach in three seconds is a
        // suggestion, not a lock. Lockdown.release() still exists in the source for a genuine
        // emergency, but it is not reachable from the interface. The remaining exits are adb
        // (documented in SETUP.md) and a recovery-mode wipe, and both cost real time, which is
        // the entire point.

        // --- diagnostics ---
        val diag = group("Diagnostics")
        diag.addView(col(this).apply {
            setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S3),
                dp(this@MainActivity, S5), dp(this@MainActivity, S4))
            addView(tableRow("Filter self-test",
                if (Filter.selfTest().contains("pass")) "PASS" else "FAIL"))
            addView(tableRow("Lock self-test",
                if (Lock.selfTest().contains("pass")) "PASS" else "FAIL"))
            addView(tableRow("Day counter self-test",
                if (Store.selfTest().contains("pass")) "PASS" else "FAIL"))
            addView(tableRow("Clean days logged", "${Store.cleanDaysTotal(this@MainActivity)}"))
            addView(tableRow("Notes stored", "${Store.notes(this@MainActivity).size}", last = true))
            addView(gap(this@MainActivity, S3))
            // The sentence model is the one thing here that can fail silently on a given device,
            // wrong ABI, asset stripped, out of memory on a cold start, and the failure is
            // invisible because the word list quietly takes over. So it is checked out loud,
            // against phrasings it has never seen.
            val nlp = body(this@MainActivity, "Sentence model: checking…",
                Brand.T_EYEBROW, Brand.INK_30)
            addView(nlp)
            Thread {
                val r = runCatching { Semantics.selfTest(this@MainActivity) }
                    .getOrDefault("Sentence model: unavailable, word list in use")
                runOnUiThread { if (nlp.isAttachedToWindow) nlp.text = r }
            }.start()
            addView(gap(this@MainActivity, S2))
            addView(out)
        })
        blocks.add(diag)

        // --- what the filter actually fired on ---
        //
        // A false positive is only fixable if you can read back the term that caused it. Without
        // this the only way to find out was to port the matcher to a laptop and run it over a
        // dictionary, because logcat keeps a few minutes and the app kept only a count.
        val trig = Store.triggers(this)
        if (trig.isNotEmpty()) {
            val tg = group("Recent filter triggers")
            tg.addView(col(this).apply {
                setPadding(dp(this@MainActivity, S5), dp(this@MainActivity, S3),
                    dp(this@MainActivity, S5), dp(this@MainActivity, S4))
                val fmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.US)
                trig.take(12).forEachIndexed { i, (at, term, text) ->
                    addView(body(this@MainActivity, "${fmt.format(java.util.Date(at))}  ·  $term",
                        Brand.T_EYEBROW, Brand.INK))
                    addView(body(this@MainActivity, text, Brand.T_EYEBROW, Brand.INK_30))
                    if (i < trig.take(12).size - 1) addView(gap(this@MainActivity, S2))
                }
                addView(gap(this@MainActivity, S3))
                addView(ghost("CLEAR") {
                    Store.clearTriggers(this@MainActivity); refresh()
                })
            })
            blocks.add(tg)
        }

        blocks.distinct().forEach { v ->
            if (v.parent == null) {
                b.addView(v, Brand.fill().apply { bottomMargin = dp(this@MainActivity, S4) })
            }
        }
        p.addView(b)
        if (!isRefresh) Anim.stagger(blocks, 40L, 100L)
        return p
    }

    /** Minus / value / plus, all square. */
    private fun stepper(value: Int, onChange: (Int) -> Unit): View {
        fun btn(label: String, delta: Int): View = title(this, label, 18f).apply {
            gravity = Gravity.CENTER
            background = Brand.tappable(this@MainActivity, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
            layoutParams = LinearLayout.LayoutParams(
                dp(this@MainActivity, 44f), dp(this@MainActivity, 44f))
            isClickable = true
            Anim.pressable(this)
            setOnClickListener { onChange(value + delta) }
        }
        return row(this).apply {
            addView(btn("\u2212", -1))
            addView(Brand.mono(this@MainActivity, "$value", 18f).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    dp(this@MainActivity, 44f), dp(this@MainActivity, 44f))
            })
            addView(btn("+", 1))
        }
    }

    private val REQ_IMPORT = 4411

    /** Write the export to Downloads via MediaStore and offer to share it. */
    private fun exportData() {
        val json = Store.exportJson(this)
        val name = "sheldy-backup-${Store.todayKey()}.json"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Sheldy")
        }
        val uri = contentResolver.insert(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Sheldy backup, $name")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Save your Sheldy backup"))
        }.onFailure { runCatching { contentResolver.delete(uri, null, null) } }
    }

    @Deprecated("startActivityForResult is the simplest route for a one-off file pick")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_IMPORT || res != RESULT_OK) return
        val uri = data?.data ?: return
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return
        Store.importJson(this, text)
        show(4)
    }

    private fun runOff(out: TextView, work: () -> String) {
        out.text = "working…"
        Thread {
            val r = runCatching(work).getOrElse { "crashed: ${it.message}" }
            runOnUiThread { out.text = r }
        }.start()
    }
}

// ------------------------------------------------------------ relapse logger

/** Logging a relapse must be quick and blameless (theme rule 8). Trigger, optional note, done. */
class RelapseActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK
        val key = intent.getStringExtra("dayKey") ?: Store.todayKey()
        var picked = ""

        val p = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@RelapseActivity, S6), dp(this@RelapseActivity, 88f),
                dp(this@RelapseActivity, S6), dp(this@RelapseActivity, S8))
        }
        p.addView(eyebrow(this, "No shame, just data"))
        p.addView(gap(this, S3))
        p.addView(Brand.display(this, "What set it off?"))
        p.addView(gap(this, S2))
        p.addView(body(this, "Naming the trigger is what makes the pattern visible later.",
            Brand.T_BODY, Brand.INK_60))
        p.addView(gap(this, S6))

        val chips = mutableListOf<TextView>()
        val wrap = col(this)
        Triggers.ALL.chunked(2).forEach { pair ->
            val r = row(this).apply {
                layoutParams = Brand.fill().apply { bottomMargin = dp(this@RelapseActivity, S2) }
            }
            pair.forEach { t ->
                val chip = title(this, t, Brand.T_BODY, Brand.INK_60).apply {
                    gravity = Gravity.CENTER
                    background = Brand.surface(this@RelapseActivity, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
                    setPadding(0, dp(this@RelapseActivity, S3), 0, dp(this@RelapseActivity, S3))
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        .apply { rightMargin = dp(this@RelapseActivity, S2) }
                    minHeight = dp(this@RelapseActivity, 48f)
                    isClickable = true
                    Anim.pressable(this)
                }
                chip.setOnClickListener {
                    picked = t
                    chips.forEach { c ->
                        val on = c.text == t
                        c.background = Brand.surface(this@RelapseActivity, Brand.R_TILE,
                            if (on) Brand.INK else 0x00000000, Brand.HAIRLINE)
                        c.setTextColor(if (on) Brand.BLACK else Brand.INK_60)
                    }
                }
                chips.add(chip)
                r.addView(chip)
            }
            wrap.addView(r)
        }
        p.addView(wrap)
        p.addView(gap(this, S4))

        val note = field("Anything else? Optional.", 3)
        p.addView(note)
        p.addView(gap(this, S6))
        p.addView(primary("Log it and move on") {
            Store.logRelapse(this, key, picked, note.text.toString())
            // Offer, never send silently. The user presses send in their own messaging app.
            if (Partner.configured(this) && Partner.notifyOnRelapse(this)) {
                Partner.compose(this, Partner.relapseMessage(this, picked))
            }
            finish()
        })
        p.addView(gap(this, S3))
        p.addView(ghost("Cancel") { finish() })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Brand.BLACK); addView(p)
        })
        Anim.enter(p)
    }
}
