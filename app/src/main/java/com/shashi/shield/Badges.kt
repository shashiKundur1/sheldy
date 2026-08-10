package com.shashi.shield

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.content.ContentValues
import android.provider.MediaStore
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
import com.shashi.shield.Brand.row
import com.shashi.shield.Brand.title

/**
 * A medallion at a given tier.
 *
 * The artwork is one struck coin per king; the tier is expressed by how much of it you can see.
 * Locked is fully desaturated and dimmed to near-nothing, so the collection reads as a wall of
 * ghosts you are gradually developing, which is the whole appeal of a collection.
 */
fun Context.badgeMedal(badge: Badge, size: Float = 84f): View {
    val tier = badge.tier(this)
    val px = dp(this, size)

    val frame = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(px, px)
    }

    // The coin is a transparent circular cutout that fills its own bounds, so it is inset a
    // little inside the frame, otherwise the tier ring below would be drawn straight over the
    // coin's own milled edge and read as a doubled rim.
    val inset = dp(this, 5f)
    frame.addView(ImageView(this).apply {
        setImageResource(badge.art)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = FrameLayout.LayoutParams(px - inset * 2, px - inset * 2)
            .apply { gravity = Gravity.CENTER }
        // The art is already monochrome; this only controls how present it is.
        // Locked coins stay bright and fully legible, the lock glyph below carries the state,
        // not dimming. Hiding the artwork defeats the point of a collection: you should be able
        // to see exactly what you have not won yet.
        // Locked sits at 0.40: dim enough to read instantly as not-yet-won, bright enough that
        // the relief and the king are still legible. The point of a collection is seeing what is
        // missing, so this must never drop to a silhouette.
        // Locked at 0.30. Dim enough that an unearned king never competes with an earned one,
        // still bright enough to make out the relief, a collection you cannot see is not a
        // collection, it is a list.
        alpha = when (tier) {
            0 -> 0.30f
            1 -> 0.80f
            2 -> 0.88f
            3 -> 0.94f
            else -> 1f
        }

    })

    // Rim: thickness and brightness carry the tier without needing colour.
    frame.addView(View(this).apply {
        background = Brand.surface(
            this@badgeMedal, 999f, 0x00000000,
            when (tier) {
                0 -> 0x1AFFFFFF
                1, 2 -> 0x4DFFFFFF
                3, 4 -> 0x99FFFFFF.toInt()
                else -> Brand.INK
            }
        )
        layoutParams = FrameLayout.LayoutParams(px, px)
    })

    if (tier == 0) {
        // No scrim disc behind the glyph, it read as a black hole punched through the coin.
        // The lock is drawn in black instead, which has plenty of contrast against bright silver
        // and lets the relief stay visible underneath.
        frame.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_lock)
            imageTintList = android.content.res.ColorStateList.valueOf(0xE6000000.toInt())
            layoutParams = FrameLayout.LayoutParams(dp(this@badgeMedal, size * 0.30f),
                dp(this@badgeMedal, size * 0.30f)).apply { gravity = Gravity.CENTER }
        })
    }

    return frame
}

/** One row in the collection: medal, name, myth, and progress toward the next tier. */
fun Context.badgeRow(badge: Badge, onTap: () -> Unit): View {
    val tier = badge.tier(this)
    val r = row(this).apply {
        setPadding(0, dp(this@badgeRow, S3), 0, dp(this@badgeRow, S3))
        minimumHeight = dp(this@badgeRow, 96f)
        isClickable = true
        Anim.pressable(this)
        setOnClickListener { onTap() }
    }
    r.addView(badgeMedal(badge, 72f).apply {
        (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(this@badgeRow, S4)
    })
    r.addView(col(this).apply {
        layoutParams = grow()
        addView(row(this@badgeRow).apply {
            addView(title(this@badgeRow, badge.king, Brand.T_BODY,
                if (tier > 0) Brand.INK else Brand.INK_45).apply { layoutParams = grow() })
            addView(eyebrow(this@badgeRow, badge.tierName(this@badgeRow),
                if (tier > 0) Brand.INK_60 else Brand.INK_30))
        })
        addView(gap(this@badgeRow, 4f))
        addView(body(this@badgeRow, badge.title, Brand.T_CAPTION, Brand.INK_30))
        addView(gap(this@badgeRow, S2))
        // progress rail toward the next tier
        val f = badge.fraction(this@badgeRow)
        addView(row(this@badgeRow).apply {
            background = Brand.surface(this@badgeRow, 2f, Brand.HAIRLINE, 0x00000000)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(this@badgeRow, 4f))
            addView(View(this@badgeRow).apply {
                background = Brand.surface(this@badgeRow, 2f, Brand.INK, 0x00000000)
                layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, f.coerceAtLeast(0.01f))
            })
            addView(View(this@badgeRow), LinearLayout.LayoutParams(
                0, MATCH_PARENT, (1f - f).coerceAtLeast(0.01f)))
        })
        addView(gap(this@badgeRow, 5f))
        val next = badge.nextAt(this@badgeRow)
        addView(eyebrow(this@badgeRow,
            if (next == null) "complete"
            else "${badge.value(this@badgeRow)} / $next ${badge.unitFor(next)}"))
    })
    return r
}

