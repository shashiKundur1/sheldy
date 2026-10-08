# Device Owner setup, and how the internals work

**You do not need this file to use Sheldy.** The README covers the standard setup, which takes
about five minutes and needs no reset. This file is for the optional Device Owner tier, plus notes
on how each layer actually works.

Device Owner adds uninstall protection, the safe mode block, the factory reset block, the browser
sweep and the phone locks. Android only grants it on a device with no accounts signed in, which in
practice means a fresh device or a reset. It is close to permanent by design. Read the whole file
before starting.

## 0. Before you reset

- [ ] Export 2FA seeds (Authy / Microsoft Authenticator). **This is the one that locks people out.**
- [ ] Photos , Google Photos
- [ ] WhatsApp , Settings , Chats , Backup to Drive
- [ ] Everything else , Samsung Smart Switch to a PC, or accept re-downloading

## 1. Build the APK

Needs JDK 17 and the Android SDK (platform 35, build-tools 35.0.0, platform-tools). The Gradle
wrapper is pinned to 8.11.1, so no Android Studio is required.

```bash
./gradlew assembleDebug
```

Output:

```
app/build/outputs/apk/debug/app-debug.apk        2.3 MB
```

Rebuild after any edit:

```sh
cd ~/Documents/projects/shield
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

Put `adb` on your PATH once:

```sh
echo 'export PATH="$PATH:/opt/homebrew/share/android-commandlinetools/platform-tools"' >> ~/.zshrc
source ~/.zshrc
adb version
```

**Copy `app-debug.apk` somewhere off this project folder before you wipe the phone.**

## 2. Wipe and set Device Owner

1. Settings , General management , Reset , Factory data reset
2. In the setup wizard, **skip every account**. No Google, no Samsung. Get to a bare home screen.
   `dpm set-device-owner` refuses to run if any account exists.
3. Settings , About phone , Software information , tap **Build number** 7×
4. Settings , Developer options , **USB debugging** on
5. Plug into the Mac and run:

```sh
adb install app-debug.apk
adb shell dpm set-device-owner com.shashi.shield/.AdminReceiver
```

Expect: `Success: Device owner set to package com.shashi.shield`

If it says *"Not allowed to set the device owner because there are already some accounts"*, an account slipped in. Settings , Accounts, remove all, retry.

## 3. Turn on the watchdog

Android 13+ blocks sideloaded apps from the Accessibility screen ("Restricted setting").
ADB skips that check entirely:

```sh
adb shell settings put secure enabled_accessibility_services com.shashi.shield/com.shashi.shield.Watchdog
adb shell settings put secure accessibility_enabled 1
```

## 4. Apply the lockdown

Open Shield , **Apply lockdown**. Expected output:

```
restrictions applied: 6/6
uninstall blocked: yes
private DNS -> adult-filter-dns.cleanbrowsing.org (code 0)
browsers hidden: 1
```

Now sign back into Google and Samsung and restore your apps.

## 5. Verify (do all six)

| Check | Expected |
|---|---|
| Long-press Shield , App info | Uninstall greyed out |
| Power + Vol Down , hold "Power off" , Safe mode | Refused |
| Settings , Private DNS | Locked, shows the CleanBrowsing host |
| Settings , VPN | Opens normally; NordVPN can connect |
| VPN on, foreign exit node, type `porn` | Still blocked, the watchdog reads the screen |
| Chrome , type `porn` in the address bar | Block screen before the 4th letter |
| Chrome , open `pornhub.com` directly | DNS failure |
| Settings , Accessibility , Shield | Bounced to home screen |

## 6. Samsung-specific: test Secure Folder

Biggest hole on this device. Settings , Security and privacy , look for **Secure Folder**.

- **Gone / greyed out** , good, Device Owner killed it.
- **Still there** , tell me, we handle it in Phase 1.5.

## Escape hatch

`adb shell am broadcast -n com.shashi.shield/.ReleaseReceiver`. It clears every restriction,
unhides and unsuspends every app, resets Private DNS, hands Device Owner back, and prints what it
did. Add `--ez keep_owner true` to do all of that but stay Device Owner, as a dry run.

`adb shell dpm remove-active-admin` is **not** an exit. Android refuses it unless the build was
`android:testOnly` when it became admin, and this one is not.

Give the adb command to the person holding you accountable. A lock you can open in 15 seconds at
1am is a suggestion, not a lock.

## Not covered by Phase 1

- Porn *inside* Instagram / Reddit / X / Telegram. Domains are legitimate, images aren't text.
  That is Phase 2 (on-device NSFW screen classifier).
- Recovery-mode factory reset. Unblockable by any app on Android.
- Pressing Home to leave the block screen early. The countdown still interrupted the impulse.

---

## What Sheldy is now

Five tabs, monochrome throughout, Inter, hard-edged geometry. Design rationale is in `THEME.md`
("Night Watch", the app is designed for 1am, and every rule derives from that).

**Today**, day counter starting at 0, thought of the day, PANIC, companion chat, month calendar,
quick note.
**Shield**, greyscale switch, live protection checklist.
**Notes**, journal, timestamped, local only.
**Progress**, contribution-graph calendar of every day, headline stats, feature checklist.
**Settings**, grouped rows: check-ins, the day, companion, protection, diagnostics.

### The counting model

You start on **day 0**. A day is only earned by finishing it and confirming it, the 21:00
check-in is what turns today into a counted day. `confirmClean()` refuses any day that has not
finished, so nothing can be pre-marked. Relapse logging asks for a trigger and never scolds.

### Reminders

09:00, 17:00 and 21:00 daily, via `AlarmManager` inexact repeating (survives Doze, needs no
special permission), re-armed on boot. Device Owner grants `POST_NOTIFICATIONS` to itself, so
notifications never depend on you accepting a prompt. Toggle is in Settings.

Android has had no real lock screen widgets since Android 4, so a lock-screen notification with
`VISIBILITY_PUBLIC` is the closest equivalent, that is what these are.

### The companion

Gemini `gemini-2.5-flash`, reachable from the Today tab and from the block screen. It is given
your streak, the hour, and your recent notes, and is instructed to reply in two or three
sentences, never moralise, and never use slogans.

No key ships with the app. Add one in `local.properties` as `gemini.key=AIza...`, or paste it in
Settings, Companion on the phone. A key starting `AIza` never expires. A value starting `AQ.` is a
short lived OAuth token and stops working within the hour. Without a key the chat says so plainly
and every other feature works normally.

## There is no in-app unlock

The "Release everything" control has been removed deliberately. `Lockdown.release()` still exists
in source for a genuine emergency, but nothing in the interface reaches it.

The remaining exits, both intentionally slow:

```sh
adb shell am broadcast -n com.shashi.shield/.ReleaseReceiver
```

or a recovery-mode wipe (hardware keys), which no Android app can block.

If you want this to hold properly, give that adb command to someone else and take Auto Blocker's
USB switch back on, with USB commands blocked, even adb stops being a way out.

## Rebuilding

```sh
cd ~/Documents/projects/shield
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Brand assets and the Gemini generation script live in `brand/`.

