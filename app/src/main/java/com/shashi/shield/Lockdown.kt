package com.shashi.shield

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserManager
import android.provider.Settings

class AdminReceiver : DeviceAdminReceiver()

object Lockdown {

    /**
     * Encrypted DNS (DoT). This is the actual porn filter for anything that resolves a hostname.
     * Alternatives with the same one-line swap:
     *   family.cloudflare-dns.com
     *   family-filter-dns.cleanbrowsing.org
     */
    /**
     * Encrypted DNS (DoT), the site filter for anything that resolves a hostname.
     *
     * CleanBrowsing *Adult*, deliberately not *Family*, and not AdGuard Family.
     *
     * The family-grade resolvers do three things at once: block porn, block ads and trackers, and
     * force safe search by CNAME-ing google/bing/youtube to their restricted endpoints. Only the
     * first is wanted, and the other two broke real life:
     *   - YouTube Restricted Mode disables comments outright and hides most search results. That
     *     was not a keyword filter misfiring, it was the DNS.
     *   - Blocking LinkedIn's fingerprinting hosts means LinkedIn cannot recognise the device, so
     *     every single login is an unknown-device challenge from an unknown location.
     * CleanBrowsing Adult blocks adult domains and nothing else, no tracker blocking, no YouTube
     * restriction, no proxy/VPN blocking. Porn stays dead, the rest of the phone works.
     */
    private const val DNS_HOST = "adult-filter-dns.cleanbrowsing.org"

    fun dnsHost(ctx: Context): String = DNS_HOST

    private val RESTRICTIONS = listOf(
        UserManager.DISALLOW_SAFE_BOOT,                        // safe mode boot is refused
        UserManager.DISALLOW_FACTORY_RESET,                    // Settings reset blocked (recovery still works)
        UserManager.DISALLOW_ADD_USER,                         // no second profile to browse from
        // DISALLOW_CONFIG_PRIVATE_DNS is applied separately, after the DNS value lands.
        // DISALLOW_CONFIG_VPN is deliberately NOT here. A VPN does route around Private DNS, so
        // the network layer of the filter goes dark whenever NordVPN is connected. That is an
        // accepted trade: the on-device keyword filter in Watchdog reads the screen, not the
        // socket, so it does not care what country the exit node is in. See CLEARED below.
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, // no sideloading another browser
        UserManager.DISALLOW_CONFIG_DATE_TIME                  // no clock-fiddling to beat time rules later
    )

    /**
     * Restrictions this build actively takes off. Dropping one from RESTRICTIONS is not enough,
     * it was already written to the device and stays there forever until something clears it.
     */
    private val CLEARED = listOf(
        UserManager.DISALLOW_CONFIG_VPN
    )

    /**
     * Everything that can browse, except Chrome. Chrome is the one browser we control.
     * Secure Folder is in here too: Device Owner does NOT remove it on One UI 8, and it is a
     * Knox container the accessibility service cannot see into. Hiding it before it is ever set
     * up is the fix; DISALLOW_ADD_USER alone is not guaranteed to stop a Knox profile.
     */
    private val HIDE = listOf(
        "com.samsung.knox.securefolder",
        "com.samsung.android.knox.containercore",
        "com.sec.android.app.sbrowser",   // Samsung Internet
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.microsoft.emmx",             // Edge
        "com.duckduckgo.mobile.android",
        "com.UCMobile.intl",
        "com.kiwibrowser.browser",
        "com.yandex.browser",
        "mark.via.gp",
        "org.torproject.torbrowser"
    )

    /** The one browser we keep, because it is the one the Watchdog reads the address bar of. */
    private const val ALLOWED_BROWSER = "com.android.chrome"

    /**
     * Hide every installed app that can open a web link, except the allowed one. This is what
     * makes a hardcoded list unnecessary: install Firefox from Play tomorrow and it is hidden
     * within a minute, without anyone editing HIDE.
     *
     * Called on a timer from the Watchdog, which is the only thing on this device guaranteed to
     * still be running. A manifest BroadcastReceiver for PACKAGE_ADDED is not reliable under
     * Android 8+ implicit-broadcast limits, and the sweep needs no new moving parts.
     */
    fun sweepBrowsers(ctx: Context): Int {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return 0

        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val webHandlers = runCatching {
            ctx.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())

        var hidden = 0
        for (p in (webHandlers + HIDE).toSet()) {
            if (p == ALLOWED_BROWSER || p == ctx.packageName) continue
            runCatching { if (dpm.setApplicationHidden(admin, p, true)) hidden++ }
        }
        return hidden
    }

