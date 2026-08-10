package com.shashi.shield

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Note(val at: Long, val text: String, val kind: String)

/** One square on the calendar. */
data class DayCell(
    val key: String, val dayOfMonth: Int, val state: String, val isToday: Boolean,
    val label: String = ""      // weekday initial, used by the bar chart axis
)

object Day {
    const val CLEAN = "clean"
    const val RELAPSE = "relapse"
    const val PENDING = "pending"   // today, not yet resolved
    const val MISSED = "missed"     // a past day nobody confirmed
    const val FUTURE = "future"
}

/**
 * All local state. SharedPreferences, not a database: a few hundred short strings on one device.
 *
 * The counting model matters more than the storage. **You start on day 0.** A day is only earned
 * by finishing it and confirming it, checking in tomorrow evening is what turns today into a
 * completed clean day. This is deliberate: a counter that hands you "day 1" for installing an app
 * is measuring the install, not the effort.
 */
object Store {

    private const val FILE = "sheldy"
    private const val K_DAYS = "day_states"      // { "2026-08-08": "clean" }
    private const val K_NOTES = "notes"
    private const val K_PANIC = "panic_events"
    private const val K_BLOCKS = "block_events"
    private const val K_FIRST = "first_run"
    private const val K_PLEDGE = "pledge_signed"
    private const val K_REMIND = "reminders_on"
    private const val K_THOUGHTS = "thoughts_per_day"
    private const val K_LESSONS = "lessons_read"
    private const val K_LAST_PANIC = "last_panic_at"
    private const val K_STARTED = "started_badge"

    private val KEY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun keyOf(cal: Calendar): String = KEY_FMT.format(cal.time)
    fun todayKey(): String = KEY_FMT.format(Date())

    fun yesterdayKey(): String = KEY_FMT.format(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
    )

    fun firstRun(ctx: Context): String {
        val s = sp(ctx)
        if (!s.contains(K_FIRST)) s.edit().putString(K_FIRST, todayKey()).apply()
        return s.getString(K_FIRST, todayKey())!!
    }

    // ---- day records -------------------------------------------------------

    private fun days(ctx: Context): JSONObject =
        runCatching { JSONObject(sp(ctx).getString(K_DAYS, "{}")!!) }.getOrDefault(JSONObject())

    fun rawState(ctx: Context, key: String): String? =
        days(ctx).optString(key, "").ifBlank { null }

    /** Unset a day entirely, back to unrecorded. */
    fun clearDay(ctx: Context, key: String) {
        val d = days(ctx)
        d.remove(key)
        sp(ctx).edit().putString(K_DAYS, d.toString()).apply()
        runCatching { ThoughtWidget.refresh(ctx) }
    }

    fun markDay(ctx: Context, key: String, state: String) {
        val d = days(ctx)
        d.put(key, state)
        sp(ctx).edit().putString(K_DAYS, d.toString()).apply()
        // Keep the home screen widget in step; the hourly system refresh is too slow to trust.
        runCatching { ThoughtWidget.refresh(ctx) }
    }

    /**
     * Confirm a completed day as clean. Refuses any day that is not finished yet: you cannot
     * mark today clean at 10am and call it earned. Today only becomes markable after the 21:00
     * check-in window opens.
     */
    fun confirmClean(ctx: Context, key: String): Boolean {
        if (!canConfirm(key)) return false
        markDay(ctx, key, Day.CLEAN)
        return true
    }

    /** Past days are always confirmable. Today only after 21:00. The future never. */
    fun canConfirm(key: String): Boolean {
        val today = todayKey()
        if (key > today) return false
        if (key < today) return true
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 21
    }

    fun logRelapse(ctx: Context, key: String, trigger: String, note: String) {
        markDay(ctx, key, Day.RELAPSE)
        addNote(ctx, buildString {
            append("Relapse")
            if (trigger.isNotBlank()) append(" · ").append(trigger)
            if (note.isNotBlank()) append("\n").append(note)
        }, "relapse")
    }

