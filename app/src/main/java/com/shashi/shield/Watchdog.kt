package com.shashi.shield

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Keyword layer. Deliberately precise: address bars and text you type, not whole-page body text.
 * Scanning page bodies sounds stronger but blocks news articles and recovery forums, and you stop
 * trusting it within a day.
 */
object Filter {

    /**
     * Obfuscation normalisation. This matters more than the word list.
     *
     * Ranking the brand tokens across 64,220 hosts in the public porn blocklist showed the same
     * thing over and over: porno365, rexporn, pornxp, porngo, xnxxx, xxnxx are all one stem
     * wearing a hat. So rather than enumerate thousands of variants, the input is folded first
     * and a short list of stems is matched against the fold.
     *
     * Folding handles: case, leetspeak (p0rn, pr0n, s3x, @, $), separators (p.o.r.n, p-o-r-n,
     * p o r n, p_o_r_n) and stretched letters (poooorn).
     */
    private fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase()) {
            when (c) {
                '0' -> sb.append('o'); '1' -> sb.append('i'); '3' -> sb.append('e')
                '4' -> sb.append('a'); '5' -> sb.append('s'); '7' -> sb.append('t')
                '8' -> sb.append('b'); '@' -> sb.append('a'); '$' -> sb.append('s')
                '!' -> sb.append('i'); '|' -> sb.append('i')
                else -> if (c.isLetterOrDigit()) sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Same fold, but keeping word gaps, for the boundary-matched list. */
    private fun foldSpaced(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase()) {
            when (c) {
                '0' -> sb.append('o'); '1' -> sb.append('i'); '3' -> sb.append('e')
                '4' -> sb.append('a'); '5' -> sb.append('s'); '7' -> sb.append('t')
                '8' -> sb.append('b'); '@' -> sb.append('a'); '$' -> sb.append('s')
                '!' -> sb.append('i'); '|' -> sb.append('i')
                else -> if (c.isLetterOrDigit()) sb.append(c) else sb.append(' ')
            }
        }
        return sb.toString()
    }

    /** Collapse stretched letters: poooorn -> porn. Kept separate so xxx survives intact. */
    private val RUNS = Regex("(.)\\1+")
    private fun squash(s: String) = RUNS.replace(s) { it.groupValues[1] }

    /**
     * Stems and distinct brands, matched as substrings against the fold. Chosen so that the
     * hundreds of derivative domains in the blocklist collapse onto them automatically.
     */
    private val STRONG = listOf(
        // core stems, these swallow most of the long tail
        "porn", "xxx", "nsfw", "hentai", "camgirl", "camwhore", "nudify",
        "deepnude", "sextape", "sexvid", "sexcam", "sexchat", "livesex", "hdsex", "freesex",
        "milfs", "gangbang", "blowjob", "handjob", "creampie", "cumshot", "deepthroat",
        // "hardcore"/"softcore" were here and had to go: hardcore Minecraft, hardcore gaming,
        // hardcore workout. Substring-matched, so they poisoned ordinary YouTube searches while
        // adding nothing, every site they described is already named below.
        "bukkake", "fisting", "bdsm", "erotica", "smut", "fapp",
        "jerkoff", "masturbat", "orgasm", "orgy", "swinger", "incest", "upskirt", "voyeur",
        "stripcam", "escortservice",
        // tube and cam brands seen most in the blocklist
        "xvideo", "xnxx", "xhamster", "pornhub", "redtube", "youporn", "youjizz", "tnaflix",
        "spankbang", "eporner", "motherless", "chaturbate", "bongacams", "stripchat", "camsoda",
        "myfreecams", "livejasmin", "cam4", "erome", "vlxx", "txxx", "upornia", "hclips",
        "hotmovs", "drtuber", "sunporno", "hqporner", "sxyprn", "daftsex", "porntrex",
        "pornone", "porngo", "porn300", "brazzers", "bangbros", "realitykings", "naughtyamerica",
        "mofos", "digitalplayground", "evilangel", "teamskeet", "nubiles", "twistys", "dorcel",
        "onlyfans", "fansly", "manyvids", "clips4sale", "justforfans",
        // leak and aggregator ecosystems
        "thothub", "coomer", "kemono", "fapello", "leakedzone", "nudostar", "dirtyship",
        "simpcity", "influencersgonewild", "gonewild", "imagefap", "imagetwist", "sexart",
        // anime / manga adult
        "rule34", "booru", "doujin", "nhentai", "hanime", "javhd", "javbus", "javlibrary",
        "missav", "sukebei", "ecchi"
    )

    /**
     * Boundary-matched, because each is a substring of something innocent:
     * jav/java, nude/denude, milf/milfoil, escort/escorted, tits/titsanity, anal/analysis,
     * cum/cumulative, ass/assignment, sex/Sussex. These need whole-word matches or the filter
     * becomes unusable within a day.
     */
    private val WEAK = listOf(
        // "pron"/"prn" are boundary-matched, not substrings: "pron" sits inside "pronounce",
        // "pronoun" and "prone". Folded input means "pr0n hd" still lands here.
        "pron", "prn",
        "nude", "nudes", "nudity", "naked", "milf", "escort", "erotic", "fetish", "kink",
        "boobs", "tits", "titties", "anal", "cum", "jav", "sexting", "r34", "nsfl",
        // Dropped: "strip"/"stripper" (comic strip, LED strip, wire stripper, paint stripper) and
        // "hook up" (hook up a subwoofer). stripcam/stripchat in STRONG cover the real ones.
        "sex", "sexy", "sexual", "horny", "slut", "whore", "lewd",
        "cam girl", "sex video", "sex videos", "sex tape", "adult video", "adult movie",
        "adult film", "leaked nudes", "nude pics", "nude photos", "only fans"
    )

    /**
     * The bypass layer, and the one most blockers forget.
     *
     * Nobody defeats a filter by searching for porn. They search "how to remove device owner"
     * first, and every keyword list on earth waves that straight through because not one word of
     * it is explicit. These are the searches that come *before* the relapse, which makes them the
     * more useful thing to catch.
     *
     * Matched as folded phrases so spacing and punctuation do not matter. The genuine escape
     * routes are documented in SETUP.md on the Mac, deliberately somewhere this phone cannot
     * reach, needing a laptop is the friction, and looking it up on the phone at 1am is exactly
     * the behaviour being blocked.
     *
     * Cut back to self-defence only. The network-evasion, tunnel, root and sideload sections are
     * gone: they were substring-matched with no word boundaries, so "NordVPN download" folded to
     * something containing "vpndownload" and died, and VPN use is now a supported thing rather
     * than an attack. What is left is the one thing a search engine could actually teach you,
     * how to take this app off the phone.
     */
    private val BYPASS = listOf(
        // undoing the management layer
        "deviceowner", "deviceadmin", "removeadmin", "removedeviceowner", "cleardeviceowner",
        "dpmremove", "removeactiveadmin", "disabledeviceadmin", "deactivatedeviceadmin",
        "mdmremove", "removemdm", "bypassmdm", "knoxremove",
        // undoing this app specifically
        "uninstallblockedapp", "cantuninstall", "cannotuninstall", "forceuninstall",
        "uninstallsheldy", "removesheldy", "disablesheldy", "killsheldy",
        // the accessibility service
        "disableaccessibility", "turnoffaccessibility", "stopaccessibilityservice",
        "accessibilityserviceremove"
    )

    /**
     * Looking for help must never be blocked, and neither must ordinary life. This wins over both
     * lists above, which is why bare "sex" can safely sit in WEAK: sex education, sexual health
     * and sexuality all land here first.
     */
    private val ALLOW = listOf(
        "addiction", "recovery", "recover", "nofap", "quit", "quitting", "stop watching",
        "rehab", "therapy", "therapist", "counsel", "relapse", "abstain", "sobriety",
        "support group", "help me", "sex education", "sexual health", "sexuality",
        "safe sex", "sexually transmitted", "sex ed", "reproduction", "puberty", "consent",
        "gender", "harassment", "assault", "abuse", "trafficking", "sexism", "biology",
        "anatomy", "medical", "doctor", "clinic", "hospital", "pregnan", "contracept"
    )

    private val STRONG_SET = STRONG.map { fold(it) }
    private val BYPASS_SET = BYPASS.map { fold(it) }
    private val WEAK_RE = Regex(
        WEAK.joinToString("|") { """\b${Regex.escape(it)}\b""" }, RegexOption.IGNORE_CASE
    )
    private val ALLOW_RE = Regex(
        ALLOW.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE
    )

    fun hit(s: String?): Boolean {
        if (s == null || s.length < 3) return false

        val spaced = foldSpaced(s)
        if (ALLOW_RE.containsMatchIn(spaced) || ALLOW_RE.containsMatchIn(s)) return false

        // Substring match against both the plain fold and the stretch-collapsed fold, so
        // "p.o.r.n", "p0rn" and "poooorn" all land on the same stem.
        val compact = fold(s)
        val squashed = squash(compact)
        if (STRONG_SET.any { compact.contains(it) || squashed.contains(it) }) return true

        // Circumvention searches. Checked against the fold with separators stripped, so
        // "remove device owner", "remove-device-owner" and "removedeviceowner" are one thing.
        if (BYPASS_SET.any { compact.contains(it) }) return true

        return WEAK_RE.containsMatchIn(spaced)
    }

    /** ponytail: one runnable check. Includes the evasion cases, not just the easy ones. */
    fun selfTest(): String {
        val cases = listOf(
            // must block
            "pornhub.com" to true,
            "xnxx" to true,
            "pporn" to true,
            "p0rn" to true,
            "p.o.r.n" to true,
            "p o r n" to true,
            "P-O-R-N" to true,
            "poooorn" to true,
            "pr0n hd" to true,
            "x videos" to true,
            "onlyfans leaks" to true,
            "rule34" to true,
            "nhentai" to true,
            "best nude photos" to true,
            "she is so horny" to true,
            "chaturbate" to true,
            "porno365" to true,
            // circumvention, narrowed to taking the app off the phone
            "how to remove device owner android" to true,
            "remove-device-owner" to true,
            "uninstall blocked app" to true,
            "disable accessibility service" to true,
            // must never block, the network is allowed to be free, the screen is not
            "nordvpn download" to false,
            "best vpn for android" to false,
            "linkedin login" to false,
            "hardcore minecraft ep 1" to false,
            "how to hook up a subwoofer" to false,
            "led strip lights diy" to false,
            "wire stripper tool review" to false,
            "how to quit porn addiction" to false,
            "nofap streak day 12" to false,
            "porn addiction therapy near me" to false,
            "sex education netflix" to false,
            "sexual health clinic" to false,
            "java tutorial" to false,
            "pronounce this word" to false,
            "assignment deadline" to false,
            "sussex university" to false,
            "cumulative total" to false,
            "analysis of results" to false,
            "ab" to false
        )
        val bad = cases.filter { hit(it.first) != it.second }
        return if (bad.isEmpty()) "Filter self-test: ${cases.size}/${cases.size} pass"
        else "Filter self-test FAILED on: " + bad.joinToString("; ") { it.first }
    }
}

