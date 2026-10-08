package com.shashi.shield

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * The layer the word filter cannot reach: films whose titles are perfectly ordinary words.
 *
 * "Shame", "Crash", "Titanic", "Oppenheimer" — a keyword list either misses all of them or, if you
 * add them, blocks car crashes and shipwrecks and the physicist. Neither is acceptable, so the
 * title is not judged by its spelling at all. It is looked up in DoesTheDogDie, a crowd-sourced
 * content database, and blocked only when that community actually reports sexual content or nude
 * scenes in that specific film.
 *
 * Two topic ids carry it, confirmed against the live API:
 *   197  "there is sexual content"
 *   279  "there are nude scenes"
 *
 * Everything about this is built around not being a nuisance:
 *
 *  - Answers are cached to disk forever. A film's content does not change, so each title costs one
 *    lookup ever, which keeps a 5,000/month free tier effectively infinite.
 *  - A miss is cached too. Otherwise every ordinary search would re-query on every keystroke.
 *  - The title must match what the database returned EXACTLY once normalised. Their search is
 *    fuzzy, and without this "the office" would come back as some film and block a work search.
 *  - A single stray community vote is not enough: MIN_YES of them, and more yes than no.
 *  - It fails open. A dead network or an expired key must never block the phone.
 */
object Movies {

    private const val SEXUAL_CONTENT = 197
    private const val NUDE_SCENES = 279
    /**
     * One vote is enough, as long as nobody has voted against it.
     *
     * This was 3, to stop a stray vote blocklisting a film. That was the wrong trade: Intimacy
     * (2001) is explicit and carries exactly 1 yes / 0 no on both topics, because arthouse films
     * collect few votes precisely because few people watch them, which is the opposite of the
     * coverage wanted here. A lone yes with zero dissent is still the community agreeing.
     */
    private const val MIN_YES = 1

    private const val PREFS = "sheldy"
    private const val K_KEY = "ddd_key"
    private const val K_CACHE = "movie_cache"

