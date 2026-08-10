package com.shashi.shield

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * Locking the phone itself, not just the porn.
 *
 * Two locks, one mechanism:
 *   FOCUS, a block of hours you start by hand. Capped at 8 hours a day.
 *   SLEEP, a nightly window you commit to for a number of days and cannot take back.
 *
 * The mechanism is Device Owner package suspension, not an overlay. An overlay is a picture of a
 * lock: it can be swiped past, killed by the task switcher, or beaten by launching the app from a
 * notification. A suspended package will not start at all, and Android puts up its own dialog
 * saying so. It survives reboots, recents, deep links and app shortcuts, because the refusal is in
 * the package manager rather than in a window drawn on top.
 *
 * What is never suspended, ever:
 *   - the dialer and the default SMS app, so calls and texts always work
 *   - the launcher, so the phone is not a black rectangle
 *   - Sheldy, so the escape hatch is always reachable
 *   - anything the system marks as a system package, so nothing essential breaks
 * Plus up to three apps you choose.
 */
object Lock {

    private const val FILE = "sheldy"

    private const val K_FOCUS_UNTIL = "lock_focus_until"
    private const val K_ALLOW = "lock_allow"              // csv, max 3
    private const val K_SLEEP_ON = "lock_sleep_on"
    private const val K_SLEEP_FROM = "lock_sleep_from"
    private const val K_SLEEP_TO = "lock_sleep_to"
    private const val K_SLEEP_COMMIT = "lock_sleep_commit"       // epoch ms; cannot disable before
    private const val K_COMMIT_DAYS = "lock_commit_days"
    private const val K_GRANT_UNTIL = "lock_grant_until"
    private const val K_GRANT_SESSION = "lock_grant_session"     // which lock the grants belong to
    private const val K_GRANT_USED = "lock_grant_used"
    private const val K_LEDGER = "lock_ledger"
    private const val K_GREY_BEFORE = "lock_grey_before"   // their own greyscale setting
    private const val K_SUSPENDED = "lock_suspended"       // exactly what we suspended
    private const val K_GREY_ON_LOCK = "lock_grey_on"
    private const val K_OWN_SCREEN = "lock_own_screen"

    /**
     * Drain the colour for the duration of a lock. On by default, because it is the one layer
     * that cannot be argued past, but it is a preference, not a law, so it can be turned off.
     */
    fun greyOnLock(ctx: Context) = sp(ctx).getBoolean(K_GREY_ON_LOCK, true)

    /**
     * Can the colour setting be changed right now? No, while anything is running.
     *
     * Decide it before, not during. A switch you can flip mid-lock is not a rule, it is a button
     * you press when the rule starts costing something, and that is the exact moment it was
     * supposed to hold. This covers the emergency window too: five minutes to ring a hospital is
     * not five minutes of colour back.
     */
    fun colourSettingLocked(ctx: Context) = focusActive(ctx) || sleepActive(ctx) || granted(ctx)

    fun setGreyOnLock(ctx: Context, on: Boolean): Boolean {
        if (colourSettingLocked(ctx)) return false
        sp(ctx).edit().putBoolean(K_GREY_ON_LOCK, on).apply()
        apply(ctx)
        return true
    }

    /** Greyscale must hold through a grant, so the manual switch is refused mid-session too. */
    fun greyscaleHeld(ctx: Context) = colourSettingLocked(ctx) && greyOnLock(ctx)

    /** Sheldy's own lock screen instead of Android's grey "app paused" dialog. */
    fun ownScreen(ctx: Context) = sp(ctx).getBoolean(K_OWN_SCREEN, true)
    fun setOwnScreen(ctx: Context, on: Boolean) =
        sp(ctx).edit().putBoolean(K_OWN_SCREEN, on).apply()

    /** Three five-minute sessions. Not a fourth: at that point it is not an emergency. */
    const val EMERGENCY_MINUTES = 5
    const val EMERGENCY_COUNT = 3

    /** The hard ceiling on a reasoned unlock, whatever the reason says. */
    const val REASON_CAP_MINUTES = 120

    /**
     * Longest reason accepted. Long enough for a real explanation, short enough that the ledger
     * stays a list you can scan rather than a wall of essays.
     */
    const val REASON_MAX_CHARS = 220

    /** The most a focus lock can run in one go. */
    const val FOCUS_MAX_HOURS = 8

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- allowlist ---------------------------------------------------------