class Watchdog : AccessibilityService() {

    private var lastTreeScan = 0L
    private var lastBlock = 0L
    private var lastSweep = 0L
    private var lastLockState = false
    private var overlay: View? = null
    private val ui = Handler(Looper.getMainLooper())

    /**
     * Read the label at runtime, never hardcode it. A previous build compared against a literal
     * "Shield"; renaming the app silently disabled the settings guard, which is exactly the kind
     * of failure nothing would have reported.
     */
    private val appLabel: String by lazy {
        runCatching { applicationInfo.loadLabel(packageManager).toString() }.getOrDefault("Sheldy")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sweep()   // catch anything installed while the service was down, including after a reboot
    }

    /**
     * Hides any newly installed browser, and applies the scheduled nightly blackout.
     *
     * The watchdog is the only component guaranteed to still be running, so it drives both. Off
     * the main thread because setApplicationHidden is slow.
     */
    private fun sweep() {
        lastSweep = System.currentTimeMillis()
        Thread {
            runCatching { Lockdown.sweepBrowsers(this) }
            // Retire an expired screen watch. The watchdog is the only thing guaranteed to be
            // running, so it is what enforces the end date.
            runCatching {
                if (FrameScan.expired(this)) {
                    FrameScan.setEnabled(this, false)
                    stopService(android.content.Intent(this, ScreenGuardService::class.java))
                }
            }
            runCatching {
                val shouldBlackout = Schedule.active(this)
                if (shouldBlackout != Lockdown.blackoutActive(this)) {
                    Lockdown.setBrowserBlackout(this, shouldBlackout)
                }
            }
            // Focus and sleep locks. Driven from here for the same reason as everything else:
            // this service is the only thing on the device guaranteed to still be running, so a
            // lock that ends at 06:00 lifts itself whether or not the app was ever opened.
            runCatching { Lock.apply(this) }
            // Load the sentence encoder once, here, on a background thread. Doing it lazily when
            // the unlock screen opens would mean a two-second wait at the exact moment someone is
            // trying to get into their phone in a hurry.
            runCatching { Semantics.warm(this) }
        }.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        // Publish the foreground app so the screen scan can stay dormant outside image feeds.
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) FrameScan.foreground = pkg
        if (pkg == packageName) return
        if (overlay != null) return   // already blocking, stop scanning

