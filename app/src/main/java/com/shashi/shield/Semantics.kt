package com.shashi.shield

import android.content.Context
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder

/**
 * Reading a sentence for what it means, not for which words are in it.
 *
 * The lock can be opened by writing why you need the phone, and something has to decide whether
 * what you wrote is a real need. A keyword list was the first attempt and it is the wrong tool:
 * it cannot tell "there is a UPI transaction that needs doing" from "I want to transact with
 * Instagram", because the discriminating information is in the meaning and not the vocabulary.
 * It also fails the other way, anything phrased unusually scores zero and locks you out of your
 * own phone, which is the worse error of the two.
 *
 * So: MediaPipe's text embedder (Google's Universal Sentence Encoder, 6MB, bundled) turns the
 * sentence into a 100-dimensional vector, and the same is done for a set of labelled example
 * sentences. Cosine distance to the nearest example is the answer. This is ordinary nearest-
 * neighbour classification over sentence embeddings, and it generalises: "my mum needs her
 * prescription" lands near the medical examples without that phrase appearing anywhere here.
 *
 * Everything runs on the phone. That matters more here than anywhere else in the app, a sleep
 * lock exists to bite at 3am, so the thing that opens it must not need a network.
 */
object Semantics {

    private const val MODEL = "sentence_encoder.tflite"

    /**
     * Real needs, each carrying the time it deserves. These are examples to measure distance
     * from, not patterns to match, three or four per category is enough for the embedder to
     * place a new sentence sensibly, and more would just be a keyword list wearing a disguise.
     */
    private val GENUINE: List<Pair<String, Int>> = listOf(
        // money, and the reason it cannot wait
        "I need to send money to someone urgently" to 30,
        "There is a UPI transaction I have to complete now" to 30,
        "I have to pay a bill before the deadline tonight" to 30,
        "I need to transfer money from my bank account" to 30,
        // codes and sign-ins
        "I need my authenticator app to get a code and log in" to 15,
        "I have to enter a one time password to verify my account" to 15,
        "I need to sign in to something and it wants a verification code" to 15,
        // work happening now
        "I have an important meeting with a client" to 60,
        "There is a work emergency and my team is waiting for me" to 60,
        "I have an interview starting soon" to 60,
        "Something has broken at work and I am the one on call" to 60,
        // documents
        "There is a document in an app that I need to share immediately" to 30,
        "I have to send a file that someone is waiting for" to 30,
        "I need to show my ticket at the counter" to 15,
        // getting somewhere, and food
        "I need to book a cab to get home" to 30,
        "I want to order food because I have not eaten" to 30,
        "I need to check where my delivery has got to" to 15,
        // other people
        "My mother is unwell and I need to reach the doctor" to 120,
        "A family member needs help right now" to 120,
        "There has been an emergency and I need my phone" to 120,
        "I need to buy medicine from the pharmacy" to 30
    )

    /** What the lock exists to refuse. Same idea, opposite label. */
    private val HOLLOW: List<String> = listOf(
        "I am bored and want to use my phone",
        "I just want to check something quickly",
        "I want to scroll through social media for a bit",
        "I want to watch videos",
        "I want to look at Instagram and Reddit",
        "no particular reason I just want my phone",
        "I want to pass some time",
        "I feel like looking at my phone"
    )

    private var embedder: TextEmbedder? = null
    private var genuineVecs: List<Pair<Embedding, Int>> = emptyList()
    private var hollowVecs: List<Embedding> = emptyList()

    @Volatile private var ready = false
    @Volatile private var broken = false

    /**
     * Load the model and pre-embed every example.
     *
     * The examples never change, so embedding them once at startup turns each later judgement
     * into one embed plus a handful of dot products, a few milliseconds, which is what lets this
     * sit in front of a button rather than behind a spinner.
     *
     * Blocking. Call off the main thread.
     */
    @Synchronized
    fun warm(ctx: Context): Boolean {
        if (ready) return true
        if (broken) return false
        return try {
            val opts = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                .setQuantize(false)
                .build()
            val e = TextEmbedder.createFromOptions(ctx, opts)
            genuineVecs = GENUINE.map { (s, m) -> e.embed(s).embeddingResult().embeddings()[0] to m }
            hollowVecs = HOLLOW.map { e.embed(it).embeddingResult().embeddings()[0] }
            embedder = e
            ready = true
            true
        } catch (t: Throwable) {
            // A missing asset, an unsupported ABI, an OOM on a cold start, any of them mean the
            // caller falls back to the keyword scorer rather than the lock becoming unopenable.
            broken = true
            false
        }
    }