    /**
     * Consecutive confirmed clean days. Today never counts until it is confirmed, so a fresh
     * install reads 0 and stays 0 until you finish a day and say so.
     */
    fun streak(ctx: Context): Int {
        val d = days(ctx)
        val cal = Calendar.getInstance()
        // Today only counts if explicitly confirmed; otherwise start counting at yesterday.
        if (d.optString(keyOf(cal)) != Day.CLEAN) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (d.optString(keyOf(cal)) == Day.CLEAN) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    /** True when yesterday finished without being confirmed or logged, the nightly prompt. */
    fun needsConfirmation(ctx: Context): Boolean {
        val y = yesterdayKey()
        return firstRun(ctx) < y && rawState(ctx, y) == null
    }

    fun todayResolved(ctx: Context) = rawState(ctx, todayKey()) != null

    /**
     * Finished days since you started that were never confirmed either way, newest first.
     *
     * A day you forgot to close should not silently become a hole in the record, it breaks the
     * streak and there is nowhere to say what actually happened. These surface at the top of
     * Today so the gap can be filled in.
     */
    fun missedDays(ctx: Context, limit: Int = 14): List<String> {
        val d = days(ctx)
        val first = firstRun(ctx)
        val out = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)                 // yesterday backwards; today is separate
        repeat(60) {
            val key = keyOf(cal)
            if (key < first) return out
            if (d.optString(key, "").isBlank()) out.add(key)
            if (out.size >= limit) return out
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return out
    }

    fun prettyDay(key: String): String = runCatching {
        SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(KEY_FMT.parse(key)!!)
    }.getOrDefault(key)

    /**
     * Export everything as JSON.
     *
     * Google's backup transport refuses to sync organisation-owned devices, and provisioning a
     * Device Owner sets that flag permanently, so this app's history can never ride along in a
     * cloud backup while the protection is active. That is Google's policy, not something the
     * app can lift, so the answer is to make the data portable by hand instead.
     */
    fun exportJson(ctx: Context): String {
        val o = JSONObject()
        o.put("exported_at", System.currentTimeMillis())
        o.put("first_run", firstRun(ctx))
        o.put("day_states", days(ctx))
        o.put("notes", rawNotes(ctx))
        o.put("panic_events", panics(ctx))
        o.put("block_events", blocks(ctx))
        o.put("lessons_read", lessonsRead(ctx))
        o.put("pledge_signed", pledgeSigned(ctx))
        o.put("streak", streak(ctx))
        o.put("best_streak", bestStreak(ctx))
        return o.toString(2)
    }

    /** Merge an exported file back in. Day records win over whatever is here. */
    fun importJson(ctx: Context, json: String): String = runCatching {
        val o = JSONObject(json)
        val e = sp(ctx).edit()
        o.optString("first_run").takeIf { it.isNotBlank() }?.let { e.putString(K_FIRST, it) }
        o.optJSONObject("day_states")?.let { e.putString(K_DAYS, it.toString()) }
        o.optJSONArray("notes")?.let { e.putString(K_NOTES, it.toString()) }
        if (o.has("panic_events")) e.putInt(K_PANIC, o.optInt("panic_events"))
        if (o.has("block_events")) e.putInt(K_BLOCKS, o.optInt("block_events"))
        if (o.has("lessons_read")) e.putInt(K_LESSONS, o.optInt("lessons_read"))
        if (o.has("pledge_signed")) e.putBoolean(K_PLEDGE, o.optBoolean("pledge_signed"))
        e.apply()
        "Restored. Streak is now ${streak(ctx)} days."
    }.getOrElse { "Could not read that file: ${it.message}" }

    // ---- calendar ----------------------------------------------------------

    fun monthGrid(ctx: Context, year: Int, month: Int): List<DayCell> {
        val d = days(ctx)
        val today = todayKey()
        val first = firstRun(ctx)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, 1)
        }
        val count = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<DayCell>()

        // Leading blanks so the 1st lands under the right weekday column.
        val lead = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Monday-first
        repeat(lead) { cells.add(DayCell("", 0, Day.FUTURE, false)) }