        // Cheap path: typed text arrives here keystroke by keystroke, before search is submitted.
        for (t in e.text) {
            val s = t?.toString() ?: continue
            if (Filter.hit(s)) { block(s); return }
        }

        // A lock starting or a grant running out has to bite within seconds, not at the next
        // minute boundary, five minutes that quietly runs to six is not a five-minute rule.
        // Prefs are already in memory, so this is a field read per event, not a disk hit.
        val locked = runCatching { Lock.active(this) }.getOrDefault(lastLockState)
        if (locked != lastLockState) { lastLockState = locked; sweep(); return }

        // Expensive path: walking the node tree. Throttled or it eats the battery.
        val now = System.currentTimeMillis()
        if (now - lastSweep > 60_000) sweep()   // also flips the scheduled blackout   // a browser installed from Play is gone within a minute
        if (now - lastTreeScan < 400) return
        lastTreeScan = now

        // While a lock is running, two system screens need intercepting.
        if (Lock.active(this)) {
            // The power menu. A lock you can end by turning the phone off is a suggestion.
            // Honest limit: this dismisses the menu, it cannot stop a hardware force-restart
            // (power + volume held), because nothing running on Android can.
            if (pkg == "com.android.systemui" && isPowerMenu()) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
            // Android's own grey "app paused" dialog, replaced with ours. The system dialog says
            // nothing useful, not how long is left, not how to get out, and it does not look
            // like the thing that put it there.
            if (Lock.ownScreen(this) && isSuspendedDialog(e)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                ui.post { showLockOverlay() }
                return
            }
        }

