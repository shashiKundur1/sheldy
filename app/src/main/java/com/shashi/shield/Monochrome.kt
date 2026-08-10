package com.shashi.shield

import android.content.Context
import android.provider.Settings

/**
 * System-wide greyscale. Drains the colour out of the entire phone, not just this app,
 * home screen, Instagram, YouTube, everything.
 *
 * This is the same knob Developer options exposes as "Simulate color space > Monochromacy",
 * driven through the accessibility daltonizer. It needs WRITE_SECURE_SETTINGS, which we already
 * hold because adb granted it during setup.
 *
 * Worth knowing why it earns its place: colour saturation is a large part of what makes feeds
 * and thumbnails compulsive. Greyscale does not block anything, it just makes the whole device
 * boring, and boring is the one defence a determined user cannot argue their way past.
 */
object Monochrome {

    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"
    private const val MONOCHROMACY = 0

    fun isOn(ctx: Context): Boolean =
        Settings.Secure.getInt(ctx.contentResolver, ENABLED, 0) == 1 &&
            Settings.Secure.getInt(ctx.contentResolver, MODE, -1) == MONOCHROMACY

    /** Returns the state actually achieved, read back rather than assumed. */
    fun set(ctx: Context, on: Boolean): Boolean {
        runCatching {
            if (on) {
                // Mode before the switch, so it never flicks on in the wrong colour space.
                Settings.Secure.putInt(ctx.contentResolver, MODE, MONOCHROMACY)
                Settings.Secure.putInt(ctx.contentResolver, ENABLED, 1)
            } else {
                Settings.Secure.putInt(ctx.contentResolver, ENABLED, 0)
            }
        }
        return isOn(ctx)
    }

    fun toggle(ctx: Context): Boolean = set(ctx, !isOn(ctx))
}