    /**
     * Scheduled blackout: hide the one remaining browser during the nightly window.
     *
     * Blocking a search is reactive, you still had to want it, type it, and be stopped. Removing
     * the browser outright during the hours your own relapse log points at means there is nothing
     * to type into. Reversed automatically when the window closes.
     */
    fun setBrowserBlackout(ctx: Context, on: Boolean): Boolean {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            dpm.setApplicationHidden(admin, ALLOWED_BROWSER, on)
        }.getOrDefault(false)
    }

    fun blackoutActive(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        return runCatching {
            dpm.isDeviceOwnerApp(ctx.packageName) &&
                dpm.isApplicationHidden(admin, ALLOWED_BROWSER)
        }.getOrDefault(false)
    }

    /**
     * Android disables the backup service the moment a Device Owner is provisioned, that is
     * what "prevented by a security policy" means on the backup screen. It is an enterprise
     * default (a managed device is not assumed to want its data in a personal cloud), and only
     * the Device Owner can lift it.
     *
     * Nothing about blocking porn requires blocking backup, so this is exposed as a plain toggle.
     */
    fun backupEnabled(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        return runCatching {
            dpm.isDeviceOwnerApp(ctx.packageName) && dpm.isBackupServiceEnabled(admin)
        }.getOrDefault(false)
    }

    fun setBackupEnabled(ctx: Context, on: Boolean): Boolean {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            dpm.setBackupServiceEnabled(admin, on)
            dpm.isBackupServiceEnabled(admin)
        }.getOrDefault(false)
    }

    /** True when the app holds Device Owner, which is optional. See [tier]. */
    fun isOwner(ctx: Context) = runCatching {
        ctx.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(ctx.packageName)
    }.getOrDefault(false)

    /** True when adb has granted WRITE_SECURE_SETTINGS, which is what the DNS filter needs. */
    fun canWriteSettings(ctx: Context) = ctx.checkSelfPermission(
        android.Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Which of the two setups is in place.
     *
     * Standard needs no wiped phone and takes about five minutes: install, turn on accessibility,
     * grant one permission over adb. That buys the DNS filter, the keyword watchdog, the block
     * screen, greyscale and every tracking feature, which is the whole app for most people.
     *
     * Locked adds Device Owner, and Device Owner can only be granted on a device with no accounts
     * signed in, so it realistically means a factory reset. It buys uninstall protection, the safe
     * mode and factory reset blocks, the browser sweep, and the phone locks. Worth it if you have
     * already tried the easy version and beaten it. Not a starting point.
     */
    fun tier(ctx: Context): String = when {
        isOwner(ctx) -> "Locked"
        canWriteSettings(ctx) -> "Standard"
        else -> "Incomplete"
    }

    fun status(ctx: Context): String = when {
        isOwner(ctx) -> "Device Owner: YES"
        canWriteSettings(ctx) -> "Standard setup. Filter and watchdog active, uninstall not blocked."
        else -> "Not set up. Run the adb grant in README.md."
    }

    /**
     * Apply whatever this device allows.
     *
     * Device Owner is optional. Without it the DNS filter still goes on, because writing the
     * private DNS setting needs WRITE_SECURE_SETTINGS rather than device ownership, and adb can
     * grant that on any phone with no reset and no accounts removed. Everything that genuinely
     * requires Device Owner is skipped with a line saying so, rather than the whole thing
     * refusing to run and leaving someone with no filter at all.
     */
    fun apply(ctx: Context): String {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        val owner = isOwner(ctx)
        if (!owner && !canWriteSettings(ctx)) {
            return "Nothing applied. Grant WRITE_SECURE_SETTINGS over adb first, see README.md."
        }

        val log = StringBuilder()

        // ORDER MATTERS. DISALLOW_CONFIG_PRIVATE_DNS blocks writes to the private DNS setting
        // including the policy engine's own, so setting it first makes the DNS call fail with
        // error 1 and no DNS filtering at all. Drop the restriction, set DNS, then re-lock.
        // The restriction that freezes private DNS also blocks writing it, including the policy
        // engine's own write, which just returns error 1. So: unlock, set, verify by reading the
        // value back, and only relock at the very end of apply().
        val host0 = DNS_HOST
        fun dnsNow() = Pair(
            Settings.Global.getString(ctx.contentResolver, "private_dns_mode"),
            Settings.Global.getString(ctx.contentResolver, "private_dns_specifier")
        )

        if (owner) runCatching {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        }
        var (mode, host) = dnsNow()
        for (attempt in 1..4) {
            if (mode == "hostname" && host == host0) break
            Thread.sleep(1200)
            // dpm.setGlobalPrivateDnsModeSpecifiedHost returns error 1 and writes nothing on
            // One UI 8, so the direct write is the real path and the DPM call is the fallback.
            // Specifier before mode, so the host is present the moment the mode flips.
            runCatching {
                Settings.Global.putString(ctx.contentResolver, "private_dns_specifier", host0)
                Settings.Global.putString(ctx.contentResolver, "private_dns_mode", "hostname")
            }.onFailure { runCatching { dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, host0) } }
            val fresh = dnsNow(); mode = fresh.first; host = fresh.second
        }

        if (!owner) {
            val ok = mode == "hostname" && host == host0
            log.append("private DNS: ${if (ok) "OK" else "NOT SET"} host=$host\n")
            log.append("standard setup: filter and watchdog only, uninstall is not blocked\n")
            Reminders.apply(ctx)
            log.append("check-ins: 09:00, 17:00, 21:00\n")
            return log.toString().trim()
        }

        for (r in CLEARED) runCatching { dpm.clearUserRestriction(admin, r) }

        var restrictions = 0
        for (r in RESTRICTIONS) {
            runCatching { dpm.addUserRestriction(admin, r) }
                .onSuccess { restrictions++ }
                .onFailure { log.append("skip $r: ${it.message}\n") }
        }
        log.append("restrictions applied: $restrictions/${RESTRICTIONS.size}\n")

        runCatching { dpm.setUninstallBlocked(admin, ctx.packageName, true) }
            .onSuccess { log.append("uninstall blocked: yes\n") }
            .onFailure { log.append("uninstall blocked: FAILED ${it.message}\n") }

        // Freeze private DNS last, once the value is confirmed in place.
        Thread.sleep(500)
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS) }
        val ok = mode == "hostname" && host == host0
        log.append("private DNS: ${if (ok) "OK" else "NOT SET"} mode=$mode host=$host, frozen\n")

        log.append("browsers+containers hidden: ${sweepBrowsers(ctx)}\n")

        // Device Owner can grant its own runtime permissions, so the check-in reminders never
        // depend on the user remembering to allow notifications.
        runCatching {
            dpm.setPermissionGrantState(
                admin, ctx.packageName, android.Manifest.permission.POST_NOTIFICATIONS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        }.onSuccess { log.append("notifications granted: yes\n") }
        Reminders.apply(ctx)
        log.append("check-ins: 09:00 · 17:00 · 21:00\n")

        return log.toString().trim()
    }

    /**
     * The escape hatch. Keep this reachable until the setup is proven stable, then move the
     * trigger behind a code held by someone else.
     */
    fun release(ctx: Context): String {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return "Not Device Owner."

        for (r in RESTRICTIONS + CLEARED) runCatching { dpm.clearUserRestriction(admin, r) }
        for (p in HIDE) runCatching { dpm.setApplicationHidden(admin, p, false) }
        runCatching { dpm.setGlobalPrivateDnsModeOpportunistic(admin) }
        runCatching { dpm.setUninstallBlocked(admin, ctx.packageName, false) }
        runCatching { dpm.clearDeviceOwnerApp(ctx.packageName) }
        return "Released. App is uninstallable again."
    }
}
