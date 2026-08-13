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
    /**
     * Leetspeak, applied to letters only. Digits are handled separately, see [joined].
     */
    private fun deleet(c: Char): Char = when (c) {
        '@' -> 'a'; '$' -> 's'; '!' -> 'i'; '|' -> 'i'
        else -> c
    }

    private fun deleetDigit(c: Char): Char = when (c) {
        '0' -> 'o'; '1' -> 'i'; '3' -> 'e'; '4' -> 'a'
        '5' -> 's'; '7' -> 't'; '8' -> 'b'
        else -> c
    }

    /** Keeps word gaps, for the boundary-matched lists. Digits map, so "pr0n" reads as a word. */
    private fun foldSpaced(s: String): String {
        val sb = StringBuilder(s.length)
        for (raw in s.lowercase()) {
            val c = deleetDigit(deleet(raw))
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        return sb.toString()
    }

    /**
     * The de-spaced form the stems are matched against, and the single most delicate thing here.
     *
     * Deleting every separator catches "p.o.r.n" and "p o r n", but it also welds neighbouring
     * words together, and a stem that spans the seam blocks a sentence nobody would object to.
     * Measured against a 235,976-word dictionary, 83 of the 111 stems could be produced by an
     * innocent pair of words: "watch anime" made "hanime", "scam soda" made "camsoda", "wealth
     * clips" made "hclips", "mother less" made "motherless".
     *
     * So gaps close only where obfuscation actually puts them. Spelling a word out leaves runs of
     * one- and two-character fragments, and ordinary prose does not, so two tokens are welded only
     * when one of them is short. "p o r n" and "x videos" still fold shut; "watch anime" does not.
     */
    private fun joined(s: String): List<String> {
        val tokens = foldSpaced(s).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val run = StringBuilder(tokens[0])
        for (i in 1 until tokens.size) {
            if (tokens[i - 1].length <= 2 || tokens[i].length <= 2) run.append(tokens[i])
            else { out.add(run.toString()); run.setLength(0); run.append(tokens[i]) }
        }
        out.add(run.toString())
        return out
    }

    /** Digits left alone, so a brand that contains one ("cam4", "rule34") stays literal. */
    private fun joinedRaw(s: String): List<String> {
        val sb = StringBuilder(s.length)
        for (raw in s.lowercase()) {
            val c = deleet(raw)
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        val tokens = sb.toString().split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val run = StringBuilder(tokens[0])
        for (i in 1 until tokens.size) {
            if (tokens[i - 1].length <= 2 || tokens[i].length <= 2) run.append(tokens[i])
            else { out.add(run.toString()); run.setLength(0); run.append(tokens[i]) }
        }
        out.add(run.toString())
        return out
    }

    /**
     * Collapse stretched letters: poooorn -> porn.
     *
     * Runs of three or more only. The old rule collapsed doubles too, which quietly rewrote every
     * ordinary double letter in the language: "poorness" became "porness" and was blocked as porn.
     * Nobody stretches a letter to exactly two, that is just how English spells things.
     */
    private val RUNS = Regex("(.)\\1{2,}")
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
        "bukkake", "fisting", "bdsm", "fapp",
        "jerkoff", "masturbat", "upskirt",
        "stripcam", "escortservice",
        // tube and cam brands seen most in the blocklist
        "xvideo", "xnxx", "xhamster", "pornhub", "redtube", "youporn", "youjizz", "tnaflix",
        "spankbang", "chaturbate", "bongacams", "stripchat", "camsoda",
        "myfreecams", "livejasmin", "cam4", "vlxx", "txxx", "upornia", "hclips",
        "hotmovs", "drtuber", "sunporno", "hqporner", "sxyprn", "daftsex", "porntrex",
        "pornone", "porngo", "porn300", "brazzers", "bangbros", "realitykings", "naughtyamerica",
        "mofos", "digitalplayground", "evilangel", "teamskeet", "twistys",
        "onlyfans", "fansly", "manyvids", "clips4sale", "justforfans",
        // leak and aggregator ecosystems
        "thothub", "coomer", "kemono", "fapello", "leakedzone", "nudostar", "dirtyship",
        "simpcity", "influencersgonewild", "gonewild", "imagefap", "imagetwist",
        // anime / manga adult
        "rule34", "gelbooru", "danbooru", "safebooru", "doujin", "nhentai", "javhd", "javbus",
        "javlibrary", "missav", "sukebei",
        // Spelled with the TLD on purpose. "motherless" on its own is an ordinary English word,
        // and as a stem it blocked "sometimes i feel like a motherless child lyrics". Only the
        // domain form is matched here; the DNS layer covers the site itself.
        "motherlesscom"
    )

    /**
     * Whole-word matches. Two reasons a term lands here rather than in STRONG.
     *
     * Some are a substring of something innocent: jav/java, nude/denude, milf/milfoil,
     * escort/escorted, anal/analysis, cum/cumulative, sex/Sussex.
     *
     * The rest were in STRONG and were caught blocking real English. Scanned against a dictionary:
     * "smut" blocked bismuth, "erome" blocked accelerometer, "erotica" blocked sclerotica, "orgy"
     * blocked porgy, "swinger" blocked inswinger, "booru" blocked zumbooruk, "ecchi" blocked
     * zecchino. A whole-word rule keeps every one of them and loses nothing real.
     */
    private val HARD = listOf(
        // "pron"/"prn" sit inside "pronounce", "pronoun" and "prone". Folded input means
        // "pr0n hd" still lands here.
        // "r34" cannot go here: the fold maps digits to letters, so it would arrive as "rea".
        // rule34 and the booru brands in STRONG cover it, matched with digits left literal.
        "pron", "prn", "nsfl",
        "milf", "milfs", "slut", "sluts", "whore", "whores", "nudes", "sexting", "smut",
        "erotica", "orgy", "orgies", "orgasm", "orgasms", "swinger", "swingers", "voyeur",
        "incest", "booru", "ecchi", "hanime", "erome", "nubiles", "dorcel", "sexart",
        "camgirls", "escorts"
    )

    /**
     * Ambiguous on their own, so one of these alone is not enough. Every single one has a life
     * outside porn: the naked eye, a kink in the hose, cum laude, same-sex marriage, anal fissure,
     * a horny toad, Sexy Back. Blocking on any one of them is most of what made the filter feel
     * arbitrary, because the words turn up constantly and the sentences around them are innocent.
     *
     * Two signals are required, see [hit]. Dropped entirely: "strip"/"stripper" (comic strip, LED
     * strip, wire stripper) and "hook up" (hook up a subwoofer).
     */
    private val SOFT = listOf(
        "nude", "nudity", "naked", "escort", "erotic", "fetish", "kink", "kinky",
        "boobs", "tits", "titties", "anal", "cum", "jav", "sex", "sexy", "sexual",
        "horny", "lewd", "hookup", "camming"
    )

    /**
     * The second signal. Deliberately shaped like porn rather than like the internet: "free",
     * "watch", "hot", "best" and "download" were considered and left out, because "best naked eye
     * telescope" is a real search and it has to survive.
     */
    private val CONTEXT = listOf(
        "video", "videos", "vid", "vids", "hd", "xxx", "pic", "pics", "photo", "photos",
        "gif", "gifs", "clip", "clips", "tube", "webcam", "cam", "cams", "stream", "leaked",
        "teen", "teens", "milf", "onlyfans", "nsfw"
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
     * Looking for help must never be blocked. This wins over every list, including STRONG, so
     * "how to quit porn addiction" reaches the search engine even though "porn" is in it.
     *
     * Whole words, not substrings. As substrings these were a hole straight through the filter:
     * "quit" sits inside "quite", so any sentence containing the word "quite" switched the entire
     * filter off. Every stem here is spelled out in the forms it actually appears in instead.
     */
    private val RESCUE = listOf(
        "addiction", "addictions", "addicted", "addict", "recovery", "recovering", "recover",
        "nofap", "quit", "quitting", "rehab", "therapy", "therapist", "therapists",
        "counselling", "counseling", "counsellor", "counselor", "relapse", "relapsed",
        "relapsing", "abstain", "abstinence", "sobriety", "sober", "support group",
        "help me", "stop watching", "how to stop", "blocker", "blocking app"
    )

    /**
     * Ordinary life that happens to use the ambiguous words. Overrides HARD and SOFT but not
     * STRONG, so sexual health and sex education stay reachable while a tube-site name in the
     * same sentence still blocks. Bare "sex" can sit in SOFT because these land first.
     */
    private val ALLOW = listOf(
        "sex education", "sex ed", "sexual health", "sexuality", "safe sex",
        "sexually transmitted", "reproduction", "reproductive", "puberty", "consent",
        "gender", "harassment", "assault", "abuse", "trafficking", "sexism",
        "biology", "anatomy", "medical", "medicine", "doctor", "clinic", "hospital",
        "pregnancy", "pregnant", "contraception", "contraceptive", "fissure", "symptoms"
    )

    private val STRONG_SET = STRONG.map { it.filter { c -> c.isLetterOrDigit() } }
    private val BYPASS_SET = BYPASS.map { it.filter { c -> c.isLetterOrDigit() } }
    private fun word(list: List<String>) = Regex(
        list.joinToString("|") { """\b${Regex.escape(it)}\b""" }, RegexOption.IGNORE_CASE
    )
    private val HARD_RE = word(HARD)
    private val SOFT_RE = word(SOFT)
    private val CONTEXT_RE = word(CONTEXT)
    private val RESCUE_RE = word(RESCUE)
    private val ALLOW_RE = word(ALLOW)

    /** Every separator gone. Only the long bypass phrases are matched against this. */
    private fun compactAll(s: String) = foldSpaced(s).filter { !it.isWhitespace() }

    /** The strings a stem may be matched against, plus their stretch-collapsed forms. */
    private fun candidates(s: String): List<String> {
        val out = joined(s) + joinedRaw(s)
        // A string with no whitespace is a URL or a single token, so closing every gap in it is
        // safe: there are no neighbouring words to weld together. This is what keeps whole-domain
        // stems like "motherless.com" matchable without putting "motherless" back in the word list.
        val whole = if (s.none { it.isWhitespace() }) listOf(compactAll(s), s.lowercase()
            .filter { it.isLetterOrDigit() }) else emptyList()
        return (out + whole).flatMap { listOf(it, squash(it)) }
    }

    fun hit(s: String?): Boolean = match(s) != null

    /**
     * The matched term, or null. Returning it rather than a boolean is what makes a false positive
     * diagnosable: the block screen and the trigger log both name the exact term that fired, so
     * "it blocked my search and I do not know why" stops being possible.
     */
    fun match(s: String?): String? {
        if (s == null || s.length < 3) return null

        val spaced = foldSpaced(s)
        if (RESCUE_RE.containsMatchIn(spaced)) return null

        val cands = candidates(s)
        for (stem in STRONG_SET) if (cands.any { it.contains(stem) }) return stem

        // Circumvention searches. Every one is a long distinctive phrase, so these alone are
        // matched with all separators closed: "remove device owner", "remove-device-owner" and
        // "removedeviceowner" are one thing, and nothing innocent is ten characters from them.
        val flat = compactAll(s)
        for (stem in BYPASS_SET) if (flat.contains(stem)) return stem

        if (ALLOW_RE.containsMatchIn(spaced)) return null

        HARD_RE.find(spaced)?.let { return it.value }

        // Ambiguous words need corroboration: a second ambiguous word, or a word that only shows
        // up around porn. One on its own is how "naked eye" and "cum laude" used to get blocked.
        val soft = SOFT_RE.findAll(spaced).map { it.value }.distinct().toList()
        if (soft.isEmpty()) return null
        if (soft.size >= 2) return soft.take(2).joinToString("+")
        CONTEXT_RE.find(spaced)?.let { return soft[0] + "+" + it.value }
        return null
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
            "chaturbate" to true,
            "porno365" to true,
            "motherless.com" to true,
            "rule 34" to true,
            "cam4" to true,
            // ambiguous words, blocked when a second signal agrees
            "naked teen pics" to true,
            "sexy webcam" to true,
            "nude photos leaked" to true,
            "horny sexy" to true,
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
            "ab" to false,
            // every one of these blocked before, found by running the matcher over a dictionary
            "watch anime" to false,                 // "hanime" spanned the gap between the words
            "bismuth crystal" to false,             // "smut"
            "accelerometer calibration" to false,   // "erome"
            "chevrolet camaro price" to false,      // "cam4" folded to "cama"
            "poorness meaning" to false,            // doubled letter collapsed into "porn"
            "inswinger cricket" to false,           // "swinger"
            "sclerotica of the eye" to false,       // "erotica"
            "porgy fish recipe" to false,           // "orgy"
            "sometimes i feel like a motherless child lyrics" to false,
            "gopalan institute llc" to false,
            "reality kingship succession" to false,
            "my mother less worried now" to false,
            "wealth clips youtube" to false,
            "dirty shipping container" to false,
            "scam soda advert" to false,
            // ambiguous words on their own are ordinary English
            "naked eye telescope" to false,
            "kink in the hose" to false,
            "cum laude meaning" to false,
            "same sex marriage law" to false,
            "anal fissure symptoms" to false,
            "horny toad lizard" to false,
            // Deliberate: one ambiguous word is no longer enough on its own. "she is so horny"
            // used to block here. The stems and the domain layer carry the actual porn, and a
            // filter that fires on a single common word is one you stop believing.
            "she is so horny" to false,
            "sexy back justin timberlake" to false,
            "escort service to the airport" to false,
            // "quit" was a substring, so the word "quite" switched the whole filter off
            "quite a good pornhub alternative" to true
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
        //
        // Only text you actually typed. This used to read e.text on every event type, and the
        // service subscribes to typeWindowContentChanged, so "what you type" quietly became "any
        // string any app redraws": video titles, search suggestions, comments, notification text,
        // song lyrics. The file header promised address bars and typed text and the code did not
        // keep that promise, which is why blocks arrived with no relation to what was on screen.
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            for (t in e.text) {
                val s = t?.toString() ?: continue
                Filter.match(s)?.let { block(s, it); return }
            }
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
                Filter.match(url)?.let { block(url, it); return }
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

    private fun block(reason: String, term: String) {
        // Keep it, do not just log it. logcat is a ring buffer a few minutes deep, so by the time
        // anyone says "it blocked me and I do not know why" the evidence is long gone. Settings,
        // Diagnostics reads this back.
        runCatching { Store.logTrigger(this, term, reason) }
        android.util.Log.i("SheldyFilter", "BLOCK on '$term' in: >>>${reason.take(300)}<<<")
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