    data class Read(val genuine: Double, val minutes: Int)

    /**
     * Score a sentence, or return null if the model is not usable and the caller should fall back.
     *
     * The score is the margin between the nearest genuine example and the nearest hollow one,
     * mapped onto 0..1 so that 0.5 means "equally close to both" and the 0.7 threshold means the
     * genuine reading has to win clearly rather than narrowly. Raw similarity would not do: every
     * English sentence is somewhat similar to every other, so the absolute number is nearly
     * meaningless and only the difference carries information.
     *
     * Blocking. Call off the main thread.
     */
    fun read(ctx: Context, sentence: String): Read? {
        if (!warm(ctx)) return null
        val e = embedder ?: return null
        val s = sentence.trim()
        if (s.length < 3) return Read(0.0, 0)

        return try {
            val v = e.embed(s).embeddingResult().embeddings()[0]

            var bestG = -1.0; var minutes = 30
            for ((g, m) in genuineVecs) {
                val sim = TextEmbedder.cosineSimilarity(v, g)
                if (sim > bestG) { bestG = sim; minutes = m }
            }
            val bestH = hollowVecs.maxOfOrNull { TextEmbedder.cosineSimilarity(v, it) } ?: 0.0

            // margin in [-2,2] -> score in [0,1], centred so equal similarity reads as 0.5.
            // Scaled by 1.6 because real margins cluster in roughly [-0.3, 0.3]; without it
            // nothing would ever clear 0.7 and the whole gate would be dead.
            val score = (0.5 + (bestG - bestH) * 1.6).coerceIn(0.0, 1.0)
            Read(score, minutes)
        } catch (t: Throwable) {
            null
        }
    }

    fun status(ctx: Context): String = when {
        ready -> "On device. ${GENUINE.size} genuine and ${HOLLOW.size} hollow examples."
        broken -> "Unavailable, falling back to the word list."
        else -> "Not loaded yet."
    }

    /**
     * ponytail: one runnable check, and the only one that can prove the model is really working.
     *
     * The sentences here are deliberately NOT the examples above, every one is phrased
     * differently from anything the classifier has seen, because a nearest-neighbour test against
     * its own training sentences proves nothing at all. If these pass, it is generalising.
     *
     * Blocking. Call off the main thread.
     */
    fun selfTest(ctx: Context): String {
        if (!warm(ctx)) return "Sentence model: unavailable, word list in use"
        val cases = listOf(
            "my landlord is asking for the rent and I must transfer it tonight" to true,
            "need the six digit code from my authenticator to get into my account" to true,
            "the client call starts in ten minutes and I have to join" to true,
            "have to forward the signed agreement to them straight away" to true,
            "ordering dinner, there is nothing in the house" to true,
            "my father fell and I need to ring the hospital" to true,
            "have to book a ride to the station or I miss the train" to true,
            "feeling restless, thought I would look at my phone" to false,
            "want to see what is happening on twitter" to false,
            "nothing much, just fancy a scroll" to false,
            "bored out of my mind" to false
        )
        val results = cases.map { (s, expected) ->
            val r = read(ctx, s)
            Triple(s, expected, r)
        }
        val bad = results.filter { (_, expected, r) ->
            r == null || (r.genuine >= Lock.GENUINE_THRESHOLD) != expected
        }
        val n = cases.size
        return if (bad.isEmpty()) "Sentence model: ${n}/${n} pass on unseen phrasings"
        else "Sentence model: ${n - bad.size}/$n, missed " +
            bad.joinToString("; ") {
                "\"${it.first.take(28)}…\" ${"%.2f".format(it.third?.genuine ?: -1.0)}"
            }
    }
}
