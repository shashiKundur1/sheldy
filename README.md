# Sheldy

**A porn blocker you own. No subscription, no account, no server, no data leaving your phone.**

Every blocker on the Play Store charges eight to fifteen dollars a month to run a DNS filter and a
keyword list. Sheldy is the same thing, built once, installed from your own machine, and yours
forever. Around 6,000 lines of Kotlin, zero UI dependencies, and nothing phones home because there
is nothing to phone home to.

It is also not a nag. It counts days you actually earn, it never scolds you, and when you relapse
it asks what set it off instead of resetting a number and looking disappointed.

---

## Why this exists

I got tired of paying a subscription for a keyword list, and I got tired of blockers that could be
uninstalled in nine seconds at one in the morning. So this one is built around a simple idea: at
1am you are not the same person who installed it, and the app should be designed for the version of
you that wants to switch it off.

That single idea shapes everything. There is no unlock button. The nightly commitment cannot be
taken back. The design is pure black and white because saturation is most of what makes a feed
compulsive, and colour is the one thing no argument can win back.

---

## What it does

**Blocking**

- Encrypted DNS (DoT) filtering, frozen so it cannot be changed in Settings
- On-device keyword watchdog reading address bars and typed text, with obfuscation folding so
  `p0rn`, `p.o.r.n`, `poooorn` and `pr0n` all land on the same stem
- A 90 second block screen you cannot cancel, with breathing pacing
- Optional screen content scanning inside image feeds, on device, no uploads
- System wide greyscale that drains colour from every app on the phone

**Locking**

- Focus lock: suspends every app except calls, messages and three you choose, up to 8 hours
- Sleep lock: a nightly window you commit to for a set number of nights and cannot take back
- Three five minute emergency openings per lock, each one written down
- A reasoned unlock, judged on device: write why you need the phone, and how long you get follows
  from what you wrote

**Recovery**

- Day counter that starts at zero, because installing an app is not day one
- Calendar tracker with clean, relapsed and missed days
- Panic button, breathing pacer, relapse log with triggers
- 19 achievement badges themed on the kings of ancient Greece, 91 tiers
- Journal, daily lessons, home screen widget, accountability partner
- Optional AI companion for the moment an urge hits

---

## The part nobody else does

The reasoned unlock runs a real sentence model on the phone.

You write why you need your phone. Google's Universal Sentence Encoder, bundled at 6 MB, turns your
sentence into a vector and compares it against labelled examples of genuine needs and hollow ones.
You get a score from 0 to 1 and anything at 0.7 or above opens the phone, for a length of time that
matches what you actually need to do.

```
"landlord needs the rent transferred tonight by upi"   ->  granted, 30 minutes
"bored and fancy a scroll through instagram"           ->  0.36, refused
```

It generalises. "My mother is unwell and I need to ring the doctor" scores high without that phrase
appearing anywhere in the examples. And it runs entirely offline, which matters more here than
anywhere else in the app: a sleep lock exists to bite at 3am, so the thing that opens it must never
depend on a network.

There is a keyword fallback underneath it, and an optional Gemini pass over the top. Nothing is
allowed to lower a score another layer already gave, because every failure mode of a smarter judge
ends with somebody locked out of their own phone in the middle of the night.

---

## Setup

Two setups. Start with the first one.

### Standard, about five minutes, no reset

This is what most people want. You keep your accounts, your data and your phone exactly as it is.

**1. Build and install**

