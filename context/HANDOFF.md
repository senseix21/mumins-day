# Handoff

Written 2026-11-26 for whoever picks this up next: a person, or Claude Code running on the
founder's Mac. Read this page first, then `CLAUDE.md`, then `context/STATE.md`.

## In one paragraph

Mu'mins DAY is an offline, private Android app for Muslims in Bangladesh: a reliable Adhan,
Bangladesh-correct prayer times, an honest Qibla compass, and one reviewed good deed a day.
Everything that can be built and proven without a phone is done and green in CI. The Android
module exists and builds an APK, but it does nothing yet and has never been installed. The
next job is phase 3: storage, then the alarm path, tested on an emulator.

## Where everything is

| What | Where |
|---|---|
| Repository | https://github.com/senseix21/mumins-day, branch `main` |
| CI | GitHub Actions, `.github/workflows/build.yml`: `./gradlew build` on JDK 17, every push and PR. Last run green (`cd502e7`) |
| Working rules, coding standard, task protocol | `CLAUDE.md` |
| The plan, with checklists | `context/PLAN.md` |
| Where things stand now | `context/STATE.md` |
| What happened and what was verified | `context/LOG.md` |
| Open questions for the founder, reviewer, lawyer | `context/QUESTIONS.md` |
| Holes found in the spec, and how each was settled | `context/GAPS.md` |
| The original design documents, kept current | `context/spec/00…04` |
| Upstream app and engine, for reading only | Not in the repo. Clone `metinkale38/prayer-times-android` at `0b2a8e7` and `metinkale38/open-prayer-times` at `a136f9b` beside it if needed |

**The protocol that keeps this working:** at the end of every task, tick the verified items in
`PLAN.md`, rewrite `STATE.md`, append to `LOG.md`, update gaps and questions, and commit the
context with the code. Timestamps come from `TZ=Asia/Dhaka date '+%Y-%m-%d %H:%M %z'`.
`/checkpoint` does this. Never tick an item that was not run and seen passing.

## What exists

Thirteen commits, all on `main`. `./gradlew build` runs 109 tests and four build gates.

| Module | What it is | State |
|---|---|---|
| `engine/calctimes` | Upstream's LGPL prayer-time engine, vendored with one change (two `internal` made public), recorded in `MODIFICATIONS.md` | 3 upstream tests pass |
| `core` | Plain Kotlin, no Android. `clock` (times), `planner` (48-hour alarm plan, late rule), `health` (Armed or Degraded), `deed` (review gate, rotation, day card), `bearing` (Qibla), `l10n` (Bengali digits) | 94 tests, property-based where it matters |
| `tools/content-gate` | Build-only. Fails the build if `content/deeds.json` holds anything unreviewed (T-BUILD-4) | 12 tests. No deeds file exists yet, so it passes with a notice |
| `app` | The Android app. Manifest with exactly nine permissions, no `INTERNET`. Placeholder screen and icon. Backup off | Builds debug and release APKs; lint clean; permission and dependency gates (T-BUILD-1…3) pass and were seen failing on purpose |

Results by test id are in `context/spec/04-verification.md`.

## Decisions already made (do not reopen without the founder)

- New app that ports parts of upstream, not a pruned fork (ADR-1).
- Engine vendored with one change; any further edit needs the founder and a line in
  `MODIFICATIONS.md` (ADR-2).
- Pure core as its own module (ADR-9). Content gate parses with Gradle's own JSON reader (ADR-10).
- Alarm window is 48 hours; passed prayers are skipped; alarms only strictly after now (ADR-3).
- One uncomputable prayer drops that day's alarms (G21).
- "Show me another" gives the deed half a cycle away; the day's card is fixed once shown;
  30 deeds minimum (G4, G5, Q9).
- Application id `com.senseix21.muminsday`. Cannot change after release.

## Rulings Claude made that a person must confirm

- **G19:** Sehri end follows the calculated Fajr and ignores the user's Fajr offset. Sehri end
  and Fajr can show one minute apart. Reviewer.
- **G20:** Sunrise rounds down. Reviewer.
- **G23:** a retired deed may stay in the file; a draft fails the build. Reviewer.
- **`NOTICE`:** copyright line "Copyright 2026 Mehedi Hasan". Founder.
- **Gradle checksum:** the wrapper pins `971fcd71…914c76b`, computed from the download, not
  compared with Gradle's published value. Check once at gradle.org/release-checksums.
- **`SCHEDULE_EXACT_ALARM maxSdkVersion=32`:** believed to be the recommended pairing with
  `USE_EXACT_ALARM`; not confirmed on a fetched page.

## Running it on the Mac

State of `ABUs-MacBook-Pro-2` as found on 2026-11-26:

