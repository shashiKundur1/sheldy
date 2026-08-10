package com.shashi.shield

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import kotlin.math.min

/**
 * Frame analysis.
 *
 * Be clear about what this is: a **skin-area heuristic**, not a trained classifier. It measures
 * how much of the frame falls inside a skin chrominance range in YCbCr, which is the technique
 * early filters used before CNNs existed. It is genuinely useful for the case DNS and keywords
 * cannot touch, scrolling an image feed inside Instagram or Reddit, and it is genuinely crude.
 *
 * Two things keep the false-positive rate survivable:
 *   1. A high area threshold. A face in a video call is a few percent of the screen; the content
 *      this is aimed at is not.
 *   2. Consecutive-frame confirmation. A single frame never triggers, so someone walking past the
 *      camera or a beach photo scrolling by is ignored. Sustained is the signal.
 *
 * It is deliberately reported as a heuristic in the UI rather than as "AI detection", because a
 * checklist that overstates a protection is worse than one that omits it. If a TFLite NSFW model
 * is ever dropped in, replace [score] and nothing else changes.
 */
object FrameScan {

    /** Fraction of the frame that must read as skin. Tuned high to survive faces and selfies. */
    const val AREA_THRESHOLD = 0.42f

    /** Consecutive positives required before blocking, at roughly one frame per second. */
    const val CONFIRM_FRAMES = 3

    /**
     * Returns the fraction of sampled pixels inside the skin chrominance range.
     *
     * Standard YCbCr gate: 77 <= Cb <= 127, 133 <= Cr <= 173. Sampled on a grid rather than
     * per-pixel, full sampling of a 1080p frame every second is pure battery burn for no
     * additional accuracy at this threshold.
     */
    fun score(bmp: Bitmap): Float {
        val w = bmp.width
        val h = bmp.height
        if (w == 0 || h == 0) return 0f
        val stepX = maxOf(1, w / 80)
        val stepY = maxOf(1, h / 80)

        var skin = 0
        var total = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = bmp.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val cb = 128 + (-0.168736 * r - 0.331264 * g + 0.5 * b)
                val cr = 128 + (0.5 * r - 0.418688 * g - 0.081312 * b)
                val luma = 0.299 * r + 0.587 * g + 0.114 * b

                // Ignore near-black and blown-out pixels: both sit inside the chroma gate by
                // accident and would otherwise make dark UI read as skin.
                if (luma in 40.0..250.0 && cb in 77.0..127.0 && cr in 133.0..173.0) skin++
                total++
                x += stepX
            }
            y += stepY
        }
        return if (total == 0) 0f else skin.toFloat() / total
    }

    /** ponytail: one runnable check. Synthetic frames, asserted both ways. */
    fun selfTest(): String {
        fun solid(r: Int, g: Int, b: Int): Bitmap =
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                for (y in 0 until 64) for (x in 0 until 64)
                    setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        val cases = listOf(
            "skin tone" to (solid(222, 171, 140) to true),
            "pale skin" to (solid(241, 194, 167) to true),
            "pure black UI" to (solid(0, 0, 0) to false),
            "white page" to (solid(255, 255, 255) to false),
            "blue sky" to (solid(80, 140, 220) to false),
            "green grass" to (solid(60, 150, 70) to false)
        )
        val bad = cases.filter { (_, v) ->
            val (bmp, expected) = v
            (score(bmp) >= AREA_THRESHOLD) != expected
        }
        return if (bad.isEmpty()) "Frame scan self-test: ${cases.size}/${cases.size} pass"
        else "Frame scan FAILED: " + bad.joinToString { it.first }
    }

    /**
     * Apps the scan actually looks at.
     *
     * Capturing the whole phone all day is far more than this needs: DNS and keywords already
     * cover the browser, so the only gap is image feeds inside apps whose domain is legitimate
     * and whose content is pictures. Everything else, banking, messages, maps, the camera, is
     * never analysed at all.
     *
     * The accessibility service already knows the foreground package, so gating on it is free.
     */
    val WATCHED = setOf(
        "com.instagram.android",
        "com.reddit.frontpage",
        "com.twitter.android", "com.x.android",
        "org.telegram.messenger", "org.thunderdog.challegram",
        "com.snapchat.android",
        "com.pinterest",
        "com.zhiliaoapp.musically",       // TikTok
        "com.google.android.youtube",
        "com.tumblr",
        "com.discord"
    )

    /** Set by the Watchdog on every window change. Volatile: read from the capture thread. */
    @Volatile
    var foreground: String = ""

    fun shouldScan() = foreground in WATCHED

    private const val K_ON = "screenguard_on"
    private const val K_UNTIL = "screenguard_until"
    private const val K_DAYS = "screenguard_days"

    /**
     * Screen watching runs on a timer and switches itself off.
     *
     * Capped at 21 days on purpose. That is roughly the window it takes to unpick a daily habit
     * loop, and it is also about as long as anyone should tolerate a screen-capture service
     * running. A protection with no end date stops being a decision and becomes background
     * surveillance you forgot you agreed to, so it expires, and turning it back on has to be a
     * fresh, deliberate act.
     */
    const val MAX_DAYS = 21
    const val DEFAULT_DAYS = 21

    fun days(ctx: Context) =
        ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)
            .getInt(K_DAYS, DEFAULT_DAYS).coerceIn(1, MAX_DAYS)

    fun setDays(ctx: Context, n: Int) =
        ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)
            .edit().putInt(K_DAYS, n.coerceIn(1, MAX_DAYS)).apply()

    fun expiresAt(ctx: Context) =
        ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE).getLong(K_UNTIL, 0L)

    fun daysLeft(ctx: Context): Int {
        val until = expiresAt(ctx)
        if (until <= 0L) return 0
        val ms = until - System.currentTimeMillis()
        if (ms <= 0L) return 0
        return Math.ceil(ms / 86_400_000.0).toInt()
    }

    fun expired(ctx: Context) = enabled(ctx) && expiresAt(ctx) in 1..System.currentTimeMillis()

    fun enabled(ctx: Context) =
        ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE).getBoolean(K_ON, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        val sp = ctx.getSharedPreferences("sheldy", Context.MODE_PRIVATE)
        val e = sp.edit().putBoolean(K_ON, on)
        // Stamp the end date when it is switched on; clear it when switched off.
        if (on) e.putLong(K_UNTIL, System.currentTimeMillis() + days(ctx) * 86_400_000L)
        else e.remove(K_UNTIL)
        e.apply()
    }
}