    fun allowed(ctx: Context): List<String> =
        sp(ctx).getString(K_ALLOW, "").orEmpty().split(",").filter { it.isNotBlank() }

    fun setAllowed(ctx: Context, packages: List<String>) {
        sp(ctx).edit().putString(K_ALLOW, packages.take(3).joinToString(",")).apply()
        apply(ctx)
    }

    // ---- focus lock --------------------------------------------------------

    fun focusUntil(ctx: Context) = sp(ctx).getLong(K_FOCUS_UNTIL, 0L)
    fun focusActive(ctx: Context) = System.currentTimeMillis() < focusUntil(ctx)

    fun startFocus(ctx: Context, minutes: Int) {
        val capped = minutes.coerceIn(5, FOCUS_MAX_HOURS * 60)
        sp(ctx).edit()
            .putLong(K_FOCUS_UNTIL, System.currentTimeMillis() + capped * 60_000L)
            .putString(K_GRANT_SESSION, "focus:${System.currentTimeMillis()}")
            .putInt(K_GRANT_USED, 0)
            .putLong(K_GRANT_UNTIL, 0L)
            .apply()
        apply(ctx)
    }

    // ---- sleep lock --------------------------------------------------------

    fun sleepOn(ctx: Context) = sp(ctx).getBoolean(K_SLEEP_ON, false)
    fun sleepFrom(ctx: Context) = sp(ctx).getInt(K_SLEEP_FROM, 23)
    fun sleepTo(ctx: Context) = sp(ctx).getInt(K_SLEEP_TO, 6)
    fun commitDays(ctx: Context) = sp(ctx).getInt(K_COMMIT_DAYS, 12)
    fun commitUntil(ctx: Context) = sp(ctx).getLong(K_SLEEP_COMMIT, 0L)

    /**
     * The commitment is the whole point of a sleep lock. Anyone can set a bedtime; the useful part
     * is not being able to unset it at midnight, when the version of you deciding is the one the
     * rule exists for.
     */
    fun canDisableSleep(ctx: Context) = System.currentTimeMillis() >= commitUntil(ctx)

    fun setSleepWindow(ctx: Context, from: Int, to: Int) {
        sp(ctx).edit()
            .putInt(K_SLEEP_FROM, from.coerceIn(0, 23))
            .putInt(K_SLEEP_TO, to.coerceIn(0, 23))
            .apply()
        apply(ctx)
    }

    fun setCommitDays(ctx: Context, days: Int) =
        sp(ctx).edit().putInt(K_COMMIT_DAYS, days.coerceIn(1, 90)).apply()

    /** Returns false and changes nothing if the commitment has not run out yet. */
    fun setSleepOn(ctx: Context, on: Boolean): Boolean {
        if (!on && !canDisableSleep(ctx)) return false
        val e = sp(ctx).edit().putBoolean(K_SLEEP_ON, on)
        if (on) e.putLong(K_SLEEP_COMMIT,
            System.currentTimeMillis() + commitDays(ctx) * 86_400_000L)
        else e.putLong(K_SLEEP_COMMIT, 0L)
        e.apply()
        apply(ctx)
        return true
    }

    fun sleepActive(ctx: Context): Boolean {
        if (!sleepOn(ctx)) return false
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val f = sleepFrom(ctx); val t = sleepTo(ctx)
        return if (f <= t) h in f until t else (h >= f || h < t)
    }

    // ---- grants ------------------------------------------------------------

    fun grantUntil(ctx: Context) = sp(ctx).getLong(K_GRANT_UNTIL, 0L)
    fun granted(ctx: Context) = System.currentTimeMillis() < grantUntil(ctx)

    /**
     * Emergency sessions reset per lock session, not per day. A focus block started this afternoon
     * and tonight's sleep window are separate emergencies; carrying a used-up counter across them
     * would mean one bad afternoon leaves you with no way to answer a real problem at 3am.
     */
    private fun sessionId(ctx: Context): String = when {
        focusActive(ctx) -> sp(ctx).getString(K_GRANT_SESSION, "focus").orEmpty()
        sleepActive(ctx) -> "sleep:" + Store.todayKey()
        else -> "none"
    }

    fun emergenciesUsed(ctx: Context): Int {
        val cur = sessionId(ctx)
        if (sp(ctx).getString(K_GRANT_SESSION, "") != cur) return 0
        return sp(ctx).getInt(K_GRANT_USED, 0)
    }

