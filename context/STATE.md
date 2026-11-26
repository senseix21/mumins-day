# State

**Last updated:** 2026-11-26 18:47 +0600

## Where the project stands

- **Phases 1 and 2, and the maths of phase 6, are built and green on the JVM.**
  `./gradlew build` passes: 59 tests in `:core`, 3 upstream tests in `:engine:calctimes`. Phase 1 still lacks the timetable check (Q2).
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
- **No Android module.** Nothing has run on a phone. Nothing is checked against a real
  timetable.
- Committed: `764dc7a` (phase 1), `da2fd48` (planner, digits, plan), `62ea747` (late rule,
  health). The Qibla maths is not committed yet. No remote. Upstream clones for reading are in
  `dev/upstream/`, outside this repository.

## Next

`PLAN.md` is the plan. What can be done here, without a phone, in order:

1. Pure part of phase 5: deed record, rotation and review gate, once G4 and G5 are settled.
2. Phase 0 leftovers: `LICENSE`, `NOTICE`, Gradle wrapper bump.

Phase 3 (the Android shell) cannot start in this container: no Android SDK, and Google's
Maven host is blocked.

## Waiting on the founder

| What | Why it matters | Id |
|---|---|---|
| A remote to push to | The work lives in a temporary container | G11 |
| Reference timetable | The only real test of correctness, T-FIX-4 | Q2 |
| Reviewer and native speaker, by name | Sehri/Fajr rulings below; all deed content | G9, Q3 |
| Confirm: Sehri end ignores the user's Fajr offset; Sehri end and Fajr can show one minute apart; sunrise rounds down | Rulings taken in slice 1 on the safe side | G19, G20 |
| Android SDK machine and phones | Slice 3 cannot be built or tested here | G13 |
| Adhan recording, district list | Slices 3 and 4 | Q5, Q4 |
