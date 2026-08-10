package com.shashi.shield

import android.content.Context

/**
 * A badge with four to six tiers, themed on the kings of ancient Greece.
 *
 * The theme is not decoration. Each king is chosen because his story is the same shape as the
 * thing being rewarded. Odysseus lashed to the mast so he could hear the Sirens and not act is
 * a better description of riding out an urge than any wellness slogan.
 *
 * The same achievement earned harder, rather than more badges. Tier 0 is locked; the top
 * tier of a six-tier badge is OLYMPIAN. 19 badges, 91 tiers in total.
 */
data class Badge(
    val id: String,
    val king: String,
    val title: String,
    val myth: String,
    val how: String,
    val unit: String,
    val art: Int,
    val tiers: List<Int>,
    val progress: (Context) -> Int
) {
    fun value(ctx: Context) = runCatching { progress(ctx) }.getOrDefault(0)

    fun tier(ctx: Context): Int = tiers.count { value(ctx) >= it }

    /** Progress toward the next tier, 0..1. Full once the top tier is held. */
    fun fraction(ctx: Context): Float {
        val v = value(ctx)
        val t = tier(ctx)
        if (t >= tiers.size) return 1f
        val floor = if (t == 0) 0 else tiers[t - 1]
        val ceil = tiers[t]
        if (ceil <= floor) return 1f
        return ((v - floor).toFloat() / (ceil - floor)).coerceIn(0f, 1f)
    }

    fun nextAt(ctx: Context): Int? = tiers.getOrNull(tier(ctx))

    fun tierName(ctx: Context) = TIERS[tier(ctx)]

    /** "1 day", not "1 days". Every unit here is a plain plural, so dropping the s is enough. */
    fun unitFor(n: Int): String =
        if (n == 1 && unit.endsWith("s")) unit.dropLast(1) else unit

    companion object {
        /**
         * Seven names for tiers 0..6. Badges carry four to six tiers, so the longest ones need
         * the full run, a five-name list would have indexed out of bounds the moment anyone
         * reached the sixth tier of LEONIDAS, which is exactly the kind of bug that only appears
         * for your most committed user.
         */
        val TIERS = listOf(
            "LOCKED", "BRONZE", "IRON", "SILVER", "GOLD", "DIAMOND", "OLYMPIAN"
        )
    }
}

object Achievements {

    private fun b(
        id: String, king: String, title: String, myth: String, how: String, unit: String,
        art: Int, tiers: List<Int>, progress: (Context) -> Int
    ) = Badge(id, king, title, myth, how, unit, art, tiers, progress)