    fun emergenciesLeft(ctx: Context) = (EMERGENCY_COUNT - emergenciesUsed(ctx)).coerceAtLeast(0)

    /** Five minutes, logged with the reason. Returns false when all three are spent. */
    fun useEmergency(ctx: Context, reason: String): Boolean {
        if (emergenciesLeft(ctx) <= 0) return false
        val used = emergenciesUsed(ctx)
        sp(ctx).edit()
            .putString(K_GRANT_SESSION, sessionId(ctx))
            .putInt(K_GRANT_USED, used + 1)
            .putLong(K_GRANT_UNTIL, System.currentTimeMillis() + EMERGENCY_MINUTES * 60_000L)
            .apply()
        log(ctx, reason, EMERGENCY_MINUTES, "emergency")
        apply(ctx)
        return true
    }

    /** A reasoned unlock. Minutes are decided by [judge]; this only enforces the ceiling. */
    fun grantMinutes(ctx: Context, minutes: Int, reason: String) {
        val m = minutes.coerceIn(0, REASON_CAP_MINUTES)
        if (m == 0) { log(ctx, reason, 0, "refused"); return }
        breakNight(ctx)
        sp(ctx).edit()
            .putLong(K_GRANT_UNTIL, System.currentTimeMillis() + m * 60_000L)
            .apply()
        log(ctx, reason, m, "reason")
        apply(ctx)
    }

    // ---- the ledger --------------------------------------------------------

    /**
     * Every unlock is written down and shown back in Settings.
     *
     * This is the only real friction here. The lock can always be escaped, it must be, a phone
     * that cannot be unlocked in a genuine emergency is dangerous, so what stops casual use is
     * not the barrier, it is having to write down why and then read the list of every other time
     * you said the same thing.
     */
    fun log(ctx: Context, reason: String, minutes: Int, kind: String) {
        val arr = JSONArray(sp(ctx).getString(K_LEDGER, "[]"))
        arr.put(JSONObject().apply {
            put("at", System.currentTimeMillis())
            // Capped at the source, not just in the view. An unbounded string here grows the
            // prefs file forever and makes one entry tall enough to push the rest of the ledger
            // off the screen, which defeats the point of being able to read it back.
            put("reason", reason.trim().take(REASON_MAX_CHARS))
            put("minutes", minutes)
            put("kind", kind)
        })
        // Keep the last 60. A ledger you cannot read is not accountability.
        val trimmed = JSONArray()
        val from = (arr.length() - 60).coerceAtLeast(0)
        for (i in from until arr.length()) trimmed.put(arr.get(i))
        sp(ctx).edit().putString(K_LEDGER, trimmed.toString()).apply()
    }

    data class Entry(val at: Long, val reason: String, val minutes: Int, val kind: String)

    fun ledger(ctx: Context): List<Entry> {
        val arr = JSONArray(sp(ctx).getString(K_LEDGER, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.optLong("at"), o.optString("reason"), o.optInt("minutes"), o.optString("kind"))
        }.reversed()
    }

    // ---- state -------------------------------------------------------------

    /** True when the phone should be locked down right now. */
    fun active(ctx: Context) = (focusActive(ctx) || sleepActive(ctx)) && !granted(ctx)

    fun reason(ctx: Context): String = when {
        granted(ctx) -> "Unlocked for ${remainingMinutes(ctx)} more min"
        focusActive(ctx) -> "Focus lock, ${remainingMinutes(ctx)} min left"
        sleepActive(ctx) -> "Sleep lock until %02d:00".format(sleepTo(ctx))
        else -> "Off"
    }

