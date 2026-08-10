package com.shashi.shield

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shashi.shield.Brand.S2
import com.shashi.shield.Brand.S3
import com.shashi.shield.Brand.S4
import com.shashi.shield.Brand.S6
import com.shashi.shield.Brand.body
import com.shashi.shield.Brand.col
import com.shashi.shield.Brand.dp
import com.shashi.shield.Brand.eyebrow
import com.shashi.shield.Brand.gap
import com.shashi.shield.Brand.row

/**
 * The panic conversation. Reachable from the block screen while the countdown runs, because the
 * whole point is having something to do with the ninety seconds other than argue with yourself.
 *
 * Same monochrome language: your turns are filled boxes, the companion's are hollow. No bubbles
 * with tails, no avatars, no typing dots pretending to be a person.
 */
class ChatActivity : Activity() {

    private val history = mutableListOf<Turn>()
    private lateinit var thread: LinearLayout
    private lateinit var scroll: ScrollView
    private var busy = false

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.statusBarColor = Brand.BLACK
        window.navigationBarColor = Brand.BLACK

        val root = col(this).apply { setBackgroundColor(Brand.BLACK) }

        // header
        root.addView(col(this).apply {
            setPadding(dp(this@ChatActivity, S6), dp(this@ChatActivity, S6),
                dp(this@ChatActivity, S6), dp(this@ChatActivity, S4))
            addView(row(this@ChatActivity).apply {
                addView(eyebrow(this@ChatActivity, "Talking it through")
                    .apply { layoutParams = Brand.grow() })
                addView(TextView(this@ChatActivity).apply {
                    text = "CLOSE"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(Brand.INK_45)
                    letterSpacing = 0.16f
                    typeface = Brand.medium(this@ChatActivity)
                    setPadding(dp(this@ChatActivity, S3), dp(this@ChatActivity, S2),
                        0, dp(this@ChatActivity, S2))
                    isClickable = true
                    setOnClickListener { finish() }
                })
            })
        })
        root.addView(Brand.rule(this))

        thread = col(this).apply {
            setPadding(dp(this@ChatActivity, S6), dp(this@ChatActivity, S4),
                dp(this@ChatActivity, S6), dp(this@ChatActivity, S4))
        }
        scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(thread)
        }
        root.addView(scroll)

        // composer
        val input = EditText(this).apply {
            setHint("Say what's going on")
            setHintTextColor(Brand.INK_30)
            setTextColor(Brand.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Brand.T_BODY)
            background = Brand.surface(this@ChatActivity, Brand.R_TILE, Brand.SURFACE)
            setPadding(dp(this@ChatActivity, S4), dp(this@ChatActivity, S3),
                dp(this@ChatActivity, S4), dp(this@ChatActivity, S3))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minHeight = dp(this@ChatActivity, 52f)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val send = TextView(this).apply {
            text = "SEND"
            gravity = Gravity.CENTER
            setTextColor(Brand.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.12f
            typeface = Brand.black(this@ChatActivity)
            background = Brand.surface(this@ChatActivity, Brand.R_TILE, Brand.INK, Brand.INK)
            layoutParams = LinearLayout.LayoutParams(dp(this@ChatActivity, 76f),
                dp(this@ChatActivity, 52f)).apply { marginStart = dp(this@ChatActivity, S2) }
            isClickable = true
            Anim.pressable(this)
        }
        send.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty() && !busy) { input.setText(""); ask(t) }
        }

        root.addView(Brand.rule(this))
        root.addView(row(this).apply {
            setPadding(dp(this@ChatActivity, S6), dp(this@ChatActivity, S3),
                dp(this@ChatActivity, S6), dp(this@ChatActivity, S3))
            addView(input); addView(send)
        })

        root.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        setContentView(root)

        addBubble(Coach.opener(this), mine = false)
        history.add(Turn("model", Coach.opener(this)))
    }

    private fun ask(text: String) {
        busy = true
        addBubble(text, mine = true)
        history.add(Turn("user", text))
        val thinking = addBubble("…", mine = false)

        Thread {
            val reply = Coach.reply(this, history)
            runOnUiThread {
                thread.removeView(thinking)
                addBubble(reply, mine = false)
                history.add(Turn("model", reply))
                busy = false
            }
        }.start()
    }

    /** Yours filled, the companion's hollow. Same fill-versus-outline language as everywhere. */
    private fun addBubble(text: String, mine: Boolean): View {
        val bubble = body(this, text, Brand.T_BODY, if (mine) Brand.BLACK else Brand.INK_80).apply {
            background = Brand.surface(this@ChatActivity, Brand.R_TILE,
                if (mine) Brand.INK else 0x00000000,
                if (mine) Brand.INK else Brand.HAIRLINE)
            setPadding(dp(this@ChatActivity, S4), dp(this@ChatActivity, S3),
                dp(this@ChatActivity, S4), dp(this@ChatActivity, S3))
            setLineSpacing(dp(this@ChatActivity, 5f).toFloat(), 1f)
            // Brand type turns font padding off, which is right for headlines sitting on a
            // baseline grid and wrong here: it crops the descenders on the last line of a long
            // reply, which is what "the message isn't fully visible" was. Put it back for prose.
            includeFontPadding = true
            // A wrapping TextView inside a horizontal LinearLayout is measured at WRAP_CONTENT,
            // so one long unbroken token (a URL, a run-on line) makes it wider than the row and
            // the overflow is simply clipped. Give it the real ceiling to wrap against.
            maxWidth = resources.displayMetrics.widthPixels -
                dp(this@ChatActivity, S6) * 2 - dp(this@ChatActivity, 48f)
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (mine) Gravity.END else Gravity.START
            layoutParams = Brand.fill().apply { bottomMargin = dp(this@ChatActivity, S3) }
            setPadding(
                if (mine) dp(this@ChatActivity, 48f) else 0, 0,
                if (mine) 0 else dp(this@ChatActivity, 48f), 0
            )
            addView(bubble)
        }
        thread.addView(holder)
        Anim.enter(holder, 0, 10f)
        // post() can run before the new bubble has been measured, so the scroll lands on the old
        // content height and a long reply opens showing only its first line or two. Wait for the
        // layout pass that actually includes it.
        holder.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                                        ol: Int, ot: Int, or_: Int, ob: Int) {
                v.removeOnLayoutChangeListener(this)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        })
        return holder
    }
}