```bash
git clone https://github.com/shashiKundur1/sheldy.git
cd sheldy
cp local.properties.example local.properties     # optional, see Configuration
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No Android Studio needed. You need JDK 17 and the Android SDK. If you have Android Studio installed,
you already have both.

**2. Grant the one permission that matters**

```bash
adb shell pm grant com.shashi.shield android.permission.WRITE_SECURE_SETTINGS
```

This is what lets the app set encrypted DNS and greyscale. It is a normal developer permission and
it does not require a wipe.

**3. Turn on the watchdog**

On the phone: Settings, Accessibility, Installed apps, Sheldy, turn it on.

**4. Open Sheldy, go to Settings, tap Apply lockdown**

You now have the DNS filter, the keyword watchdog, the block screen, greyscale and every tracking
feature. The app reports `Standard` as its tier.

### Locked, optional, for when you have already beaten the easy version

Device Owner adds uninstall protection, the safe mode block, the factory reset block, the browser
sweep and the phone locks. Android only grants it on a device with no accounts signed in, so in
practice it means a fresh device or a reset. Do this only if you want it.

```bash
# with no Google or Samsung account signed in
adb shell dpm set-device-owner com.shashi.shield/.AdminReceiver
```

Then sign back in and restore your apps as normal. Run Apply lockdown again.

**Read this before you do it.** Device Owner is close to permanent by design. It also flags the
phone as organisation owned, which means Google's backup transport will refuse to sync it to a
personal account. Google Photos, WhatsApp's own backup, contacts and calendar sync are unaffected,
and Sheldy has its own export. But it is a real trade and you should make it knowingly.

---

## Configuration

Everything worth changing is in one place per topic.

| What | Where | Default |
|---|---|---|
| DNS filter host | `Lockdown.kt`, `DNS_HOST` | `adult-filter-dns.cleanbrowsing.org` |
| Blocked words and stems | `Watchdog.kt`, `STRONG` / `WEAK` | ~110 stems, ~50 boundary matched |
| Words that must never block | `Watchdog.kt`, `ALLOW` | recovery, medical, education |
| Browsers to hide | `Lockdown.kt`, `HIDE` | 16 browsers plus Knox |
| Genuine and hollow unlock examples | `Semantics.kt` | 21 genuine, 8 hollow |
| Emergency count and length | `Lock.kt` | 3 sessions, 5 minutes each |
| Longest reasoned unlock | `Lock.kt`, `REASON_CAP_MINUTES` | 120 |
| Focus lock ceiling | `Lock.kt`, `FOCUS_MAX_HOURS` | 8 |
| Check-in times | `Reminders.kt` | 09:00, 17:00, 21:00 |
| Colours, spacing, type | `Brand.kt` | black and white only |
| Badges and tiers | `Achievements.kt` | 19 badges, 91 tiers |

**Picking a different DNS.** Any DoT hostname works. Be careful with the family grade resolvers:
most of them force safe search by redirecting YouTube and Google to restricted endpoints, which
disables YouTube comments and can break sign-in flows on unrelated sites. CleanBrowsing Adult blocks
adult domains and leaves everything else alone, which is why it is the default.

**The AI companion key.** Optional. Get a free key at
[aistudio.google.com/apikey](https://aistudio.google.com/apikey), then either put it in
`local.properties` as `gemini.key=AIza...` or paste it in the app under Settings, Companion.
Without a key the chat says so plainly and every other feature works.

**Changing the package name.** If you want your own build alongside mine, change `applicationId` in
`app/build.gradle.kts` and the `namespace`. Note that the adb grant and the Device Owner command
both reference the package name, so update those too.

---

## Verifying it works

```bash
adb shell settings get global private_dns_specifier      # your DNS host
adb shell ping -c1 pornhub.com                           # should fail to resolve
adb shell ping -c1 www.google.com                        # should resolve fine
```

Inside the app, Settings, Diagnostics runs three self tests on the device: the keyword filter
against evasion and false positive cases, the lock window maths, and the sentence model against
phrasings it has never seen. All three should read PASS.

---

## Honest limits

A blocker that overstates itself is worse than no blocker, so:

- **Holding power and volume down** force restarts the phone. Nothing running on Android can stop
  that. Sheldy dismisses the power menu during a lock, which is friction, not prevention.
- **A VPN routes around Private DNS.** VPN use is allowed on purpose, and when one is connected the
  DNS layer goes dark. The keyword watchdog reads the screen rather than the socket, so it holds
  through any tunnel and any exit country, but the domain layer does not.
- **Device Owner disables Google's backup transport.** Google refuses to sync organisation owned
  devices to a personal account and no app can override that. Sheldy ships JSON export and import.
- **The screen scan is a heuristic**, not a trained classifier. It needs three consecutive positive
  frames before acting, runs only inside a short list of image feed apps, and never uploads a frame.
- **Nothing here is a substitute for actual help.** If this is affecting your life, talk to someone.

---

## Built with

Kotlin, no UI framework. Every view is constructed in code. No AppCompat, no Compose, no Material
components, no image loading library. The single third party dependency is MediaPipe Tasks Text,
which is what runs the sentence model on the phone.

Requires Android 11 or newer. Built and tested against Android 16, One UI 8, on a Galaxy S23 FE.

---

## Contributing

Fork it and make it yours. That is the point of publishing it.

If you send a pull request, keep the house style: no new dependencies without a good reason, views
built in code, comments explain why rather than what, and any non trivial logic leaves one runnable
self test behind. The three existing self tests are the model.

---

## Licence

MIT. Do what you like with it.

Built by [Shashidhar Kundur](https://github.com/shashiKundur1).