    fun remainingMinutes(ctx: Context): Int {
        val end = when {
            granted(ctx) -> grantUntil(ctx)
            focusActive(ctx) -> focusUntil(ctx)
            else -> return 0
        }
        return ((end - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0)
    }

    // ---- enforcement -------------------------------------------------------

    /**
     * Packages that are never suspended, whatever else happens.
     *
     * The dialer is resolved rather than hardcoded, because the Samsung dialer, the Google dialer
     * and whatever ships next are all different package names and a wrong guess here means a phone
     * that cannot make a call.
     */
    private fun untouchable(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val keep = mutableSetOf(ctx.packageName)

        fun resolve(intent: Intent) = runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()

        // calls, in and out
        resolve(Intent(Intent.ACTION_DIAL))?.let { keep.add(it) }
        resolve(Intent(Intent.ACTION_CALL, Uri.parse("tel:0")))?.let { keep.add(it) }
        runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(ctx) }
            .getOrNull()?.let { keep.add(it) }
        // the launcher, or the phone is a black rectangle
        resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let { keep.add(it) }
        // Samsung splits calling across several packages; suspending any of them breaks dialling
        keep.addAll(listOf(
            "com.samsung.android.dialer", "com.samsung.android.incallui",
            "com.android.dialer", "com.google.android.dialer",
            "com.android.server.telecom", "com.samsung.android.messaging",
            "com.google.android.apps.messaging", "com.android.phone",
            "com.android.settings", "com.samsung.android.app.contacts",
            "com.android.contacts", "com.google.android.contacts"
        ))
        return keep
    }

    /**
     * Apply or lift the lock. Suspends every launchable non-system app outside the allowlist.
     *
     * Only launchable apps are touched: suspending a background service or a system component
     * would break things nobody asked to block, and setPackagesSuspended refuses most of them
     * anyway. Safe to call repeatedly, it computes the target state and sets it.
     */
    fun apply(ctx: Context): Int {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return 0

        val on = active(ctx)
        val keep = untouchable(ctx) + allowed(ctx)

        val launchable = runCatching {
            ctx.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
            ).map { it.activityInfo.packageName }.distinct()
        }.getOrDefault(emptyList())

        // Colour comes off for the whole lock session, and stays off through an emergency and
        // through a reasoned unlock.
        //
        // This is the part that cannot be argued with. Every other layer can be talked past,
        // write a good enough sentence and the phone opens, but nothing you type brings the
        // colour back. So even a won argument still feels like the lock is on, and the reward for
        // lying your way in is a grey phone. Saturation is most of what makes a feed compulsive;
        // removing it costs nothing you actually need and takes away the thing being chased.
        // Colour is held for the whole session INCLUDING any grant, so an emergency or a won
        // argument still leaves you with a grey phone.
        val session = colourSettingLocked(ctx)
        runCatching { trackNight(ctx) }
        runCatching {
            if (session && greyOnLock(ctx)) {
                if (!sp(ctx).contains(K_GREY_BEFORE)) {
                    // Remember whether greyscale was already their own choice, so ending a lock
                    // restores what they had rather than switching off something they wanted.
                    sp(ctx).edit().putBoolean(K_GREY_BEFORE, Monochrome.isOn(ctx)).apply()
                }
                if (!Monochrome.isOn(ctx)) Monochrome.set(ctx, true)
            } else if (sp(ctx).contains(K_GREY_BEFORE)) {
                val before = sp(ctx).getBoolean(K_GREY_BEFORE, false)
                sp(ctx).edit().remove(K_GREY_BEFORE).apply()
                if (!before) Monochrome.set(ctx, false)
            }
        }

        // Unsuspend from the recorded list, not from a fresh query.
        //
        // This was a genuine trap. A suspended app stops coming back from the LAUNCHER query, so
        // once the lock was on, the live list no longer contained the very packages that needed
        // releasing, the lock could be applied and then never lifted. Testing caught a phone
        // stuck suspended with no active lock at all. So: write down exactly what was suspended,
        // and lift exactly that.
        val remembered = sp(ctx).getString(K_SUSPENDED, "").orEmpty()
            .split(",").filter { it.isNotBlank() }
        val targets = (if (on) launchable.filter { it !in keep }
                       else (remembered + launchable.filter { it !in keep }).distinct())
            .toTypedArray()
        if (targets.isEmpty()) return 0