        if (pkg.contains("settings", ignoreCase = true)) { guardOffSwitch(); return }

        val root = rootInActiveWindow ?: return
        for (id in URL_BARS) {
            for (n in root.findAccessibilityNodeInfosByViewId(id).orEmpty()) {
                val url = n.text?.toString() ?: continue
                if (Filter.hit(url)) { block(url); return }
            }
        }
    }

    /**
     * Bounces you out of this app's own accessibility toggle and app-info page, which is where
     * you would go to switch the watchdog off. Keyed on our label plus a dangerous action word,
     * so the rest of Settings stays usable.
     */
    private fun guardOffSwitch() {
        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collect(root, text, 0)
        val s = text.toString()

        // Never bounce an ordinary Settings page. The old rule fired on any screen mentioning
        // the app plus a broad word like "Device admin" or "Accessibility", which is most of
        // Samsung's Security index, so legitimate settings became unreachable. Anything that
        // looks like a general Security or Privacy page is exempt outright.
        if (EXEMPT.any { s.contains(it, ignoreCase = true) }) return

        if (!s.contains(appLabel, ignoreCase = true)) return

        // Only a screen offering a real off-switch for this app counts.
        if (DANGER.none { s.contains(it, ignoreCase = true) }) return
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (n == null || depth > 12 || out.length > 4000) return
        n.text?.let { out.append(it).append('\n') }
        n.contentDescription?.let { out.append(it).append('\n') }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    private fun block(reason: String) {
        // Diagnostic: log exactly what tripped the filter, so a false positive can be traced to
        // the term that caused it instead of guessed at.
        android.util.Log.i("SheldyFilter", "BLOCK matched on: >>>${reason.take(300)}<<<")
        val now = System.currentTimeMillis()
        if (now - lastBlock < 4000) return   // one intervention per attempt, not per keystroke
        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_HOME)   // leave the app first, then cover the screen
        ui.post { showOverlay(reason.take(120)) }
    }

    /**
     * TYPE_ACCESSIBILITY_OVERLAY, not an Activity. Android 14+ silently denies background
     * activity launches, so startActivity() from here never showed anything, the app just
     * appeared to close. An accessibility overlay is exempt and needs no extra permission.
     */
    private fun showOverlay(reason: String) {
        if (overlay != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        val view = buildBlockView(this, reason) { hideOverlay() }
        runCatching { wm.addView(view, lp) }.onSuccess { overlay = view }
    }

    /**
     * Is this the power menu? Matched on its contents rather than a class name, because the
     * global-actions dialog is a different class on every OEM skin and Samsung renames it between
     * One UI versions. Scoped tightly: only ever consulted while a lock is running and only for
     * SystemUI windows, so an ordinary notification shade cannot trip it.
     */
    private fun isPowerMenu(): Boolean {
        val root = rootInActiveWindow ?: return false
        return POWER_WORDS.any { w ->
            runCatching { root.findAccessibilityNodeInfosByText(w).orEmpty().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    /** The system's suspended-app dialog, by class name with a text fallback. */
    private fun isSuspendedDialog(e: AccessibilityEvent): Boolean {
        val cls = e.className?.toString().orEmpty()
        if (cls.contains("SuspendedApp", ignoreCase = true)) return true
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false
        val root = rootInActiveWindow ?: return false
        return runCatching {
            root.findAccessibilityNodeInfosByText("paused").orEmpty().isNotEmpty()
        }.getOrDefault(false)
    }

    /** The lock's own screen. No dismiss, it goes when the lock does. */
    private fun showLockOverlay() {
        if (overlay != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        val view = buildLockView(this) { hideOverlay() }
        runCatching { wm.addView(view, lp) }.onSuccess { overlay = view }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(v) }
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private companion object {
        /** Enough of the power menu to recognise it, in the wording One UI actually uses. */
        val POWER_WORDS = listOf("Power off", "Restart", "Emergency mode")

        val URL_BARS = listOf(
            "com.android.chrome:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.brave.browser:id/url_bar"
        )
        /**
         * Words that only appear where this app can actually be switched off: its own app-info
         * page and its accessibility entry. "Accessibility", "Device admin" and "Installed
         * service" were removed, they appear on index pages that have nothing to do with
         * disabling anything.
         */
        val DANGER = listOf(
            "Uninstall", "Force stop", "Deactivate", "Clear data", "Clear storage",
            "Turn off this service", "Stop service"
        )

        /**
         * Pages that must always stay reachable. If any of these appear, the screen is a normal
         * Settings page and is left alone no matter what else it mentions.
         */
        val EXEMPT = listOf(
            "Screen lock", "Biometrics", "Face recognition", "Fingerprint",
            "Find My Mobile", "Security update", "Google Play Protect", "Secure Wi-Fi",
            "Permission manager", "Privacy", "Auto Blocker", "Install unknown apps",
            "Encrypt", "Credential", "Backup", "Restore", "Accounts and backup",
            "Smart Switch", "Samsung Cloud", "Developer options", "USB debugging"
        )
    }
}