        for (i in 1..count) {
            cal.set(Calendar.DAY_OF_MONTH, i)
            val key = keyOf(cal)
            val stored = d.optString(key, "")
            val state = when {
                stored.isNotBlank() -> stored
                key > today -> Day.FUTURE
                key == today -> Day.PENDING
                key < first -> Day.FUTURE      // before you started; render as inactive
                else -> Day.MISSED
            }
            cells.add(DayCell(key, i, state, key == today))
        }
        return cells
    }

    /** The last [n] days, oldest first, for the bar chart. */
    fun recentDays(ctx: Context, n: Int): List<DayCell> {
        val d = days(ctx)
        val today = todayKey()
        val first = firstRun(ctx)
        val initials = listOf("S", "M", "T", "W", "T", "F", "S")
        return (n - 1 downTo 0).map { back ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
            val key = keyOf(cal)
            val stored = d.optString(key, "")
            val state = when {
                stored.isNotBlank() -> stored
                key == today -> Day.PENDING
                key < first -> Day.FUTURE
                else -> Day.MISSED
            }
            DayCell(
                key, cal.get(Calendar.DAY_OF_MONTH), state, key == today,
                initials[cal.get(Calendar.DAY_OF_WEEK) - 1]
            )
        }
    }

    /**
     * A contribution-graph grid: whole weeks, Monday-first, ending with the week containing
     * today. Returned oldest-first so column 0 is the leftmost week and index % 7 is the row.
     */
    fun heatDays(ctx: Context, weeks: Int): List<DayCell> {
        val d = days(ctx)
        val today = todayKey()
        val first = firstRun(ctx)

        // Walk back to the Monday that starts the leftmost week.
        val cur = Calendar.getInstance()
        val dow = (cur.get(Calendar.DAY_OF_WEEK) + 5) % 7          // Mon = 0
        cur.add(Calendar.DAY_OF_YEAR, -dow - (weeks - 1) * 7)

        val initials = listOf("S", "M", "T", "W", "T", "F", "S")
        return (0 until weeks * 7).map {
            val key = keyOf(cur)
            val stored = d.optString(key, "")
            val state = when {
                stored.isNotBlank() -> stored
                key > today -> Day.FUTURE
                key == today -> Day.PENDING
                key < first -> Day.FUTURE
                else -> Day.MISSED
            }
            val cell = DayCell(
                key, cur.get(Calendar.DAY_OF_MONTH), state, key == today,
                initials[cur.get(Calendar.DAY_OF_WEEK) - 1]
            )
            cur.add(Calendar.DAY_OF_YEAR, 1)
            cell
        }
    }

    /** Full Mon-Sun weeks where all seven days are confirmed clean. */
    fun perfectWeeks(ctx: Context): Int {
        val d = days(ctx)
        val clean = d.keys().asSequence().filter { d.optString(it) == Day.CLEAN }.toSet()
        if (clean.isEmpty()) return 0
        val cal = Calendar.getInstance()
        // walk back to the most recent Monday, then step a week at a time
        cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        var weeks = 0
        repeat(260) {                                  // five years of lookback is plenty
            val start = cal.clone() as Calendar
            var all = true
            repeat(7) {
                if (!clean.contains(keyOf(start))) all = false
                start.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (all) weeks++
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        return weeks
    }

    /** Calendar months with at least 28 confirmed clean days and no logged relapse. */
    fun cleanMonths(ctx: Context): Int {
        val d = days(ctx)
        val byMonth = HashMap<String, IntArray>()      // [clean, relapse]
        d.keys().forEach { k ->
            val m = k.substring(0, 7)
            val slot = byMonth.getOrPut(m) { intArrayOf(0, 0) }
            when (d.optString(k)) {
                Day.CLEAN -> slot[0]++
                Day.RELAPSE -> slot[1]++
            }
        }
        return byMonth.values.count { it[0] >= 28 && it[1] == 0 }
    }

    /**
     * Comebacks: a relapse day followed by a confirmed clean day. This is the number that
     * actually predicts long-term outcome, not the streak, but whether you restart.
     */
    fun comebacks(ctx: Context): Int {
        val d = days(ctx)
        return d.keys().asSequence().count { k ->
            if (d.optString(k) != Day.RELAPSE) return@count false
            val next = Calendar.getInstance().apply {
                time = KEY_FMT.parse(k) ?: return@count false
                add(Calendar.DAY_OF_YEAR, 1)
            }
            d.optString(keyOf(next)) == Day.CLEAN
        }
    }

    fun notesTotal(ctx: Context) = notes(ctx).count { it.kind == "note" }

    /** Days since first run, how long you have been keeping any record at all. */
    fun daysTracked(ctx: Context): Int {
        val start = runCatching { KEY_FMT.parse(firstRun(ctx))!!.time }
            .getOrDefault(System.currentTimeMillis())
        return java.util.concurrent.TimeUnit.MILLISECONDS
            .toDays(System.currentTimeMillis() - start).toInt().coerceAtLeast(0) + 1
    }

    /** How many different triggers you have named. Self-knowledge, not volume. */
    fun distinctTriggers(ctx: Context): Int {
        val text = notes(ctx).filter { it.kind == "relapse" }.joinToString(" ") { it.text }
        return Triggers.ALL.count { text.contains(it, ignoreCase = true) }
    }

    /** Notes written between midnight and 5am, the hours that matter most. */
    fun nightNotes(ctx: Context) = notes(ctx).count {
        Calendar.getInstance().apply { timeInMillis = it.at }
            .get(Calendar.HOUR_OF_DAY) in 0..4
    }

    fun cleanDaysTotal(ctx: Context): Int {
        val d = days(ctx)
        return d.keys().asSequence().count { d.optString(it) == Day.CLEAN }
    }

    fun relapseTotal(ctx: Context): Int {
        val d = days(ctx)
        return d.keys().asSequence().count { d.optString(it) == Day.RELAPSE }
    }

    fun bestStreak(ctx: Context): Int {
        val d = days(ctx)
        val keys = d.keys().asSequence().filter { d.optString(it) == Day.CLEAN }.sorted().toList()
        if (keys.isEmpty()) return 0
        var best = 1; var run = 1
        for (i in 1 until keys.size) {
            val prev = Calendar.getInstance().apply {
                time = KEY_FMT.parse(keys[i])!!; add(Calendar.DAY_OF_YEAR, -1)
            }
            run = if (keyOf(prev) == keys[i - 1]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    // ---- notes -------------------------------------------------------------

    fun addNote(ctx: Context, text: String, kind: String = "note") {
        if (text.isBlank()) return
        val arr = rawNotes(ctx)
        arr.put(JSONObject().apply {
            put("at", System.currentTimeMillis())
            put("text", text.trim())
            put("kind", kind)
        })
        while (arr.length() > 500) arr.remove(0)
        sp(ctx).edit().putString(K_NOTES, arr.toString()).apply()
    }

    fun notes(ctx: Context): List<Note> {
        val arr = rawNotes(ctx)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Note(o.optLong("at"), o.optString("text"), o.optString("kind", "note"))
        }.sortedByDescending { it.at }
    }

    private fun rawNotes(ctx: Context): JSONArray =
        runCatching { JSONArray(sp(ctx).getString(K_NOTES, "[]")) }.getOrDefault(JSONArray())

    // ---- counters ----------------------------------------------------------

    fun logPanic(ctx: Context) = sp(ctx).edit()
        .putInt(K_PANIC, panics(ctx) + 1)
        .putLong(K_LAST_PANIC, System.currentTimeMillis())
        .apply()

    /** Days since the panic button was last needed. Counts from first run if never pressed. */
    fun daysSincePanic(ctx: Context): Int {
        val last = sp(ctx).getLong(K_LAST_PANIC, 0L)
        val from = if (last > 0L) last else
            runCatching { KEY_FMT.parse(firstRun(ctx))!!.time }.getOrDefault(System.currentTimeMillis())
        return java.util.concurrent.TimeUnit.MILLISECONDS
            .toDays(System.currentTimeMillis() - from).toInt().coerceAtLeast(0)
    }

    fun markLessonRead(ctx: Context) {
        val key = "lesson_" + todayKey()
        if (sp(ctx).getBoolean(key, false)) return          // one per day, no farming
        sp(ctx).edit().putBoolean(key, true)
            .putInt(K_LESSONS, lessonsRead(ctx) + 1).apply()
    }

    fun lessonsRead(ctx: Context) = sp(ctx).getInt(K_LESSONS, 0)

    fun lessonReadToday(ctx: Context) = sp(ctx).getBoolean("lesson_" + todayKey(), false)

    /** Consecutive days ending today (or yesterday) that have at least one note. */
    fun noteStreak(ctx: Context): Int {
        val dayKeys = notes(ctx).map { KEY_FMT.format(Date(it.at)) }.toSet()
        if (dayKeys.isEmpty()) return 0
        val cal = Calendar.getInstance()
        if (!dayKeys.contains(keyOf(cal))) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (dayKeys.contains(keyOf(cal))) { n++; cal.add(Calendar.DAY_OF_YEAR, -1) }
        return n
    }

    /** Total days ever confirmed, clean or relapse, i.e. days you actually showed up. */
    fun checkInsTotal(ctx: Context): Int {
        val d = days(ctx)
        return d.keys().asSequence().count()
    }
    fun panics(ctx: Context) = sp(ctx).getInt(K_PANIC, 0)

    fun logBlock(ctx: Context) = sp(ctx).edit().putInt(K_BLOCKS, blocks(ctx) + 1).apply()
    fun blocks(ctx: Context) = sp(ctx).getInt(K_BLOCKS, 0)

    fun pledgeSigned(ctx: Context) = sp(ctx).getBoolean(K_PLEDGE, false)
    fun signPledge(ctx: Context) = sp(ctx).edit().putBoolean(K_PLEDGE, true).apply()

    // ---- settings ----------------------------------------------------------

    fun remindersOn(ctx: Context) = sp(ctx).getBoolean(K_REMIND, true)
    fun setReminders(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean(K_REMIND, on).apply()
        // Scheduling three alarms and touching NotificationManager on the main thread inside a
        // click handler is enough to drop frames right as the row is being redrawn.
        Thread { runCatching { Reminders.apply(ctx.applicationContext) } }.start()
    }

    /** How many thoughts to surface per day. 1 by default; more for the harder days. */
    fun thoughtsPerDay(ctx: Context) = sp(ctx).getInt(K_THOUGHTS, 1)
    fun setThoughtsPerDay(ctx: Context, n: Int) =
        sp(ctx).edit().putInt(K_THOUGHTS, n.coerceIn(1, 5)).apply()
}

/** Triggers offered when logging a relapse. Short, concrete, chosen from the research. */
object Triggers {
    val ALL = listOf(
        "Late night", "Bored", "Stressed", "Lonely", "Tired",
        "Social feed", "Argument", "Drinking", "Alone at home", "Woke up early"
    )
}

/**
 * One thought per day, chosen by day-of-year so it is stable all day and rolls at midnight with
 * no scheduling. Short, blunt, second person, it has to land at 1am when you are arguing with
 * yourself, not read like a poster in a waiting room.
 */
object Thoughts {

    private val LINES = listOf(
        "The urge is a wave. Waves break.",
        "You have already survived every craving you have ever had.",
        "Nothing bad happens if you feel this and do nothing.",
        "Ten minutes from now this will be quieter.",
        "You are not missing out. You are opting out.",
        "The version of you at 7am is counting on the version at 1am.",
        "Boredom is not an emergency.",
        "You do not have to win the argument. Just do not open the tab.",
        "It lies about how good it will be, every single time.",
        "Loneliness wants company, not a screen.",
        "Put the phone in another room. That is the whole plan.",
        "You are tired, not weak. Sleep instead.",
        "This craving is asking for relief, not for porn.",
        "The streak is not the point. The person building it is.",
        "You have done a hard day. Do not end it owing yourself an apology.",
        "Stress is the trigger. Name it and it loosens.",
        "Nobody relapses on a full night of sleep and a walk.",
        "The regret always outlasts the relief.",
        "You are allowed to just go to bed.",
        "Every time you do not, it gets a little easier.",
        "The trigger is the feeling, not the phone.",
        "You are rebuilding a reward system. That takes weeks, not days.",
        "Restlessness at night is normal. It is not a sign to act.",
        "You would not do this in front of someone you respect.",
        "Wanting it and choosing it are different things.",
        "Discomfort now, or shame later. Pick the cheaper one.",
        "The screen cannot fix what is actually wrong.",
        "Talk to someone tomorrow. Tonight, just wait it out.",
        "You are not starting over. You are continuing.",
        "One clean night is a real thing. Collect it."
    )

    fun today(): String =
        LINES[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % LINES.size]
}
