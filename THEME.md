# Sheldy, "Night Watch"

## The single idea

**This app is designed for 1am.**

Not for the morning, when you are motivated and browsing features. For the moment you are
tired, alone, in a dark room, arguing with yourself, thumb already moving. That is the only
moment this app has to win. Every rule below is derived from it, and any decision that cannot
be traced back to it does not go in.

---

## The rules, and why each exists

### 1. Pure black. `#000000`, not dark grey.

Dark grey is a *style*. True black is a *function*: on the OLED panel of an S23 FE, black
pixels are switched off, so the screen emits the least possible light into a dark room. A
bright interface at 1am wakes you up, and awake is when you lose.

### 2. No colour. None. Anywhere.

This is the load-bearing rule.

Saturation is the actual mechanism of the thing you are fighting. Thumbnails, feeds and
previews are engineered in colour because colour is what makes the eye stop. An app built to
break that hold cannot itself run on the same trick, a recovery app that glows violet is
using the enemy's tooling.

It also makes the greyscale switch honest. When you flip the whole phone to monochrome, Sheldy
does not change, because Sheldy already lives there. The app looks like the world it is asking
you to move into.

### 3. Hollow versus filled is the entire state language.

With colour gone, something else has to carry meaning, and it is fill.

- **Hollow**, hairline outline, transparent centre. Dormant, pending, not yet built.
- **Filled**, solid white. Live, active, urgent.

That is the whole vocabulary. A live protection is a filled dot; a roadmap item is a hollow
ring. You can read the entire state of the app from across the room without reading a word.
This is also why colour-blindness and greyscale mode change nothing here: the signal was never
in the hue.

### 4. Exactly one filled element per screen.

The panic button is solid white. Almost nothing else is. In an interface built from outlines,
a single filled shape is the loudest thing possible, and your eye lands on it without
searching.

At 1am you should not be choosing between six options. Decision fatigue is the failure mode.
One obvious action per screen, everything else subordinate.

### 5. Type is oversized, and there is little of it.

The day counter is 68sp. The headline is 34sp. Body text is sparse and short.

Tired eyes and a tired brain read one thing. If a screen needs a paragraph to explain itself,
the screen is wrong. Numerals are set in tabular figures so the counter does not jitter as
digits change width, a count that wobbles is a count you stop trusting.

### 6. Hairlines, never shadows.

A drop shadow implies a light source above the interface. Night Watch has no light source, so
depth is expressed by a 1px border at 12% white instead. It is quieter, it is sharper on
OLED, and it costs no blur.

### 7. Motion is slow and never excited.

200-300ms, ease-out, no spring, no bounce. The app should feel like something steady sitting
with you, not something pleased with itself. Celebration animation on a streak counter is a
trap, it makes the streak the point, and the streak is not the point.

### 8. It never scolds.

No red, no warnings, no shame. Partly because there is no colour, but mainly because shame is
the fuel of the cycle: feel bad, seek relief, feel worse. The block screen says "Not this."
and then counts down. It does not lecture, and it never says you failed.

### 9. Space is structural, on a 4/8dp rhythm.

`4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48`

Sections are separated by 32, cards padded by 20, related items by 8. Nothing is spaced by
eye. Most of what reads as "premium" in an interface is just consistent spacing applied
without exception, and most of what reads as amateur is one element off the grid.

### 10. Touch targets are 48dp minimum.

Non-negotiable, and easy to get wrong at 1am with one thumb in the dark.

---

## Tokens

| Role | Value | Used for |
|---|---|---|
| Ground | `#000000` | Every screen background |
| Ink | `#FFFFFF` | Primary text, filled states |
| Ink 60% | `rgba(255,255,255,.60)` | Body copy |
| Ink 30% | `rgba(255,255,255,.30)` | Eyebrows, dormant marks |
| Surface | `5% white` | Resting card |
| Surface high | `9% white` | Active card |
| Hairline | `12% white` | All borders |

Type: `11 · 12.5 · 14.5 · 16.5 · 21 · 34 · 68`
Radius: `20 tile · 24 card · 999 pill`

---

## What this theme refuses

- Gradients, glows, neon, decoration pretending to be design
- Progress celebrations, confetti, badges, gamifying the wrong thing
- Red alerts and warning states, shame is the relapse fuel
- Emoji as icons, inconsistent across platforms, uncontrollable by tokens
- More than one primary action per screen
