# Handoff

Rewritten 2026-11-28. Read this page first, then `CLAUDE.md`, then `context/STATE.md`.

## In one paragraph

Mu'mins DAY is an offline, private Android app for Muslims in Bangladesh: Adhan, prayer times
for all 64 districts calibrated to the Islamic Foundation, an honest Qibla compass, and one
reviewed good deed a day. Every phase is coded as far as it can go without phones and without
people. What is left needs a phone, the reviewer, a native Bengali speaker, an Adhan recording
or a Play account.

## Where everything is

| What | Where |
|---|---|
| Repository | https://github.com/senseix21/mumins-day, `main` |
| CI | `.github/workflows/build.yml`: `./gradlew build` on JDK 17, every push and PR |
| Rules, standard, task protocol | `CLAUDE.md` |
| Plan with checklists | `context/PLAN.md` |
| State now / history | `context/STATE.md` / `context/LOG.md` |
| Open questions / spec holes | `context/QUESTIONS.md` / `context/GAPS.md` |
| Spec | `context/spec/00…06`; decisions are ADR-1…16 in `03-architecture.md` |
| District data and its sources | `content/districts.json`, `content/districts-SOURCES.md` |

## What exists

| Module | What it is |
|---|---|
| `engine/calctimes` | Upstream's LGPL engine, vendored, one recorded change |
| `core` | Pure Kotlin: `clock` (times, `Calibration`), `planner` (48-hour plan, late rule, nudge), `health`, `deed`, `bearing`, `l10n`, `districts` |
| `tools/content-gate` | Build-only gates for `content/deeds.json` and `content/districts.json` |
| `app` | Platform Views UI, alarm path, store, sensors, phone-maker steps; 8 permissions, no `INTERNET`; release 184,738 bytes |

`./gradlew build`: 269 tests and five gates, green on 2026-11-28.

## Decisions that shape everything (do not reopen without the founder)

- New app that ports parts of upstream (ADR-1). Engine vendored with one change (ADR-2).
- 48-hour alarm window; passed prayers skipped (ADR-3). Platform Views, no Compose (ADR-11).
- No vibrate (ADR-12); location only for "find my district", coarse, one read (ADR-19). R8 on for release (ADR-15).
- **Times = each district's own astronomy + the Islamic Foundation's safety margins (ADR-16).**

## Claude's rulings that a person must confirm

| Ruling | Who | Id |
|---|---|---|
| Foundation margins: Fajr +2, Dhuhr +3, Maghrib +3, Isha +2, Sehri end 6 min before Fajr | Reviewer | ADR-16, G48, G49 |
| Districts follow their own sun, not the Foundation's flat offsets (up to ~10 min apart in the north and far south) | Reviewer, founder | G50 |
| Ramadan mode is switched by the user; no Hijri date | Founder | G33 |
| Daily nudge on the exact-alarm path, off by default | Founder | G35 |
| Sehri end ignores the user's Fajr offset; sunrise rounds down | Reviewer | G19, G20 |
| A retired deed may stay in the file | Reviewer | G23 |

## Running it on the Mac

The Mac (`ABUs-MacBook-Pro-2`, `~/dev/mumins-day`) builds and runs the emulator suite with
JDK 21 from Homebrew, `local.properties` pointing at `~/Library/Android/sdk`, and Claude Code's
sandbox off for that folder (it blocks `adb` and the emulator). Pull `main` first, then:

```bash
./gradlew build
```

```bash
tests/inst/run.sh
```

## What to do next

1. Run the emulator suite against the current `main`: Ramadan mode, the nudge, the brand screen
   and the calibration have only run on the JVM.
2. Get the founder's and reviewer's answers in the table above, and on G51 (exact-alarm
   permission) and G52 (playback service type) before any Play submission.
3. Content: 30 reviewed deeds, an Adhan recording (candidates in `spec/06` section E), native
   review of 169 string keys and 64 district names.
4. Phase 7 on real phones; phase 8 via `tools/release.sh`.
