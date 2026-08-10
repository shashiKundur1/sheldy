package com.shashi.shield

import android.content.Context
import java.util.Calendar

data class Milestone(val days: Int, val name: String, val why: String) {
    fun reached(streak: Int) = streak >= days
}

/**
 * Milestones people actually feel, at the intervals the withdrawal literature keeps landing on.
 * No badges, no confetti, the theme forbids celebrating the counter (rule 7). These exist so you
 * know what is coming and that the bad stretch is normal, not a sign it isn't working.
 */
object Milestones {

    val ALL = listOf(
        Milestone(1, "One day", "The hardest ratio you will ever face: one day against a habit."),
        Milestone(3, "Three days", "Peak withdrawal. Irritable, restless, sleep is poor. This is the worst of it."),
        Milestone(7, "One week", "Sleep starts to settle. Cravings become episodic rather than constant."),
        Milestone(14, "Two weeks", "The flatline. Low mood and no motivation. Most people quit here believing it failed."),
        Milestone(30, "One month", "Attention span and drive begin returning. The first genuinely different month."),
        Milestone(90, "Ninety days", "The interval most recovery programmes use as a reset point.")
    )

    fun next(streak: Int): Milestone? = ALL.firstOrNull { streak < it.days }

    fun reachedCount(streak: Int) = ALL.count { it.reached(streak) }
}

data class Lesson(val title: String, val body: String)

/**
 * One lesson a day, indexed by day-of-year so it rolls at midnight with no scheduling, the same
 * way the thought does. Written to be read in under thirty seconds, anything longer does not get
 * read at all.
 */
object Lessons {

    private val ALL = listOf(
        Lesson("Why it escalates",
            "Novelty is the active ingredient, not the content. The brain habituates to what it has " +
            "already seen, so the same material stops working and you go looking for something " +
            "stronger. That escalation is a property of the mechanism, not of you."),
        Lesson("The chaser effect",
            "Relief lasts minutes, the dip lasts hours. Because the dip is uncomfortable, it reliably " +
            "produces the next urge. This is why one slip so often becomes three days of them."),
        Lesson("It is a state, not a trait",
            "You are not a person who cannot stop. You are a person running a loop that has been " +
            "reinforced several thousand times. Loops are unlearnable. Traits are not."),
        Lesson("Triggers are feelings",
            "Almost nobody relapses because they saw something. They relapse because they were bored, " +
            "lonely, stressed or tired and the phone was within reach. Fix the feeling and the reach " +
            "stops mattering."),
        Lesson("The flatline is real",
            "Around week two most people feel worse, not better: flat mood, no drive, no interest in " +
            "anything. It is dopamine receptors re-regulating. It passes, and it is the single most " +
            "common point people give up believing nothing changed."),
        Lesson("Sleep is the lever",
            "Tired brains have almost no inhibitory control. Nearly every relapse happens after " +
            "midnight. Going to bed on time is a bigger intervention than any blocker, including " +
            "this one."),
        Lesson("Shame is fuel",
            "Feeling disgusted with yourself reliably produces another urge, because the brain reaches " +
            "for the fastest available relief from a bad feeling. Logging a relapse flatly, without " +
            "self-attack, breaks that cycle."),
        Lesson("Never zero, always next",
            "A relapse does not delete the weeks before it. Those weeks physically changed your reward " +
            "system. The counter resets because counters are simple, not because your progress did."),
        Lesson("Boredom tolerance",
            "The skill you are actually building is sitting with an unpleasant feeling and doing " +
            "nothing about it. Every urge you wait out is a repetition of that skill."),
        Lesson("Environment beats willpower",
            "Willpower is a finite daily budget that runs out exactly when you need it. Distance is " +
            "not. Charging the phone in another room outperforms every decision you will make at 1am."),
        Lesson("The 90 second rule",
            "The neurochemical component of an urge peaks and clears in roughly ninety seconds. What " +
            "extends it past that is your own attention feeding it. That is the whole reason the " +
            "block screen counts."),
        Lesson("Tell someone",
            "Secrecy is the condition the habit needs. One person knowing, even without detail, " +
            "changes the maths more than any technical measure on this phone."),
        Lesson("Urges are not instructions",
            "Wanting something and choosing it are separate events with a gap between them. Recovery " +
            "happens entirely inside that gap. The urge arriving is not a failure."),
        Lesson("Track the pattern, not the guilt",
            "After a month of notes you will see your own shape: the hour, the app, the mood. That is " +
            "actionable. Guilt is not.")
    )

    fun today(): Lesson = ALL[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % ALL.size]

    fun all() = ALL
}

data class Pattern(val label: String, val count: Int, val share: Float)

/**
 * Your own relapse data, grouped. Deliberately refuses to show anything until there are at least
 * three data points, a "pattern" drawn from one relapse is noise presented as insight, and it
 * would be the first thing in the app you stop trusting.
 */
object Patterns {

    const val MIN_DATA = 3

    private fun relapses(ctx: Context) = Store.notes(ctx).filter { it.kind == "relapse" }

    fun enoughData(ctx: Context) = relapses(ctx).size >= MIN_DATA

    /** Which trigger you pick most. Parsed from the note text the relapse logger writes. */
    fun byTrigger(ctx: Context): List<Pattern> {
        val rows = relapses(ctx)
        if (rows.size < MIN_DATA) return emptyList()
        val counts = Triggers.ALL.associateWith { t ->
            rows.count { it.text.contains(t, ignoreCase = true) }
        }.filterValues { it > 0 }
        val total = counts.values.sum().coerceAtLeast(1)
        return counts.entries.sortedByDescending { it.value }
            .map { Pattern(it.key, it.value, it.value.toFloat() / total) }
    }

    /** Which part of the day it happens in. Four buckets, because 24 bars of noise helps nobody. */
    fun byTimeOfDay(ctx: Context): List<Pattern> {
        val rows = relapses(ctx)
        if (rows.size < MIN_DATA) return emptyList()
        val buckets = linkedMapOf(
            "Late night · 12am-5am" to 0,
            "Morning · 5am-12pm" to 0,
            "Afternoon · 12pm-6pm" to 0,
            "Evening · 6pm-12am" to 0
        )
        rows.forEach { n ->
            val h = Calendar.getInstance().apply { timeInMillis = n.at }.get(Calendar.HOUR_OF_DAY)
            val key = when (h) {
                in 0..4 -> "Late night · 12am-5am"
                in 5..11 -> "Morning · 5am-12pm"
                in 12..17 -> "Afternoon · 12pm-6pm"
                else -> "Evening · 6pm-12am"
            }
            buckets[key] = (buckets[key] ?: 0) + 1
        }
        val total = buckets.values.sum().coerceAtLeast(1)
        return buckets.filterValues { it > 0 }.entries
            .sortedByDescending { it.value }
            .map { Pattern(it.key, it.value, it.value.toFloat() / total) }
    }

    /** One plain sentence naming the strongest pattern, or null when there is not enough data. */
    fun headline(ctx: Context): String? {
        val time = byTimeOfDay(ctx).firstOrNull() ?: return null
        val trig = byTrigger(ctx).firstOrNull()
        val pct = (time.share * 100).toInt()
        return if (trig != null)
            "$pct% of your relapses happen ${time.label.substringBefore(" ·").lowercase()}, " +
                "most often when you logged \"${trig.label.lowercase()}\"."
        else
            "$pct% of your relapses happen ${time.label.substringBefore(" ·").lowercase()}."
    }
}
