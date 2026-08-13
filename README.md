<div align="center">

<img src="brand/banner.png" alt="Sheldy" width="820">

<br>

**🔥 A porn blocker you own, not rent.**

No account. No server. No subscription. Nothing leaves your phone.

<br>

[![Download](https://img.shields.io/badge/⬇%20Download%20APK-latest-white?style=for-the-badge&labelColor=000000)](https://github.com/shashiKundur1/sheldy/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-white?style=for-the-badge&labelColor=000000)](LICENSE)
[![Android](https://img.shields.io/badge/Android-11%2B-white?style=for-the-badge&labelColor=000000&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-white?style=for-the-badge&labelColor=000000&logo=kotlin&logoColor=white)](#)

<br>

<img src="brand/sheldy-ad.gif" alt="Sheldy" width="760">

</div>

---

## 🔥 Why this exists

Every blocker on the Play Store charges eight to fifteen dollars a month to run a DNS filter and a
keyword list. That is roughly a hundred and fifty dollars a year to rent something you could own.

Worse, most of them come off in nine seconds at one in the morning.

Sheldy is built around one idea: **at 1am you are not the same person who installed it.** The app is
designed for the version of you that wants to switch it off. There is no unlock button. The nightly
commitment cannot be taken back. The whole interface is pure black and white, because saturation is
most of what makes a feed compulsive, and colour is the one thing no argument wins back.

About 6,000 lines of Kotlin. One dependency. Nothing phones home, because there is nothing to phone
home to.

---

## ⬇️ Get it

**[Download the latest APK →](https://github.com/shashiKundur1/sheldy/releases/latest)**

Install it, then run one adb command and turn on one toggle. Full steps in
[Setup](#-setup). Takes about five minutes and does **not** need a factory reset.

Prefer to build it yourself? [Skip to building](#build-it-yourself).

---

## ✨ What it does

### 🛡️ Blocking

| | |
|---|---|
| **Encrypted DNS** | DoT filtering, frozen so Settings cannot change it |
| **Keyword watchdog** | Reads address bars and typed text, on device |
| **Obfuscation folding** | `p0rn`, `p.o.r.n`, `poooorn`, `pr0n` all land on the same stem |
| **Two-signal rule** | Ambiguous words need corroboration, so `naked eye` gets through |
| **Trigger log** | Every block records the term it matched, readable in Diagnostics |
| **Block screen** | 90 seconds, uncancellable, with breathing pacing |
| **Screen scanning** | Optional heuristic inside image feeds, never uploaded |
| **Greyscale** | Drains colour from every app on the phone |

### 🔒 Locking

| | |
|---|---|
| **Focus lock** | Suspends every app except calls, messages and three you pick |
| **Sleep lock** | A nightly window you commit to and cannot take back |
| **Emergency** | Three five-minute openings per lock, each one written down |
| **Reasoned unlock** | Write why you need your phone. A model decides how long you get |

### 📈 Recovery

| | |
|---|---|
| **Day counter** | Days since the last relapse. Logging one puts it back to zero |
| **Calendar** | Clean, relapsed and missed days, all editable |
| **Panic button** | One tap, 90 seconds, breathing pacer |
| **19 badges** | Themed on the kings of ancient Greece, 91 tiers |
| **Journal** | Notes, triggers, daily lessons, home screen widget |
| **Partner** | Someone else gets told, on your tap |
| **AI companion** | Optional, for the moment an urge hits |

---

## 🧠 The part nobody else does

The reasoned unlock runs a real sentence model **on the phone**.

You write why you need it. Google's Universal Sentence Encoder, bundled at 6 MB, turns your
sentence into a vector and compares it against labelled examples of genuine needs and hollow ones.
You get a score from 0 to 1. Anything at **0.7 or above** opens the phone, for a length of time that
matches what you actually have to do.

```
"landlord needs the rent transferred tonight by upi"   →  granted, 30 minutes
"need the code from my authenticator to sign in"       →  granted, 15 minutes
"bored and fancy a scroll through instagram"           →  0.36, refused
```

It generalises. *"My mother is unwell and I need to ring the doctor"* scores high without that
phrase appearing anywhere in the examples.

And it is fully offline, which matters more here than anywhere else in the app: **a sleep lock
exists to bite at 3am**, so the thing that opens it must never depend on a network. There is a
keyword fallback underneath and an optional Gemini pass on top. Nothing may lower a score another
layer already gave, because every failure mode of a smarter judge ends with somebody locked out of
their own phone in the middle of the night.

---

## 🚀 Setup

Two tiers. **Start with the first one.** It needs no reset and keeps your phone exactly as it is.

### Standard, about five minutes

**1. Get the APK**

Download it from [Releases](https://github.com/shashiKundur1/sheldy/releases/latest), or build it
yourself (below).

```bash
adb install -r sheldy.apk
```

**2. Grant the one permission that matters**

```bash
adb shell pm grant com.shashi.shield android.permission.WRITE_SECURE_SETTINGS
```

This is what lets the app set encrypted DNS and greyscale. Ordinary developer permission, no wipe.

**3. Turn on the watchdog**

On the phone: **Settings → Accessibility → Installed apps → Sheldy → On**

**4. Open Sheldy → Settings → Apply lockdown**

Done. You now have the DNS filter, keyword watchdog, block screen, greyscale and every tracking
feature. The app will report its tier as `Standard`.

### Locked, optional

Device Owner adds uninstall protection, the safe mode block, the factory reset block, the browser
sweep and the phone locks. Android only grants it on a device with no accounts signed in, so in
practice it means a fresh device or a reset. **Do this only if you have already beaten the easy
version.**

```bash
# with no Google or Samsung account signed in
adb shell dpm set-device-owner com.shashi.shield/.AdminReceiver
```

> ⚠️ Device Owner is close to permanent by design. It also flags the phone as organisation owned,
> so Google's backup transport refuses to sync it to a personal account. Google Photos, WhatsApp's
> own backup, contacts and calendar sync are unaffected, and Sheldy ships its own export. Real
> trade, make it knowingly.

### Build it yourself

```bash
git clone https://github.com/shashiKundur1/sheldy.git
cd sheldy
cp local.properties.example local.properties    # optional, see Configuration
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17 and the Android SDK. No Android Studio required.

---

## ⚙️ Configuration

Everything worth changing sits in one obvious place.

| What | Where | Default |
|---|---|---|
| DNS filter host | `Lockdown.kt` → `DNS_HOST` | `adult-filter-dns.cleanbrowsing.org` |
| Blocked stems | `Watchdog.kt` → `STRONG` | ~100 substring stems |
| Blocked words | `Watchdog.kt` → `HARD` | whole-word only |
| Needs a second signal | `Watchdog.kt` → `SOFT` / `CONTEXT` | ambiguous words |
| Never-block lists | `Watchdog.kt` → `RESCUE` / `ALLOW` | recovery, medical, education |
| Browsers to hide | `Lockdown.kt` → `HIDE` | 16 browsers plus Knox |
| Unlock examples | `Semantics.kt` | 21 genuine, 8 hollow |
| Emergency count and length | `Lock.kt` | 3 sessions, 5 minutes |
| Longest reasoned unlock | `Lock.kt` → `REASON_CAP_MINUTES` | 120 |
| Focus lock ceiling | `Lock.kt` → `FOCUS_MAX_HOURS` | 8 |
| Check-in times | `Reminders.kt` | 09:00, 17:00, 21:00 |
| Colours, spacing, type | `Brand.kt` | black and white only |
| Badges and tiers | `Achievements.kt` | 19 badges, 91 tiers |

**Picking a different DNS.** Any DoT hostname works. Be careful with family-grade resolvers: most
force safe search by redirecting YouTube and Google to restricted endpoints, which kills YouTube
comments and breaks sign-in flows on unrelated sites. CleanBrowsing Adult blocks adult domains and
leaves everything else alone, which is why it is the default.

**The AI companion key.** Optional. Grab a free one at
[aistudio.google.com/apikey](https://aistudio.google.com/apikey), then either put it in
`local.properties` as `gemini.key=AIza...` or paste it in the app under **Settings → Companion**.
No key ships with the app, and every other feature works without one.

**Your own build alongside mine.** Change `applicationId` and `namespace` in
`app/build.gradle.kts`. Remember the adb grant and the Device Owner command both name the package.

---

## ✅ Verifying it works

```bash
adb shell settings get global private_dns_specifier    # your DNS host
adb shell ping -c1 pornhub.com                         # should fail to resolve
adb shell ping -c1 www.google.com                      # should resolve fine
```

In the app, **Settings → Diagnostics** runs four self tests on the device: the keyword filter
against evasion and false-positive cases, the lock window maths, the day counter across relapse
and missed-day records, and the sentence model against phrasings it has never seen.

Underneath them, **Recent filter triggers** lists the last thirty blocks with the exact term that
matched. If something gets blocked that should not have, that screen names the word responsible.

---

## ⚠️ Honest limits

A blocker that overstates itself is worse than no blocker, so:

- **Holding power and volume down force-restarts the phone.** Nothing on Android can stop that.
  Sheldy dismisses the power menu during a lock. That is friction, not prevention.
- **A VPN routes around Private DNS.** VPN use is allowed on purpose, and while one is connected
  the domain layer goes dark. The keyword watchdog reads the screen rather than the socket, so it
  holds through any tunnel and any exit country.
- **Device Owner disables Google's backup transport.** No app can override that. Sheldy ships JSON
  export and import instead.
- **The screen scan is a heuristic**, not a trained classifier. Three consecutive positive frames
  before it acts, only inside a short list of image-feed apps, never uploaded.
- **None of this replaces actual help.** If this is affecting your life, talk to someone.

---

## 🧱 Built with

Kotlin, and no UI framework at all. Every view is constructed in code: no AppCompat, no Compose, no
Material components, no image loading library. The single third-party dependency is **MediaPipe
Tasks Text**, which runs the sentence model on the phone.

Android 11+. Built and tested against Android 16 / One UI 8.

---

## 🤝 Contributing

Fork it and make it yours. That is the point of publishing it.

If you send a pull request, keep the house style: no new dependencies without a good reason, views
built in code, comments explain **why** rather than what, and any non-trivial logic leaves one
runnable self test behind. The three existing self tests are the model.

---

<div align="center">

**MIT licensed. Do what you like with it.**

Built by [Shashidhar Kundur](https://github.com/shashiKundur1)

<img src="brand/logo-square.png" alt="" width="52">

</div>