---

## The collection

18 badges themed on the kings of ancient Greece, 86 tiers between them
(BRONZE , IRON , SILVER , GOLD , DIAMOND , OLYMPIAN, four to six per badge).

The theme is load-bearing, not decoration. Odysseus lashed to the mast, hearing the Sirens in
full and not steering toward them, is a better description of riding out an urge than any
wellness slogan. Priam crossing the lines unarmed to ask for his son back is what logging a
relapse honestly costs.

Weighted deliberately away from the streak: only two badges count raw days. The rest reward
showing up (NESTOR), writing it down (CADMUS), reading (MINOS), not needing the panic button
(AJAX), being honest about relapses (PRIAM) and, the one that actually predicts outcome, restarting after a relapse (SISYPHUS). A collection that only counts days makes a relapse feel
like losing everything, which is the exact thought that turns one slip into a week of them.

Artwork: 18 struck-silver medallions generated with Gemini 3 Pro Image, exposure-normalised in
Photoshop, then framed programmatically on the detected coin. Share renders a composed card via
MediaStore, which also drops it in your gallery.

## Filter coverage

Rebuilt around **normalisation rather than a longer word list**. Ranking brand tokens across
64,220 hosts in the public porn blocklist showed porno365 / rexporn / pornxp / porngo are all one
stem wearing a hat, so input is folded first, case, leetspeak (`p0rn`, `pr0n`, `$`, `@`),
separators (`p.o.r.n`, `p o r n`) and stretched letters (`poooorn`), and short stems are matched
against the fold.

Three lists: STRONG (substring, ~110 stems and brands), WEAK (word-boundary, because `jav` is
inside `java` and `anal` inside `analysis`), and BYPASS.

**BYPASS covers self-defence only.** Nobody defeats a filter by searching for porn, they search
"how to remove device owner" first, and every keyword list waves that through because not one word
is explicit. Blocked: undoing Device Owner, uninstalling this app, disabling the accessibility
service.

It used to also cover VPNs, proxies, safe mode, root and sideloading. Those came out, because they
were substring-matched with no word boundary and "NordVPN download" folded onto "vpndownload".
VPN use is supported now, so blocking searches about it was blocking a supported feature.

Self-test: 41/41, including every evasion case and the false-positive guards
("sex education", "java tutorial", "pronounce", "analysis", "device driver update").

## Screen scanning

The only layer that can see inside Instagram or Reddit, where the domain is legitimate and there
is no text to read. Off by default; enable in Settings.

Honest about what it is: a **skin-area heuristic in YCbCr, not a trained classifier**. Two things
keep false positives survivable, a high area threshold (42%), and three consecutive positive
frames before it acts, so a face or a beach photo scrolling past is ignored. Every frame is
analysed on-device; nothing is uploaded. If a TFLite NSFW model is ever dropped in, replace
`FrameScan.score()` and nothing else changes.
