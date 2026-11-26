# State

**Last updated:** 2026-11-26 18:24 +0600

## Where the project stands

- **Slice 1 is built and green on the JVM.** `./gradlew build` passes: 13 tests in `:core`,
  3 upstream tests in `:engine:calctimes`.
- Three decisions are recorded: engine vendored with one change (ADR-2 amended), plain Kotlin
  `:core` module (ADR-9), application id `com.senseix21.muminsday`.
- What exists in code:
  - `engine/calctimes/`: upstream engine at `a136f9b`, two visibility modifiers changed,
    recorded in `MODIFICATIONS.md`.
  - `core/.../clock/`: `PrayerClock` seam, `CalcTimesClock` (Karachi, Hanafi default, safe-side
    rounding, explicit `NotComputable`, correct local-day attribution in any zone).
- **No Android module.** Nothing has run on a phone. Nothing is checked against a real
  timetable.
- Nothing is committed and there is no remote. Upstream clones for reading are in
  `dev/upstream/`, outside this repository.

## Next

Slice 2 (`spec/05-slices.md`): the alarm planner and Bengali digits, both in `:core`.
Before it starts, settle G8 (24 or 48-hour window) and G16 (past prayers, near-simultaneous
alarms).

## Waiting on the founder

| What | Why it matters | Id |
|---|---|---|
| A remote to push to | The work lives in a temporary container | G11 |
| Reference timetable | The only real test of correctness, T-FIX-4 | Q2 |
| Reviewer and native speaker, by name | Sehri/Fajr rulings below; all deed content | G9, Q3 |
| Confirm: Sehri end ignores the user's Fajr offset; Sehri end and Fajr can show one minute apart; sunrise rounds down | Rulings taken in slice 1 on the safe side | G19, G20 |
| Android SDK machine and phones | Slice 3 cannot be built or tested here | G13 |
| Adhan recording, district list | Slices 3 and 4 | Q5, Q4 |