    val ALL = listOf(
        // Signing the commitment earns the first tier on its own. Tying tier 1 to the streak
        // meant the "I started" badge stayed locked at the exact moment someone commits, which
        // is the one moment the collection should acknowledge immediately.
        b("theseus", "THESEUS", "Into the labyrinth",
            "Walked into the maze on purpose, holding one thread.",
            "Sign the commitment, then survive the first days", "days",
            R.drawable.badge_theseus, listOf(1, 3, 7, 14)) {
            if (Store.pledgeSigned(it)) maxOf(1, Store.streak(it)) else 0
        },

        b("leonidas", "LEONIDAS", "Held the pass",
            "Three hundred at the narrow place, refusing to move.",
            "Confirmed clean days in a row", "days",
            R.drawable.badge_leonidas, listOf(7, 30, 90, 180, 365, 1000)) { Store.streak(it) },

        b("alexander", "ALEXANDER", "The long campaign",
            "Marched further than anyone believed a man could.",
            "Clean days accumulated, all told", "days",
            R.drawable.badge_alexander,
            listOf(50, 150, 365, 750, 1500, 3000)) { Store.cleanDaysTotal(it) },

        b("odysseus", "ODYSSEUS", "Bound to the mast",
            "Heard the Sirens in full and did not steer toward them.",
            "Urges you sat through instead of acting on", "urges",
            R.drawable.badge_odysseus, listOf(10, 50, 120, 300, 1000)) { Store.panics(it) },

        b("ajax", "AJAX", "The unmoved shield",
            "Stood behind the great shield and simply did not break.",
            "Days straight without needing the panic button", "days",
            R.drawable.badge_ajax, listOf(15, 60, 180, 365, 730)) { Store.daysSincePanic(it) },

        b("nestor", "NESTOR", "Returned to counsel",
            "Outlived three generations by turning up and speaking plainly.",
            "Days you came back and confirmed, either way", "check-ins",
            R.drawable.badge_nestor, listOf(7, 60, 250, 700, 1500)) { Store.checkInsTotal(it) },

        b("cadmus", "CADMUS", "Brought the letters",
            "Carried the alphabet into Greece so things could be written down.",
            "Consecutive days with a note", "days",
            R.drawable.badge_cadmus, listOf(7, 30, 100, 365, 730)) { Store.noteStreak(it) },

        b("minos", "MINOS", "Learned the law",
            "Went up the mountain to be taught, then came back down and applied it.",
            "Daily lessons actually read", "lessons",
            R.drawable.badge_minos, listOf(10, 50, 150, 365, 730)) { Store.lessonsRead(it) },

        b("priam", "PRIAM", "Walked to the enemy's tent",
            "Crossed the lines unarmed to ask for his son. The hardest thing in the poem.",
            "Relapses logged instead of hidden", "logs",
            R.drawable.badge_priam, listOf(1, 5, 20, 50)) { Store.relapseTotal(it) },

        b("hector", "HECTOR", "Kept the gate",
            "Defended the wall every single day, knowing how it ended.",
            "Blocked attempts the watchdog stopped", "blocks",
            R.drawable.badge_hector, listOf(10, 50, 200, 500, 1500)) { Store.blocks(it) },

        b("sisyphus", "SISYPHUS", "Pushed it back up",
            "The boulder rolls down. He goes back to the bottom and starts again.",
            "Relapses followed by a clean day. The number that predicts the outcome.", "comebacks",
            R.drawable.badge_sisyphus, listOf(1, 5, 15, 40)) { Store.comebacks(it) },

        b("atlas", "ATLAS", "Carried the sky",
            "Held the whole weight without setting it down.",
            "Calendar months clean with no relapse logged", "months",
            R.drawable.badge_atlas, listOf(1, 3, 6, 12)) { Store.cleanMonths(it) },

        b("achilles", "ACHILLES", "The perfect week",
            "Unmatched when he chose to be on the field at all.",
            "Full weeks with all seven days confirmed clean", "weeks",
            R.drawable.badge_achilles, listOf(1, 4, 12, 26, 52)) { Store.perfectWeeks(it) },

        b("orpheus", "ORPHEUS", "Sang in the dark",
            "Went down into the underworld and made something out of it.",
            "Notes written between midnight and 5am", "notes",
            R.drawable.badge_orpheus, listOf(5, 20, 60, 150)) { Store.nightNotes(it) },

        b("perseus", "PERSEUS", "Looked at it sideways",
            "Beat the Gorgon by refusing to meet it head on, using a mirror.",
            "Your longest streak ever recorded", "days",
            R.drawable.badge_perseus, listOf(7, 30, 90, 365, 1000)) { Store.bestStreak(it) },

        b("agamemnon", "AGAMEMNON", "Kept the fleet together",
            "Held a coalition of kings in the field for ten years.",
            "Days since you started keeping any record at all", "days",
            R.drawable.badge_agamemnon,
            listOf(30, 90, 365, 1000, 2000)) { Store.daysTracked(it) },

        b("diomedes", "DIOMEDES", "Saw clearly at night",
            "Athena lifted the mist so he could tell gods from men.",
            "Notes written in total", "notes",
            R.drawable.badge_diomedes, listOf(10, 50, 200, 600, 1500)) { Store.notesTotal(it) },

        b("peleus", "PELEUS", "Knew the weapon",
            "Owned the ash spear only he could handle, because he understood it.",
            "Different triggers you have named for yourself", "triggers",
            R.drawable.badge_peleus, listOf(1, 3, 5, 8)) { Store.distinctTriggers(it) },

        // Lycurgus is the only lawgiver in a collection of warriors, and he belongs here more
        // than any of them. He made the Spartans swear to keep his laws until he came back from
        // Delphi, then never came back, so the oath could never be released. That is exactly
        // what the sleep lock does: it is not a bedtime, it is a rule you have removed your own
        // ability to repeal. Counted per night held, and a night only counts once it is over.
        b("lycurgus", "LYCURGUS", "Left so the law would hold",
            "Bound Sparta to an oath, then walked away so nobody could ever release them from it.",
            "Nights the sleep lock held", "nights",
            R.drawable.badge_lycurgus, listOf(1, 7, 21, 60, 120)) { Lock.nightsHeld(it) }
    )

    fun earned(ctx: Context) = ALL.count { it.tier(ctx) > 0 }
    fun diamondCount(ctx: Context) = ALL.count { it.tier(ctx) >= it.tiers.size }

    /** Tiers held out of every tier available, the honest completion number. */
    fun tiersHeld(ctx: Context) = ALL.sumOf { it.tier(ctx) }
    fun tiersTotal() = ALL.sumOf { it.tiers.size }

    /** Whichever badge is closest to its next tier. Shown as "next up". */
    fun nextUp(ctx: Context): Badge? =
        ALL.filter { it.tier(ctx) < it.tiers.size }
            .maxByOrNull { it.fraction(ctx) }

    // ---- calendar sync ---------------------------------------------------

    private const val K_EARNED = "badges_earned"

    /**
     * Records the day a tier was first reached, so the calendar can mark it. Called on every
     * app open; awarding is idempotent because the key includes the tier.
     */
    fun syncEarned(ctx: Context): List<String> {
        val sp = ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)
        val seen = sp.getStringSet(K_EARNED, emptySet())!!.toMutableSet()
        val fresh = mutableListOf<String>()
        ALL.forEach { b ->
            val t = b.tier(ctx)
            for (i in 1..t) {
                val key = "${b.id}:$i"
                if (seen.add(key)) {
                    fresh += "${b.king} · ${Badge.TIERS[i]}"
                    // stamp it on today's date so the calendar can show it
                    sp.edit().putString("badge_day_$key", Store.todayKey()).apply()
                }
            }
        }
        if (fresh.isNotEmpty()) sp.edit().putStringSet(K_EARNED, seen).apply()
        return fresh
    }

    /** Badge tiers earned on a given day, for the calendar cell. */
    fun earnedOn(ctx: Context, dayKey: String): List<String> {
        val sp = ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)
        val seen = sp.getStringSet(K_EARNED, emptySet())!!
        return seen.filter { sp.getString("badge_day_$it", null) == dayKey }
            .mapNotNull { k ->
                val id = k.substringBefore(":")
                val tier = k.substringAfter(":").toIntOrNull() ?: return@mapNotNull null
                ALL.firstOrNull { it.id == id }?.let { "${it.king} ${Badge.TIERS[tier]}" }
            }
    }

    fun daysWithBadges(ctx: Context): Set<String> {
        val sp = ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)
        val seen = sp.getStringSet(K_EARNED, emptySet())!!
        return seen.mapNotNull { sp.getString("badge_day_$it", null) }.toSet()
    }
}
