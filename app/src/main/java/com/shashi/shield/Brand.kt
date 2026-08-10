package com.shashi.shield

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Sheldy design system. Strictly monochrome: pure black ground, white ink, greys made only by
 * alpha. No hue anywhere, by rule, the app cannot preach greyscale and then glow violet at you.
 *
 * Spacing follows a 4/8dp rhythm and type follows one scale. Both are enumerated rather than
 * typed inline, because "premium" in practice is mostly just consistent spacing.
 */
object Brand {

    // ---- colour (monochrome only) -----------------------------------------
    const val BLACK = 0xFF000000.toInt()
    const val INK = 0xFFFFFFFF.toInt()
    const val INK_80 = 0xCCFFFFFF.toInt()
    const val INK_60 = 0x99FFFFFF.toInt()
    const val INK_45 = 0x73FFFFFF.toInt()
    const val INK_30 = 0x4DFFFFFF.toInt()

    const val SURFACE = 0x0DFFFFFF      // 5%, resting card
    const val SURFACE_HI = 0x17FFFFFF   // 9%, raised / active card
    const val HAIRLINE = 0x1FFFFFFF     // 12%, borders
    const val RIPPLE = 0x33FFFFFF

    // ---- spacing scale (dp) -----------------------------------------------
    const val S1 = 4f
    const val S2 = 8f
    const val S3 = 12f
    const val S4 = 16f
    const val S5 = 20f
    const val S6 = 24f
    const val S8 = 32f
    const val S10 = 40f
    const val S12 = 48f

    // ---- radius -----------------------------------------------------------
    // Boxy, not rounded. Hard edges read as instrument panel rather than consumer app, and a
    // square grid is far easier to align than a field of pills. Only the one primary action
    // per screen stays fully round, so it remains the single soft shape in a hard interface.
    const val R_CARD = 5f
    const val R_TILE = 3f
    const val R_PILL = 999f
    const val R_HARD = 0f

    // ---- type scale (sp) --------------------------------------------------
    const val T_EYEBROW = 11f
    const val T_CAPTION = 12.5f
    const val T_BODY = 14.5f
    const val T_TITLE = 16.5f
    const val T_SECTION = 21f
    const val T_DISPLAY = 34f
    const val T_HERO = 68f

    fun dp(ctx: Context, v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics
    ).toInt()

    // ---- surfaces ---------------------------------------------------------

    fun surface(ctx: Context, radius: Float = R_CARD, fill: Int = SURFACE,
                stroke: Int = HAIRLINE): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, radius).toFloat()
            setColor(fill)
            setStroke(dp(ctx, 1f), stroke)
        }

    /** Pressed feedback without shifting layout bounds. */
    fun tappable(ctx: Context, radius: Float = R_CARD, fill: Int = SURFACE,
                 stroke: Int = HAIRLINE): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(RIPPLE), surface(ctx, radius, fill, stroke), null)

    fun pill(ctx: Context, fill: Int, stroke: Int = Color.TRANSPARENT) =
        surface(ctx, R_PILL, fill, stroke)

    // ---- type -------------------------------------------------------------

    /**
     * Inter, bundled. The system font is Samsung's One UI Sans here, which is perfectly fine and
     * looks like every other Samsung app, the whole point of a brand is not looking like the OS.
     * Inter also has proper tabular figures, which the day counter and every table depend on.
     *
     * Loaded once and cached: getFont() hits the resource loader, and these are built per row in
     * lists that rebuild on every tab switch.
     */
    private val fonts = HashMap<Int, Typeface>()

    private fun font(ctx: Context, res: Int): Typeface =
        fonts.getOrPut(res) {
            runCatching { ctx.resources.getFont(res) }
                .getOrDefault(Typeface.create("sans-serif", Typeface.NORMAL))
        }

    fun regular(ctx: Context) = font(ctx, R.font.inter_regular)
    fun medium(ctx: Context) = font(ctx, R.font.inter_medium)
    fun semibold(ctx: Context) = font(ctx, R.font.inter_semibold)
    fun black(ctx: Context) = font(ctx, R.font.inter_black)

    private fun text(ctx: Context, s: String, size: Float, color: Int,
                     face: Typeface, tracking: Float): TextView = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        typeface = face
        letterSpacing = tracking
        includeFontPadding = false
        // Tabular figures everywhere: counters and table columns must not reflow as digits change.
        fontFeatureSettings = "tnum"
    }

    // Tracking tightens as size grows, large type needs negative tracking to look set rather
    // than typed, small caps needs positive tracking to stay readable.
    fun hero(ctx: Context, s: String, size: Float = T_HERO) =
        text(ctx, s, size, INK, black(ctx), -0.045f)

    fun display(ctx: Context, s: String, size: Float = T_DISPLAY) =
        text(ctx, s, size, INK, black(ctx), -0.03f)

    fun section(ctx: Context, s: String) =
        text(ctx, s, T_SECTION, INK, semibold(ctx), -0.018f)

    fun title(ctx: Context, s: String, size: Float = T_TITLE, color: Int = INK) =
        text(ctx, s, size, color, medium(ctx), -0.008f)

    fun body(ctx: Context, s: String, size: Float = T_BODY, color: Int = INK_60) =
        text(ctx, s, size, color, regular(ctx), 0f)
            .apply { setLineSpacing(dp(ctx, 4f).toFloat(), 1f) }

    /** All-caps micro label with wide tracking. Section eyebrows only. */
    fun eyebrow(ctx: Context, s: String, color: Int = INK_30) =
        text(ctx, s.uppercase(), T_EYEBROW, color, medium(ctx), 0.18f)

    /** Numerals. Inter's tabular figures, so columns and counters never jitter. */
    fun mono(ctx: Context, s: String, size: Float, color: Int = INK) =
        text(ctx, s, size, color, medium(ctx), -0.01f)

    // ---- layout -----------------------------------------------------------

    fun col(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun row(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun gap(ctx: Context, h: Float): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(ctx, h))
    }

    fun rule(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(HAIRLINE)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(ctx, 1f))
    }

    fun grow() = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
    fun wrap() = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    fun fill() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

    // ---- minimum heights ---------------------------------------------------
    // Every scrollable container gets a floor so it can never collapse to nothing, and so a
    // card does not visibly resize when its contents change (a streak going 9 -> 10, a note
    // wrapping to a second line). Layout that twitches as data updates reads as unfinished.
    const val MIN_CARD = 72f      // any card
    const val MIN_ROW = 56f       // list row
    const val MIN_TAP = 48f       // anything tappable, per the touch-target rule
    const val MIN_PAGE = 560f     // a page's content column, so short tabs still fill the screen

    /** Card with the standard internal padding. Every card in the app uses this. */
    fun card(ctx: Context, fill: Int = SURFACE, radius: Float = R_CARD): LinearLayout =
        col(ctx).apply {
            background = surface(ctx, radius, fill)
            setPadding(dp(ctx, S5), dp(ctx, S5), dp(ctx, S5), dp(ctx, S5))
            minimumHeight = dp(ctx, MIN_CARD)
        }
}
