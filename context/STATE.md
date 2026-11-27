# State

**Last updated:** 2026-11-27 22:10 +0600  

**Where it runs.** The Mac (`ABUs-MacBook-Pro-2`) now builds and runs the app: JDK 21 from
Homebrew, `local.properties` with `sdk.dir` (git-ignored), platform 37 and build tools 37
installed. `./gradlew build --rerun-tasks` is green there (KNOWN, 2026-11-27): 142 tests in `:core`, 52 in `:app`, 12 in the content gate; the adb suite passes 9 of 10 T-INST ids (6 not run) and `tests/inst/ui.sh` passes. The `streambert`
emulator boots (20 s) and runs the debug APK. This answers the emulator half of G13; a physical
phone is still needed for the reliability claim. Claude Code's sandbox blocks writes to the SDK
folder and adb/emulator sockets, so those commands run with the sandbox off.

## Where the project stands

- **Everything that can be built without a phone is built and green on the JVM:** phases 1
  and 2, and the pure parts of phases 5 and 6. `./gradlew build` passes: 94 tests in `:core`, 12 in `:tools:content-gate`, 3 upstream tests in `:engine:calctimes`. Phase 1 still lacks the timetable check (Q2).
- Three decisions are recorded: engine vendored with one change (ADR-2 amended), plain Kotlin
  `:core` module (ADR-9), application id `com.senseix21.muminsday`.
- What exists in code:
  - `engine/calctimes/`: upstream engine at `a136f9b`, two visibility modifiers changed,
    recorded in `MODIFICATIONS.md`.
  - `core/.../clock/`: `PrayerClock` seam, `CalcTimesClock` (Karachi, Hanafi default, safe-side
    rounding, explicit `NotComputable`, correct local-day attribution in any zone).
  - `core/.../planner/`: `Planner.plan(now, settings)` gives every alarm for the next 48 hours
    with stable ids in a closed space of 64, one replan after local midnight, and gaps for
    days that cannot be computed.
  - `core/.../planner/Delivery.kt`: the late rule, `decideDelivery` (10 minutes).
  - `core/.../health/`: `assessHealth(now, facts)` gives `Armed` or `Degraded` with named reasons.
  - `core/.../l10n/`: `toBengaliDigits`.
  - `core/.../bearing/`: `qiblaDirection`, `magneticBearing`, `compassPoint`.
  - `core/.../deed/`: the review gate (`checkContent`), rotation (`deedFor`, `alternateFor`)
    and the day card (`cardFor`, `swap`, `markDone`, `weekDots`). Test records are fake; no
    deed content exists.
  - `app/`: the Android module. Manifest with the seven allowlisted permissions (ADR-12: location and vibration removed), backup on for `settings.xml` and `deed-days.db` only (G41), gates
    T-BUILD-1…3. `store/` (settings, SQLite log and deed tables, migrations), `alarm/`
    (`apply(plan)`, receivers, delivery, `mediaPlayback` service with Stop, notifications, health,
    Test Adhan), a debug-only adb receiver, and `tests/inst/run.sh` (the T-INST suite over adb).
    Still no real screens: the home screen is a placeholder.
  - `tools/content-gate/`: the T-BUILD-4 build task. Passes with a notice until
    `content/deeds.json` exists.
- The placeholder app has run on an emulator, nothing more. Nothing has run on a phone.
  Nothing is checked against a real timetable.
- Committed: `764dc7a` (phase 1), `da2fd48` (planner, digits, plan), `62ea747` (late rule,
  health), `586630d` (Qibla maths), `0684bc1` (deed core). Pushed to
  `github.com/senseix21/mumins-day` (`main`). The repository is public. CI builds and tests every push on JDK 17. Upstream clones for reading are in
  `dev/upstream/`, outside this repository.

## Next

All six slices are built as far as they can be without the founder's inputs and without phones. Release APK 1,055,071 bytes, R8 on (ADR-15), desugaring gone (ADR-14), LGPL texts shipped in assets (ADR-13), 4 libraries, 7 permissions. Seen on an emulator only: alarms, store, screens, deed card (fake records), Qibla. Measured: APK 1,034,164 bytes, 142 tests in `:core`, 52 in `:app`, 12 in the content gate.

What blocks the rest, in order of leverage:
1. **Decisions** (no work for anyone else): G38, G41, G42, G44, G45, G35, G33, default language (INV-L1), test-only dependencies, G26 and G30.
2. **Content and data:** reviewer and `content/deeds.json` (G9); district list (Q4); Adhan recording (Q5); native Bengali speaker for 152 draft keys (G9); support email, developer name, privacy URL (G15).
3. **Phones** (Q8, G13): M-1 to M-4, T-INST-6 (API 31 or 32), the accuracy half of T-INST-12, audible Adhan, brand steps, backup and restore.
4. **A commit and a push** so CI runs the app (G46).
5. Reference timetable for the prayer times (Q2). Lawyer on LGPL (Q6).

## Waiting on the founder

| What | Why it matters | Id |
|---|---|---|
| Reference timetable | The only real test of correctness, T-FIX-4 | Q2 |
| Reviewer and native speaker, by name | Sehri/Fajr rulings below; all deed content | G9, Q3 |
| Confirm: Sehri end ignores the user's Fajr offset; Sehri end and Fajr can show one minute apart; sunrise rounds down | Rulings taken in slice 1 on the safe side | G19, G20 |
| A phone (or several) | The app can be built here, but not run or tested | G13 |
| Adhan recording, district list | Slices 3 and 4 | Q5, Q4 |