        return runCatching {
            // Returns the packages it could NOT change, so the count is what actually moved.
            val failed = dpm.setPackagesSuspended(admin, targets, on)
            sp(ctx).edit().putString(K_SUSPENDED,
                if (on) targets.filter { it !in failed }.joinToString(",") else "").apply()
            targets.size - failed.size
        }.getOrDefault(0)
    }

    // ---- nights held -------------------------------------------------------

    private const val K_NIGHTS = "lock_nights_held"
    private const val K_NIGHT_PENDING = "lock_night_pending"
    private const val K_NIGHT_BROKEN = "lock_night_broken"

    /**
     * Which night a sleep window belongs to. A window that wraps past midnight is still last
     * night's, otherwise every night would be counted twice, once either side of 00:00.
     */
    private fun nightKey(ctx: Context): String {
        val c = Calendar.getInstance()
        if (c.get(Calendar.HOUR_OF_DAY) < sleepFrom(ctx)) c.add(Calendar.DAY_OF_YEAR, -1)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK).format(c.time)
    }

    /**
     * Count a night only once it has actually finished, and only if it was not opened with a
     * reasoned unlock.
     *
     * Emergencies deliberately do not break the night. Three five-minute openings exist for real
     * problems, and a badge that punishes you for ringing a hospital would be teaching the wrong
     * thing entirely. Talking your way out for two hours is the one that breaks it.
     */
    private fun trackNight(ctx: Context) {
        val s = sp(ctx)
        if (sleepActive(ctx)) {
            val k = nightKey(ctx)
            if (s.getString(K_NIGHT_PENDING, "") != k) {
                s.edit().putString(K_NIGHT_PENDING, k).apply()
            }
            return
        }
        val pending = s.getString(K_NIGHT_PENDING, "").orEmpty()
        if (pending.isBlank()) return
        val held = pending != s.getString(K_NIGHT_BROKEN, "")
        s.edit().apply {
            remove(K_NIGHT_PENDING)
            if (held) putInt(K_NIGHTS, s.getInt(K_NIGHTS, 0) + 1)
        }.apply()
    }

    fun nightsHeld(ctx: Context) = sp(ctx).getInt(K_NIGHTS, 0)

    /** When tonight's sleep window closes, as an absolute time. */
    fun sleepEndsAt(ctx: Context): Long {
        val c = Calendar.getInstance()
        val to = sleepTo(ctx)
        if (c.get(Calendar.HOUR_OF_DAY) >= to) c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, to)
        c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
        return c.timeInMillis
    }

    private fun breakNight(ctx: Context) {
        if (sleepActive(ctx)) sp(ctx).edit().putString(K_NIGHT_BROKEN, nightKey(ctx)).apply()
    }

    /** Lift everything. Called when a lock ends so nothing is left suspended by accident. */
    fun release(ctx: Context) {
        sp(ctx).edit().putLong(K_FOCUS_UNTIL, 0L).putLong(K_GRANT_UNTIL, 0L).apply()
        apply(ctx)
    }

    // ---- the reasoned unlock ----------------------------------------------

    data class Verdict(
        val genuine: Double,      // 0..1
        val minutes: Int,
        val note: String,
        val offline: Boolean = false
    ) {
        val allowed get() = genuine >= GENUINE_THRESHOLD && minutes > 0
    }

    /** Above this, the reason is taken at face value and the phone opens. */
    const val GENUINE_THRESHOLD = 0.7

    /**
     * What a real need looks like, offline.
     *
     * This exists because the model is the wrong thing to depend on for getting into your own
     * phone. Keys expire, aeroplane mode happens, and the whole point of a sleep lock is that it
     * bites at 3am, which is exactly when you do not want your phone's escape hatch making an
     * HTTPS call. So the local scorer runs every time and the model only ever overrides it.
     *
     * Ordered most urgent first; the highest-scoring match wins rather than the first one.
     */
    private val NEEDS: List<Triple<Double, Int, List<String>>> = listOf(
        // genuine emergencies
        Triple(1.0, 120, listOf("emergency", "hospital", "ambulance", "accident", "doctor",
            "police", "fire", "died", "death", "surgery", "icu", "urgent family")),
        // money moving, their example, and the one that genuinely cannot wait
        Triple(0.95, 30, listOf("upi", "gpay", "google pay", "phonepe", "paytm", "send money",
            "transfer money", "transaction", "payment", "pay ", "paying", "bank", "netbanking",
            "refund", "bill", "emi", "rent", "salary", "invoice")),
        // codes and sign-ins, the authenticator case
        Triple(0.95, 15, listOf("authenticator", "otp", "2fa", "two factor", "two-factor",
            "verification code", "verify", "code to log", "login", "log in", "sign in",
            "signin", "password reset", "recovery code")),
        // work that is happening now
        Triple(0.9, 60, listOf("client", "meeting", "interview", "presentation", "deadline",
            "standup", "stand-up", "zoom", "google meet", "teams call", "conference",
            "on call", "oncall", "production", "outage", "incident", "boss", "manager")),
        // documents and files
        Triple(0.85, 30, listOf("document", "documents", "file", "pdf", "attach", "attachment",
            "share the", "send the", "sign the", "contract", "offer letter", "resume", "cv",
            "ticket", "boarding", "aadhaar", "aadhar", "pan card", "passport")),
        // getting somewhere
        Triple(0.85, 30, listOf("cab", "uber", "ola", "taxi", "auto", "flight", "train",
            "bus", "airport", "station", "directions", "maps", "navigate", "pickup",
            "pick up", "drop off")),
        // food and household
        Triple(0.8, 30, listOf("order food", "food", "hungry", "swiggy", "zomato", "blinkit",
            "zepto", "instamart", "grocery", "groceries", "dinner", "lunch", "breakfast",
            "medicine", "pharmacy", "delivery", "courier", "amazon", "flipkart")),
        // someone else needs you
        Triple(0.8, 30, listOf("mum", "mom", "mother", "dad", "father", "wife", "husband",
            "sister", "brother", "family", "friend needs", "help my", "helping my"))
    )

    /** Things that are not reasons, whatever else is in the sentence. */
    private val HOLLOW = listOf(
        "bored", "boring", "just checking", "just want", "just need to see", "nothing",
        "scroll", "scrolling", "reels", "shorts", "feed", "timeline", "instagram", "insta",
        "tiktok", "snapchat", "twitter", "reddit", "youtube", "netflix", "browse", "browsing",
        "kill time", "pass time", "time pass", "timepass", "curious", "check my phone"
    )

    /**
     * Score a reason locally. Pure and deterministic, so [selfTest] can hold it to account.
     *
     * Not a bag of words: a hollow phrase caps the score no matter how many good words sit
     * around it, because "I'm bored, might order food" is the sentence this whole feature exists
     * to catch.
     */
    fun scoreLocally(reason: String): Verdict {
        val s = reason.lowercase().trim()
        if (s.length < 12) return Verdict(0.0, 0, "Write a bit more than that.", true)

        val hollow = HOLLOW.any { s.contains(it) }
        val best = NEEDS.filter { (_, _, terms) -> terms.any { s.contains(it) } }
            .maxByOrNull { it.first }

        if (best == null) {
            // Nothing recognised. Do not refuse outright, the phrasing may simply be unusual,
            // but do not open the phone for two hours on an unread sentence either.
            return Verdict(
                if (hollow) 0.1 else 0.55, if (hollow) 0 else 15,
                if (hollow) "That is the sentence this lock exists for."
                else "Could not read that as a specific need. The five-minute emergency is there " +
                    "if you need it now.",
                true
            )
        }

        val (score, minutes, _) = best
        if (hollow) return Verdict(
            0.35, 0,
            "There is a real reason in there, but it is wrapped in one that is not.", true
        )

        // A longer, more specific sentence is a slightly better signal than three words.
        val detail = if (s.length >= 40) 0.03 else 0.0
        return Verdict(
            (score + detail).coerceAtMost(1.0), minutes,
            "Read as genuine. $minutes minutes.", true
        )
    }

    /**
     * Read the reason and decide how long it is worth, capped at two hours.
     *
     * Two judges, and the local one is the floor rather than the fallback: it always runs, and
     * the model only replaces its verdict when it actually answers. A keyword list cannot tell
     * "send money to my brother, his rent is due tonight" from "send a message", so the model is
     * worth having, but depending on it would mean a dead key or no signal locks you out of your
     * own phone at 3am, which is a far worse failure than being let in when you did not need it.
     *
     * Blocking. Callers run this off the main thread.
     */
    fun judge(ctx: Context, reason: String): Verdict {
        // Three readers, weakest first, each only allowed to improve on the last.
        //   1. word list, always works, cannot generalise
        //   2. sentence model, on device, understands meaning, no network needed
        //   3. Gemini, best judgement, but only when it can be reached
        // Nothing may lower a score another reader has already given, because every failure mode
        // of a smarter reader ends with someone locked out of their own phone at 3am.
        val words = scoreLocally(reason)
        var local = words
        if (reason.length >= 12) {
            Semantics.read(ctx, reason)?.let { r ->
                if (r.genuine > local.genuine) local = Verdict(
                    r.genuine, r.minutes,
                    // The note has to follow the verdict. Testing caught this printing "Read as
                    // genuine. 120 minutes." directly above "(genuine 0.36, needs 0.7)" on a
                    // refusal, which reads as the app arguing with itself.
                    if (r.genuine >= GENUINE_THRESHOLD) "Read as genuine."
                    else "That did not read as a specific need.",
                    offline = true
                )
            }
        }

        val k = Coach.key(ctx)
        if (k.isBlank() || reason.isBlank()) return local

        val prompt = """
You decide whether to unlock someone's phone. They locked it themselves, on purpose, to sleep or
to focus. They are asking for it back and have given a reason.

Their reason: "${reason.trim()}"

Judge how genuine the need is, 0.0 to 1.0. Be fair, not strict, this is an adult managing their
own phone, and a real need must get through. Score high for: paying or transferring money, a UPI
transaction, an authenticator or verification code needed to sign in, a client meeting or work
that is happening now, sharing a document someone is waiting for, ordering food, booking travel,
a family member needing something, anything medical or an emergency.
Score low only for: boredom, scrolling, social media, "just checking", or no actual task.

Then choose the smallest time that genuinely covers it:
  15  one quick task, a code, one message, one payment
  30  an errand, food, a cab, a document, a transaction with steps
  60  something involved, a meeting, a work problem, helping someone
  120 an emergency or something unavoidable that will take real time

Do not moralise and do not lecture. Reply as JSON only:
{"genuine": <0.0-1.0>, "minutes": <0,15,30,60,120>, "note": "<one short sentence to them>"}
""".trim()

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 200)
                put("responseMimeType", "application/json")
            })
        }

        return try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.5-flash:generateContent?key=$k"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12000
                readTimeout = 25000
                setRequestProperty("Content-Type", "application/json")
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            if (conn.responseCode !in 200..299) return local

            val text = JSONObject(conn.inputStream.bufferedReader().readText())
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text").orEmpty()

            val o = JSONObject(text)
            val genuine = o.optDouble("genuine", local.genuine).coerceIn(0.0, 1.0)
            val minutes = o.optInt("minutes", local.minutes).coerceIn(0, REASON_CAP_MINUTES)
            val note = o.optString("note").ifBlank { local.note }
            // The model can open a door the keywords missed, but it never shuts one they opened.
            // A model having an off day is not a reason to be locked out of your own phone.
            if (genuine >= local.genuine) Verdict(genuine, minutes, note)
            else local
        } catch (e: Exception) {
            local
        }
    }

    /**
     * ponytail: one runnable check. Two things here can be silently wrong and never throw, the
     * window maths across midnight, and whether a real reason actually gets you into your phone
     * with no network. Both are checked with the sentences that will really be typed.
     */
    fun selfTest(): String {
        fun inWindow(h: Int, f: Int, t: Int) = if (f <= t) h in f until t else (h >= f || h < t)
        val windows = listOf(
            Triple(23, 23, 6) to true,    // start of a wrapping window
            Triple(2, 23, 6) to true,     // middle, past midnight
            Triple(6, 23, 6) to false,    // exclusive end
            Triple(22, 23, 6) to false,   // just before
            Triple(13, 9, 17) to true,    // same-day window
            Triple(17, 9, 17) to false,
            Triple(8, 9, 17) to false
        )
        val badWindows = windows.filter {
            inWindow(it.first.first, it.first.second, it.first.third) != it.second
        }

        // Offline scoring, using the reasons actually given as examples.
        val reasons = listOf(
            "I want to order food, I have not eaten all day" to true,
            "I want to send money to someone right now" to true,
            "There is a UPI transaction that needs to be done, it is important" to true,
            "I have an important meeting with the client" to true,
            "I need my authenticator to give the code and log in" to true,
            "There is a document in my application that I want to share immediately" to true,
            "Need to book a cab to the airport" to true,
            "My mother is in hospital and I need to call the doctor" to true,
            // and the ones that must not get through
            "I am bored" to false,
            "just checking something" to false,
            "I want to scroll instagram for a bit" to false,
            "bored, might order food later" to false,   // hollow phrase caps a good one
            "nothing really" to false
        )
        val badReasons = reasons.filter { (text, expected) ->
            scoreLocally(text).allowed != expected
        }

        val total = windows.size + reasons.size
        val bad = badWindows.size + badReasons.size
        return if (bad == 0) "Lock self-test: $total/$total pass"
        else "Lock self-test FAILED on: " +
            (badWindows.map { "${it.first}" } + badReasons.map { "\"${it.first}\"" })
                .joinToString("; ")
    }
}