/**
 * The collection, plus a share card.
 *
 * Sharing renders a card off-screen to a Bitmap rather than screenshotting the live UI, so what
 * goes out is composed for the purpose, no status bar, no tab bar, no half-scrolled content.
 */
class BadgesActivity : Activity() {

    /**
     * Detail and list are two states of one Activity, swapped with setContentView, so the system
     * back stack knows nothing about the transition. BACK from a detail view was closing the
     * whole collection and dumping you on Progress. Track the state and consume BACK ourselves.
     */
    private var showingDetail = false

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (showingDetail) { render() } else { @Suppress("DEPRECATION") super.onBackPressed() }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK
        Achievements.syncEarned(this)
        render()
    }

    private fun render() {
        showingDetail = false
        val p = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@BadgesActivity, S6), dp(this@BadgesActivity, 76f),
                dp(this@BadgesActivity, S6), dp(this@BadgesActivity, S8))
            minimumHeight = dp(this@BadgesActivity, Brand.MIN_PAGE)
        }

        val held = Achievements.tiersHeld(this)
        val total = Achievements.tiersTotal()

        p.addView(eyebrow(this, "Kings of Greece"))
        p.addView(gap(this, S3))
        p.addView(Brand.display(this, "The collection."))
        p.addView(gap(this, S4))
        p.addView(row(this).apply {
            addView(col(this@BadgesActivity).apply {
                layoutParams = grow()
                addView(Brand.hero(this@BadgesActivity, "$held", 40f))
                addView(gap(this@BadgesActivity, 5f))
                addView(eyebrow(this@BadgesActivity, "of $total tiers", Brand.INK_45))
            })
            addView(col(this@BadgesActivity).apply {
                layoutParams = grow()
                addView(Brand.hero(this@BadgesActivity,
                    "${Achievements.earned(this@BadgesActivity)}", 40f))
                addView(gap(this@BadgesActivity, 5f))
                addView(eyebrow(this@BadgesActivity, "of ${Achievements.ALL.size} kings", Brand.INK_45))
            })
        })
        p.addView(gap(this, S6))

        p.addView(Brand.title(this, "Share the collection", Brand.T_BODY, Brand.BLACK).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(this@BadgesActivity, Brand.R_TILE, Brand.INK, Brand.INK)
            setPadding(0, dp(this@BadgesActivity, S4), 0, dp(this@BadgesActivity, S4))
            layoutParams = Brand.fill()
            minHeight = dp(this@BadgesActivity, 52f)
            isClickable = true
            Anim.pressable(this)
            setOnClickListener { share() }
        })
        p.addView(gap(this, S8))

        val rows = mutableListOf<View>()
        Achievements.ALL.forEachIndexed { i, badgeItem ->
            val c = card(this)
            c.addView(badgeRow(badgeItem) { detail(badgeItem) })
            rows.add(c)
            p.addView(c, Brand.fill().apply { bottomMargin = dp(this@BadgesActivity, S2) })
        }

        // The collection is reached from Progress, so the bar shows Progress as the active tab.
        setContentView(Nav.scaffold(this, ScrollView(this).apply {
            setBackgroundColor(Brand.BLACK); addView(p)
        }, active = 3))
        Anim.stagger(rows.take(10), 34L, 100L)
    }

    /** Tapping a medal opens its myth and how it is earned. */
    private fun detail(badge: Badge) {
        showingDetail = true
        val p = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@BadgesActivity, S6), dp(this@BadgesActivity, 88f),
                dp(this@BadgesActivity, S6), dp(this@BadgesActivity, S8))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        p.addView(badgeMedal(badge, 170f))
        p.addView(gap(this, S6))
        p.addView(eyebrow(this, badge.tierName(this), Brand.INK_60)
            .apply { gravity = Gravity.CENTER })
        p.addView(gap(this, S2))
        p.addView(Brand.display(this, badge.king, 34f).apply { gravity = Gravity.CENTER })
        p.addView(gap(this, S2))
        p.addView(title(this, badge.title, Brand.T_TITLE, Brand.INK_60)
            .apply { gravity = Gravity.CENTER })
        p.addView(gap(this, S6))
        p.addView(body(this, badge.myth, Brand.T_BODY, Brand.INK_80)
            .apply { gravity = Gravity.CENTER })
        p.addView(gap(this, S6))
        p.addView(Brand.rule(this))
        p.addView(gap(this, S4))
        p.addView(eyebrow(this, "How it is earned"))
        p.addView(gap(this, S2))
        p.addView(body(this, badge.how, Brand.T_CAPTION, Brand.INK_60))
        p.addView(gap(this, S4))
        badge.tiers.forEachIndexed { i, t ->
            val got = badge.value(this) >= t
            p.addView(row(this).apply {
                setPadding(0, dp(this@BadgesActivity, S2), 0, dp(this@BadgesActivity, S2))
                addView(eyebrow(this@BadgesActivity, Badge.TIERS[i + 1],
                    if (got) Brand.INK else Brand.INK_30).apply { layoutParams = grow() })
                addView(Brand.mono(this@BadgesActivity, "$t ${badge.unitFor(t)}", 13f,
                    if (got) Brand.INK else Brand.INK_30))
            })
        }
        p.addView(gap(this, S8))
        p.addView(title(this, "Back", Brand.T_BODY, Brand.INK_60).apply {
            gravity = Gravity.CENTER
            background = Brand.surface(this@BadgesActivity, Brand.R_TILE, 0x00000000, Brand.HAIRLINE)
            setPadding(0, dp(this@BadgesActivity, S4), 0, dp(this@BadgesActivity, S4))
            layoutParams = Brand.fill()
            minHeight = dp(this@BadgesActivity, 48f)
            isClickable = true
            setOnClickListener { render() }
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(Brand.BLACK); addView(p) }.also { sv ->
            sv.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        })
        Anim.enter(p)
    }

    // ---- share ----------------------------------------------------------

    /**
     * Builds the card off-screen, measures it, draws it to a Bitmap, then publishes it through
     * MediaStore.
     *
     * MediaStore rather than FileProvider on purpose: it needs no AndroidX dependency and no
     * manifest provider, and it drops the card into the gallery, which is where you want it
     * anyway if you are about to post it.
     */
    private fun share() {
        val card = buildShareCard()
        val w = dp(this, 380f)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        card.layout(0, 0, w, card.measuredHeight)

        val bmp = Bitmap.createBitmap(w, card.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        card.draw(canvas)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "sheldy-collection.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sheldy")
        }
        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return

        runCatching {
            contentResolver.openOutputStream(uri)?.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT,
                            "${Store.streak(this@BadgesActivity)} days clean. " +
                                "${Achievements.tiersHeld(this@BadgesActivity)} of " +
                                "${Achievements.tiersTotal()} tiers earned.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share your collection"
                )
            )
        }.onFailure {
            runCatching { contentResolver.delete(uri, null, null) }
        }
    }

    private fun buildShareCard(): View {
        val c = col(this).apply {
            setBackgroundColor(Brand.BLACK)
            setPadding(dp(this@BadgesActivity, S8), dp(this@BadgesActivity, S8),
                dp(this@BadgesActivity, S8), dp(this@BadgesActivity, S8))
        }
        c.addView(eyebrow(this, "Sheldy"))
        c.addView(gap(this, S3))
        c.addView(Brand.hero(this, "${Store.streak(this)}", 76f))
        c.addView(gap(this, S1))
        c.addView(eyebrow(this, "days clean", Brand.INK_45))
        c.addView(gap(this, S6))

        // Earned medals only. Nobody shares a wall of locked coins.
        val earned = Achievements.ALL.filter { it.tier(this) > 0 }.take(12)
        if (earned.isNotEmpty()) {
            earned.chunked(4).forEach { rowOf ->
                c.addView(row(this).apply {
                    layoutParams = Brand.fill()
                        .apply { bottomMargin = dp(this@BadgesActivity, S2) }
                    rowOf.forEach { bd ->
                        addView(badgeMedal(bd, 64f).apply {
                            (layoutParams as LinearLayout.LayoutParams).rightMargin =
                                dp(this@BadgesActivity, S2)
                        })
                    }
                })
            }
            c.addView(gap(this, S4))
        }
        c.addView(Brand.rule(this))
        c.addView(gap(this, S4))
        c.addView(title(this,
            "${Achievements.tiersHeld(this)} of ${Achievements.tiersTotal()} tiers · " +
                "${Achievements.earned(this)} of ${Achievements.ALL.size} kings", Brand.T_BODY, Brand.INK_60))
        return c
    }
}