- Clone at `~/dev/mumins-day`, at `cd502e7` (the latest when this was written; `git pull` first).
- Android SDK at `~/Library/Android/sdk`: build tools 35 and 36, **platform 36 only**, emulator
  37.2, one AVD `streambert` (Pixel 8, API 36, arm64).
- **No Java on the default path.** Homebrew has `openjdk@21` and `openjdk@25`.
- No `local.properties` in the clone.

To build and run, in `~/dev/mumins-day`:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

```bash
~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.0" "build-tools;37.0.0"
```

```bash
./gradlew build
```

```bash
~/Library/Android/sdk/emulator/emulator -avd streambert &
```

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Why the cloud session could not do this:** Claude Code's sandbox on the Mac blocked `adb`
from opening its local port ("could not install smartsocket listener: Operation not
permitted") and the emulator exited with "Incompatible processor… requires neon", which the
chip does support. Run Claude Code on the Mac with the sandbox off for this folder, or start
the emulator and `adb` yourself.

`local.properties` is in `.gitignore`; it must never be committed.

## What to do next

Phase 3 in `PLAN.md`, in this order. Everything can be written and compiled in the cloud; it
can only be proven on the emulator or a phone.

1. **Install and open the placeholder APK** on `streambert`. First time the app has ever run.
2. **Store.** Settings in one SharedPreferences file, parsed once into typed values. SQLite
   through the platform helper: `alarm_log(id, prayer, kind, planned_utc, delivered_utc, outcome)`
   and `deed_day(local_date, deed_id, content_version, swapped, done_at)`. Numbered migrations
   with a test. Log trimmed to 30 days.
3. **Alarm path.** `apply(plan)`: cancel all 64 `AlarmId`s, register the plan; Adhan with
   `setAlarmClock`, the rest with `setExactAndAllowWhileIdle`. Receivers for boot, time set,
   zone change, package replaced, exact-alarm permission changed. **The boot receiver only
   replans; it never starts playback** (Android 15 forbids a `mediaPlayback` service from
   `BOOT_COMPLETED`). Delivery calls `decideDelivery`; playback is a `mediaPlayback`
   foreground service with its own Stop control (G6). Log every alarm. "Test Adhan" goes
   through the same path. A test tone until the real recording is licensed (Q5).
4. **Health checks** gather facts and call `assessHealth`.
5. **Instrumented tests T-INST-1…10** on the emulator, driven through `adb shell dumpsys alarm`.

Open before or during phase 3: G6 (how the user stops the Adhan with notifications blocked;
fetch Android's "handle-user-stopping" page), the Android 12 exact-alarm grant flow (ADR-4),
and on-device whether `setAlarmClock` and `setExactAndAllowWhileIdle` both qualify for the
background-start exemption (G7).

**Decide before phase 4 (G25):** Compose and Material 3 bring dozens of transitive libraries
and AndroidX adds a permission of its own. Either an ADR grows both allowlists, or the UI is
built with platform Views.

## What only the founder can unblock

| Need | Blocks | Id |
|---|---|---|
| Reference timetable, a year, eight cities | Proof that the times are right (T-FIX-4) | Q2 |
| Reviewer, named | Every deed; rulings G19, G20, G23; Q3 | G9 |
| Native Bengali speaker | All copy; phase 4 | G9 |
| District list with a licence | District picker; phase 4 | Q4 |
| Adhan recording and tone, licensed in writing | Release | Q5 |
| Physical phones (Xiaomi, Samsung, Realme, Infinix, a 2 GB phone) | Phase 7, the reliability claim | Q8, G13 |
| UI direction | Phase 4 | G12 |
| Privacy policy and support email | Release | G15 |
| Lawyer on LGPL in a Play app | Any paid tier | Q6 |

## Environments

- **Cloud:** environment `mumins-day` (personal) allows `dl.google.com` and has a setup script
  that installs the Android SDK at `/opt/android-sdk` with `ANDROID_HOME` set. The setup
  script has not yet been seen working in a fresh session; check the first one.
- **Commits** so far carry the container's default author ("Claude"), not the founder. Set
  `git config user.name` and `user.email` on any new machine.

## Rules that are easy to break by accident

- No `INTERNET`, ever, in v1. No new permission or library without an ADR; the build enforces both.
- Claude writes no religious content: no deed text, hadith, grading or ruling, not even as
  a placeholder. Test fixtures say `TEST DEED n`.
- No Bengali copy written by Claude for shipping; a native speaker writes it.
- Pure code takes `now` and the zone as arguments; it never reads the clock or the default locale.
- Never tick a box in `PLAN.md` on intent or on a subagent's word.
