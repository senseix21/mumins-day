# State

**Last updated:** 2026-11-26 23:10 +0600

## Where the project stands

- **Everything that can be built without a phone is built and green on the JVM:** phases 1
  and 2, and the pure parts of phases 5 and 6. `./gradlew build` passes: 94 tests in `:core`, 3 upstream tests in `:engine:calctimes`. Phase 1 still lacks the timetable check (Q2).
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
- **No Android module.** Nothing has run on a phone. Nothing is checked against a real
  timetable.
- Committed: `764dc7a` (phase 1), `da2fd48` (planner, digits, plan), `62ea747` (late rule,
  health), `586630d` (Qibla maths), `0684bc1` (deed core). Pushed to
  `github.com/senseix21/mumins-day` (`main`). The repository is public. CI builds and tests every push on JDK 17. Upstream clones for reading are in
  `dev/upstream/`, outside this repository.

## Next

Almost nothing is left that this container can do alone:

1. The T-BUILD-4 build task (G24), which needs no phone but has no real content to check yet.

Everything else in `PLAN.md` needs a phone and Android SDK (phases 3, 4, 7), or a person:
the reviewer, the native speaker, the timetable, the district list.

## Waiting on the founder

| What | Why it matters | Id |
|---|---|---|
| Reference timetable | The only real test of correctness, T-FIX-4 | Q2 |
| Reviewer and native speaker, by name | Sehri/Fajr rulings below; all deed content | G9, Q3 |
| Confirm: Sehri end ignores the user's Fajr offset; Sehri end and Fajr can show one minute apart; sunrise rounds down | Rulings taken in slice 1 on the safe side | G19, G20 |
| Android SDK machine and phones | Slice 3 cannot be built or tested here | G13 |
| Adhan recording, district list | Slices 3 and 4 | Q5, Q4 |