    /** Titles currently being looked up, so a redraw does not fire the same request ten times. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun key(ctx: Context): String {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_KEY, null)
        return if (!saved.isNullOrBlank()) saved else BuildConfig.DDD_KEY
    }

    fun setKey(ctx: Context, k: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(K_KEY, k.trim()).apply()

    fun configured(ctx: Context) = key(ctx).isNotBlank()

    /**
     * Lower case, no punctuation, no year, no streaming chrome. The cache key and match key.
     *
     * British spellings are folded to American because the database is American and the match is
     * exact: "Blue is the warmest colour" found nothing at all, while "...Color" is 21 yes / 0 no.
     * A whole film slipping through on one vowel is not an acceptable way to lose.
     */
    fun norm(s: String): String = s.lowercase()
        .replace(Regex("""\(\d{4}\)"""), " ")
        .replace(Regex("""\b(watch|online|full movie|movie|film|hd|1080p|720p|free|streaming|trailer|cast|review)\b"""), " ")
        .replace(Regex("""[^a-z0-9 ]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .replace("colour", "color")
        .replace("honour", "honor")
        .replace("favourite", "favorite")
        .replace("theatre", "theater")
        .replace("grey", "gray")

    /**
     * The forms a title may be matched in. A trailing year is dropped as a SECOND option rather
     * than always, because "Blade Runner 2049" is a title whose last word really is a year.
     */
    fun variants(n: String): List<String> {
        val noYear = n.replace(Regex("""\s+(19|20)\d{2}$"""), "").trim()
        return if (noYear != n && noYear.length >= 3) listOf(n, noYear) else listOf(n)
    }

    /**
     * Pull the thing that might be a film title out of an address bar or a typed string.
     * Returns null when there is clearly nothing to look up, which is the common case.
     */
    fun candidate(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null

        // a search URL: the query is the title
        val q = Regex("""[?&](?:q|search_query)=([^&]+)""").find(s)?.groupValues?.get(1)
        val text = if (q != null) {
            runCatching { java.net.URLDecoder.decode(q, "UTF-8") }.getOrDefault(q.replace('+', ' '))
        } else if (s.contains("://") || s.contains(".")) {
            // Any host, provided the PATH looks like a film page.
            //
            // A whitelist of known movie sites was the first attempt and it missed the obvious
            // case: a direct link to a pirate streamer, cinejoy.to/movie/11845-intimacy-2001,
            // which is precisely the route worth blocking and precisely the kind of domain no
            // whitelist will ever have in it. The path shape is the signal, not the domain.
            val seg = MOVIE_PATH.find(s)?.groupValues?.get(1) ?: return null
            seg.replace(Regex("""^\d+[-_]"""), "")      // "11845-intimacy-2001" -> "intimacy-2001"
                .replace(Regex("""[-_.]+"""), " ")
        } else s

        val n = norm(text)
        // Long strings are sentences, not titles. Very short ones are noise.
        if (n.length !in 3..48) return null
        if (n.split(" ").size > 7) return null
        // Must contain a real word. Bare ids like IMDb's "tt0120338" are not worth a lookup.
        if (!Regex("""[a-z]{3}""").containsMatchIn(n)) return null
        return n
    }

    /** A path segment naming a film, on any host. */
    private val MOVIE_PATH = Regex(
        """/(?:title|movie|movies|film|films|watch|series)/([^/?#]+)""", RegexOption.IGNORE_CASE
    )

    private fun cache(ctx: Context): JSONObject {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_CACHE, "{}")
        return runCatching { JSONObject(raw.orEmpty()) }.getOrDefault(JSONObject())
    }

    private fun remember(ctx: Context, title: String, reason: String?) {
        val c = cache(ctx)
        c.put(title, reason ?: "")
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(K_CACHE, c.toString()).apply()
    }

    /**
     * Ask whether this text names a film with sexual content.
     *
     * Returns a reason immediately when the answer is already cached, which is the path that runs
     * almost every time. Otherwise returns null now and calls [onBlocked] later, off the main
     * thread, if the lookup says so. Never blocks the caller on the network.
     */
    fun check(ctx: Context, raw: String?, onBlocked: (String) -> Unit): String? {
        val title = candidate(raw) ?: return null
        val c = cache(ctx)
        if (c.has(title)) {
            val reason = c.optString(title, "")
            return if (reason.isNotBlank()) reason else null
        }
        if (!configured(ctx)) return null
        if (!inFlight.add(title)) return null

        Thread {
            try {
                val reason = lookup(title, key(ctx))
                remember(ctx, title, reason)
                if (reason != null) onBlocked(reason)
            } catch (t: Throwable) {
                // fail open, and do not cache a network failure as "clean"
            } finally {
                inFlight.remove(title)
            }
        }.start()
        return null
    }

    private fun get(path: String, key: String): JSONObject {
        val c = (URL("https://www.doesthedogdie.com$path").openConnection() as HttpURLConnection)
        c.setRequestProperty("X-API-KEY", key)
        c.setRequestProperty("Accept", "application/json")
        // their edge returns 403 to the default Java agent
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36")
        c.connectTimeout = 8000
        c.readTimeout = 12000
        return c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }

    /** The reason string to show, or null when this title is fine. Blocking; background only. */
    fun lookup(title: String, key: String): String? {
        val forms = variants(title)
        // Search on the barest form. "intimacy 2001" returns nothing; "intimacy" returns the film.
        val found = get("/dddsearch?q=" + URLEncoder.encode(forms.last(), "UTF-8"), key)
        val items = found.optJSONArray("items") ?: return null

        var id = -1
        for (i in 0 until minOf(items.length(), 6)) {
            val it = items.optJSONObject(i) ?: continue
            if (norm(it.optString("name")) in forms) { id = it.optInt("id", -1); break }
        }
        if (id < 0) return null   // fuzzy match only, so not confidently this film

        val stats = get("/media/$id", key).optJSONArray("topicItemStats") ?: return null
        for (i in 0 until stats.length()) {
            val s = stats.optJSONObject(i) ?: continue
            val topic = s.optJSONObject("topic") ?: continue
            val tid = topic.optInt("id", -1)
            if (tid != SEXUAL_CONTENT && tid != NUDE_SCENES) continue
            val yes = s.optInt("yesSum", 0)
            val no = s.optInt("noSum", 0)
            if (yes >= MIN_YES && yes > no) return topic.optString("name", "sexual content")
        }
        return null
    }
}