/**
 * Requests screen-capture consent, then starts the service.
 *
 * Android requires this consent dialog per session and it cannot be suppressed, not even by a
 * Device Owner, which is correct, and means one tap after each reboot.
 */
class ScreenGuardActivity : Activity() {

    private val REQ = 7788

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val mgr = getSystemService(MediaProjectionManager::class.java)
        if (mgr == null) { finish(); return }
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ)
    }

    @Deprecated("startActivityForResult is the only API that returns a projection token")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ && res == RESULT_OK && data != null) {
            FrameScan.setEnabled(this, true)
            startForegroundService(
                Intent(this, ScreenGuardService::class.java)
                    .putExtra("code", res)
                    .putExtra("data", data)
            )
        }
        finish()
    }
}

class ScreenGuardService : Service() {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var hits = 0
    private var lastCheck = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(9001, notification())

        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra("data", Intent::class.java)
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        val mgr = getSystemService(MediaProjectionManager::class.java)
        projection = mgr?.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { teardown(); stopSelf() }
        }, Handler(mainLooper))

        val metrics = resources.displayMetrics
        // Capture small. The heuristic gains nothing from resolution and this keeps the copy cheap.
        val w = min(320, metrics.widthPixels)
        val h = w * metrics.heightPixels / metrics.widthPixels

        thread = HandlerThread("scan").apply { start() }
        val handler = Handler(thread!!.looper)

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ r -> onFrame(r) }, handler)
        }
        display = projection?.createVirtualDisplay(
            "sheldy-scan", w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )
        return START_STICKY
    }

    private fun onFrame(r: ImageReader) {
        val image = r.acquireLatestImage() ?: return
        try {
            // One frame per second is plenty; the rest are dropped without being copied.
            val now = System.currentTimeMillis()
            if (now - lastCheck < 1000) return
            lastCheck = now

            // Nothing is copied out of the frame buffer unless a watched app is in front, so
            // the rest of the phone is never analysed even while the projection is held.
            if (!FrameScan.shouldScan()) { hits = 0; return }

            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val padding = rowStride - pixelStride * image.width
            val bmp = Bitmap.createBitmap(
                image.width + padding / pixelStride, image.height, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)

            val score = FrameScan.score(bmp)
            bmp.recycle()

            if (score >= FrameScan.AREA_THRESHOLD) {
                hits++
                if (hits >= FrameScan.CONFIRM_FRAMES) {
                    hits = 0
                    Handler(mainLooper).post { trigger() }
                }
            } else {
                hits = 0
            }
        } catch (_: Exception) {
            // A dropped frame is not worth crashing a always-on service over.
        } finally {
            image.close()
        }
    }

    private fun trigger() {
        Store.logBlock(this)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra("reason", "screen content")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun notification(): Notification {
        val ch = "sheldy_guard"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm?.getNotificationChannel(ch) == null) {
            nm?.createNotificationChannel(
                NotificationChannel(ch, "Screen guard", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "Runs while screen scanning is on." }
            )
        }
        return Notification.Builder(this, ch)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Sheldy screen scan")
            .setContentText("Only reads image-feed apps. Nothing leaves this phone.")
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun teardown() {
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { thread?.quitSafely() }
        projection = null
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }
}
