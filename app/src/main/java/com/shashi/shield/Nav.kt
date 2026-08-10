package com.shashi.shield

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The bottom tab bar, shared.
 *
 * It used to live only inside MainActivity, so any screen opened on top of it, the collection,
 * a badge detail, the lesson library, lost the bar entirely and left you with no way back
 * except the system gesture. That breaks the navigation model: the bar is the app's spine and
 * should not vanish because you went one level deeper.
 *
 * Screens that are not MainActivity route a tab tap back to it with the target tab, so the bar
 * behaves identically everywhere.
 */
object Nav {

    const val EXTRA_TAB = "tab"

    val TABS = listOf(
        Triple("Today", R.drawable.ic_today, 0),
        Triple("Shield", R.drawable.ic_shield, 1),
        Triple("Notes", R.drawable.ic_journal, 2),
        Triple("Progress", R.drawable.ic_progress, 3),
        Triple("Settings", R.drawable.ic_settings, 4)
    )

    /**
     * Build the bar. [onSelect] receives the tab index; pass null on secondary screens to get the
     * default behaviour of returning to MainActivity on that tab.
     */
    fun bar(ctx: Context, active: Int, onSelect: ((Int) -> Unit)? = null): LinearLayout {
        val strip = Brand.row(ctx).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(Brand.dp(ctx, Brand.S2), Brand.dp(ctx, Brand.S2),
                Brand.dp(ctx, Brand.S2), Brand.dp(ctx, Brand.S2))
        }

        TABS.forEach { (label, icon, index) ->
            val on = index == active
            val item = Brand.col(ctx).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                minimumHeight = Brand.dp(ctx, 52f)
                background = Brand.tappable(ctx, Brand.R_TILE, 0x00000000, 0x00000000)
                isClickable = true
                setPadding(0, Brand.dp(ctx, Brand.S3), 0, Brand.dp(ctx, Brand.S3))
                setOnClickListener {
                    if (onSelect != null) onSelect(index)
                    else {
                        ctx.startActivity(
                            Intent(ctx, MainActivity::class.java)
                                .putExtra(EXTRA_TAB, index)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                        (ctx as? Activity)?.finish()
                    }
                }
            }
            item.addView(ImageView(ctx).apply {
                setImageResource(icon)
                alpha = if (on) 1f else 0.3f
                layoutParams = LinearLayout.LayoutParams(
                    Brand.dp(ctx, 21f), Brand.dp(ctx, 21f))
            })
            item.addView(Brand.gap(ctx, 5f))
            item.addView(TextView(ctx).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                setTextColor(if (on) Brand.INK else Brand.INK_30)
                typeface = if (on) Brand.semibold(ctx) else Brand.regular(ctx)
                includeFontPadding = false
                gravity = Gravity.CENTER
            })
            strip.addView(item)
        }

        return Brand.col(ctx).apply {
            setBackgroundColor(Brand.BLACK)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            addView(Brand.rule(ctx))
            addView(strip)
        }.also { it.tag = strip }
    }

    /** Content above, bar pinned below, insets handled once. */
    fun scaffold(activity: Activity, content: View, active: Int): View {
        val root = Brand.col(activity).apply { setBackgroundColor(Brand.BLACK) }
        root.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(bar(activity, active))
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        return root
    }
}
