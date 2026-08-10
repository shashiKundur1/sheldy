package com.shashi.shield

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class Turn(val role: String, val text: String)

/**
 * The companion you talk to while the countdown runs.
 *
 * It is given the actual state, streak, hour of night, recent triggers, because generic
 * encouragement is useless at 1am and specific observation is not. The system prompt is
 * deliberately narrow: short replies, no lecturing, no shame, and it must never moralise. The
 * failure mode of every recovery chatbot is sounding like a pamphlet.
 */
object Coach {

    /**
     * The key never lives in source. There are two supported places to put it, in this order:
     *
     *   1. Settings, Companion, paste a key. Stored on the device only.
     *   2. local.properties in the project root, as gemini.key=AIza...
     *      That file is gitignored, and Gradle copies the value into BuildConfig at build time.
     *
     * Leave both empty and the app still works. The companion chat is the only feature that
     * needs a key, and it says so plainly rather than failing quietly.
     *
     * A key starts "AIza" and does not expire. A value starting "AQ." is a short lived OAuth
     * token, not an API key, and it will stop working within the hour.
     */
    private const val MODEL = "gemini-2.5-flash"

    fun key(ctx: Context): String {
        val saved = ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)
            .getString("gemini_key", null)
        return if (!saved.isNullOrBlank()) saved else BuildConfig.GEMINI_KEY
    }

    /** True when the companion has a key to talk to. */
    fun configured(ctx: Context) = key(ctx).isNotBlank()

    fun setKey(ctx: Context, k: String) =
        ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)
            .edit().putString("gemini_key", k.trim()).apply()

    private fun systemPrompt(ctx: Context): String {
        val streak = Store.streak(ctx)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val recent = Store.notes(ctx).take(3).joinToString(" | ") { it.text.take(80) }
        return """
You are the companion inside Sheldy, an app someone uses to stop watching porn.
They have just hit the panic button. They are mid-urge, right now.

What you know: current streak $streak days. Local hour is $hour.
Their recent notes: ${recent.ifBlank { "none yet" }}.

How to reply:
- Two or three short sentences. Never more. They are not reading an essay right now.
- Talk like a steady friend who has been here, not a therapist and not a coach.
- Never shame, never moralise, never mention willpower or discipline.
- Never say "you've got this" or any slogan. Be specific instead.
- Ask one concrete question, or name one concrete thing to do in the next five minutes
  (stand up, water, go outside, phone in the other room, message someone).
- If they say they already relapsed: no disappointment at all. It is data. Ask what set it off.
- If they mention self-harm or crisis, tell them plainly to contact a local crisis line.
- British spelling. No emoji. No markdown formatting, no bullet points.
""".trim()
    }

    /** Blocking. Callers run this off the main thread. */
    fun reply(ctx: Context, history: List<Turn>): String {
        val k = key(ctx)
        if (k.isBlank()) return "No API key set. Add one in Settings."

        val contents = JSONArray()
        history.forEach { t ->
            contents.put(JSONObject().apply {
                put("role", if (t.role == "user") "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            })
        }

        val body = JSONObject().apply {
            put("contents", contents)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt(ctx))))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.9)
                put("maxOutputTokens", 200)
            })
        }

        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$k")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/json")
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                return when {
                    "API_KEY_INVALID" in err || conn.responseCode == 401 || conn.responseCode == 403 ->
                        "That key is no longer valid. If it starts with \"AQ.\" it was a temporary token and has expired, get one starting \"AIza\" from aistudio.google.com and paste it in Settings."
                    else -> "Could not reach the model (${conn.responseCode}). The countdown still works."
                }
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                ?.ifBlank { null }
                ?: "No reply came back. Sit with the countdown, it still works."
        } catch (e: Exception) {
            "No connection. That is fine, the ninety seconds is the part that matters."
        }
    }

    /** Opening line, so the screen is never an empty chat box. */
    fun opener(ctx: Context): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour in 0..4 -> "It's late. What's actually going on right now?"
            hour in 5..11 -> "Morning urge. What happened just before this?"
            hour in 12..17 -> "What set this off?"
            else -> "You hit the button. What's happening?"
        }
    }
}
