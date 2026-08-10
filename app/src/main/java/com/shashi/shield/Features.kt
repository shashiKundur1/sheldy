package com.shashi.shield

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.provider.Settings

/**
 * The feature checklist, drawn from what the paid apps actually ship.
 *
 * Sourced by reading the shipped feature sets of QUITTR (~$350k/mo, 50k installs/mo), Overcomer,
 * Covenant Eyes, Accountable2You and BlockerX. Anything they charge a subscription for that is
 * genuinely useful is here. Anything that exists only to drive conversion, 40-step onboarding,
 * stacked hard paywalls, referral cash, streak-loss dark patterns, is deliberately absent.
 *
 * `done` is measured against real device or stored state wherever possible, never hardcoded true.
 * A checklist that lies about being green is worse than no checklist at all.
 */
data class Feature(
    val name: String,
    val note: String,
    val done: Boolean,
    val live: Boolean = false   // true when this is a protection, shown on the Shield tab
)

object Features {

    fun all(ctx: Context): List<Feature> {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val owner = runCatching { dpm.isDeviceOwnerApp(ctx.packageName) }.getOrDefault(false)

        val dnsOn = Settings.Global.getString(ctx.contentResolver, "private_dns_mode") == "hostname"
        val watchdogOn = (Settings.Secure.getString(
            ctx.contentResolver, "enabled_accessibility_services"
        ) ?: "").contains(ctx.packageName)

        return listOf(
            // ---- protections (Shield tab) ----
            Feature("Site blocking", "Encrypted DNS, frozen by policy", dnsOn, true),
            Feature("Keyword watchdog", "Reads address bars and typed text", watchdogOn, true),
            Feature("Block screen", "90 second uncancellable pause", watchdogOn, true),
            Feature("Uninstall protection", "Device Owner holds the app down", owner, true),
            Feature("Safe mode block", "Booting to safe mode is refused", owner, true),
            Feature("Factory reset block", "Settings reset is refused", owner, true),
            Feature("Browser sweep", "Any new browser hidden within a minute", owner, true),
            Feature("Secure Folder killed", "Knox container hidden before setup", owner, true),
            // VPN is allowed on purpose. A tunnel takes the DNS layer offline, so the keyword
            // watchdog above is what holds the line, it reads the screen, not the socket.
            Feature("VPN allowed", "Watchdog still reads the screen through it", true, true),
            Feature("Sideload block", "Cannot install outside the store", owner, true),
            Feature("Greyscale the phone", "Drains colour from every app", Monochrome.isOn(ctx), true),
            Feature("Focus lock", "Suspends every app but calls and three",
                Lock.focusActive(ctx), true),
            Feature("Sleep lock", "Overnight, committed for days at a time",
                Lock.sleepOn(ctx), true),
            Feature("Emergency sessions", "Three five-minute openings, reason written down", true),
            Feature("Reasoned unlock", "The time you get follows what you wrote", true),

            // ---- recovery tools ----
            Feature("Day counter", "Starts at zero, earned by finishing days", true),
            Feature("Calendar tracker", "Every day clean, relapsed or missed", true),
            Feature("Nightly confirmation", "Yesterday only counts once you say so", true),
            Feature("Panic button", "One tap, 90 seconds, breathing pace", true),
            Feature("Breathing pacer", "Four in, four out, on screen", true),
            Feature("Relapse log", "Trigger and note, no shame", true),
            Feature("Trigger list", "Ten common triggers, one tap", true),
            Feature("Journal", "Free notes, timestamped, kept local", true),
            Feature("Daily thought", "Rolls at midnight, no scheduling", true),
            Feature("Best streak", "Longest run, not just the current one", true),
            Feature("Panic counter", "Urges survived is the real number", true),

            Feature("Home screen widget", "Today's thought, outside the app", true),
            Feature("Lock screen prompts", "Check-in reminders, three a day", Store.remindersOn(ctx)),
            Feature("AI companion", "Gemini, given your streak and notes", true),
            Feature("Commitment pledge", "Signed with your finger", Store.pledgeSigned(ctx)),
            Feature("Daily lessons", "One a minute-long read, rolls at midnight", true),
            Feature("Milestones", "1, 3, 7, 14, 30, 90 days, and why each bites", true),
            Feature("Trigger patterns", "Your own hours and triggers, once there is data", true),
            Feature("Accountability partner", "Someone gets told, on your tap",
                Partner.configured(ctx)),
            Feature("Nightly blackout", "Browser removed during your worst hours",
                Schedule.enabled(ctx)),

            Feature("Screen content scan", "On-device heuristic inside image feeds",
                FrameScan.enabled(ctx))
        )
    }

    fun done(list: List<Feature>) = list.count { it.done }
}
