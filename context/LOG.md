# Log

Append-only, newest last. One entry per task. Get the timestamp from
`TZ=Asia/Dhaka date '+%Y-%m-%d %H:%M %z'`.

Entry shape:

```
## YYYY-MM-DD HH:MM +0600 · short title
**Did.** What changed, with paths.
**Verified.** What was run or read, and the result. "Nothing" is an allowed answer.
**Not done / follow-up.** What was left, with question or gap ids.
```

---

## 2026-11-26 15:48 +0600 · Repository bootstrap

**Did.**
- Created `dev/mumins-day`, ran `git init` (branch `main`).
- Copied the five design documents to `context/spec/00…04` unchanged.
- Wrote `CLAUDE.md`, `.claude/settings.json`, `.claude/commands/checkpoint.md`, `.gitignore`.
- Wrote `context/STATE.md`, `LOG.md`, `QUESTIONS.md`, `GAPS.md`, `README.md`.

**Verified.**
- Fetched `calctimes/.../DoubleTimes.kt`, `PrayerTimes.kt` and the declarations of
  `CalcTimes.kt` from `metinkale38/open-prayer-times` at commit `a136f9b`
  (raw.githubusercontent.com). Result: the only public output is `PrayerTimes` with rounded
  `LocalTime` fields; `DoubleTimes` and `getDoubleTimes` are `internal`. This is gap G1.
- Counted question ids referenced in the spec with `grep`: Q1, Q2, Q3, Q4, Q5, Q6, Q8. Q7 is
  never referenced and no file defines any of them. This is gap G2.

**Not done / follow-up.**
- No commit made and no remote set; waiting for the founder's word.
- No code. See `STATE.md` for what is next.

## 2026-11-26 18:09 +0600 · Upstream repositories cloned for reference

**Did.** Cloned `metinkale38/prayer-times-android` and `metinkale38/open-prayer-times` into
`dev/upstream/`, beside this repository, not inside it. Nothing from them was copied in.

**Verified.** `git log -1` in each: app at `0b2a8e70` (2026-11-17), engine at `a136f9b`
(2026-11-08). The app's `open-prayer-times.commit` file names the same engine commit. These
are the commits `spec/00-base-audit.md` was written against.

**Not done / follow-up.** Both checkouts are detached at those commits. They are local to this
container. G3 (how this repository takes the engine) is still open.

## 2026-11-26 18:16 +0600 · Application id chosen; build reachability checked

**Did.** Recorded the founder's application id `com.senseix21.muminsday` in `CLAUDE.md`,
`STATE.md` and `GAPS.md` (G10). Opened G18.

**Verified.** In this container: Gradle 8.14.3 is installed. `curl` returned 200 for
`services.gradle.org`, `repo.maven.apache.org` and `plugins.gradle.org`, and no connection
for `dl.google.com`. No Android SDK is present. No build was run.

**Not done / follow-up.** Slice 1 not started. Waiting on G1 (engine) and G18 (module split).

## 2026-11-26 18:24 +0600 · Slice 1: engine vendored, clock wrapper, property tests

**Did.**
- Founder decided: fork the engine (G1) and split out a plain Kotlin core (G18). Recorded as
  the ADR-2 amendment and ADR-9; wrote `spec/05-slices.md`; opened G19 and G20.
- A Sonnet subagent, working from a written design, built: Gradle scaffold (wrapper 8.14.3,
  Kotlin 2.4.10, version catalog), `engine/calctimes/` (vendored, `MODIFICATIONS.md`),
  `core/.../clock/` (`Units.kt`, `Model.kt`, `CalcTimesClock.kt`) and its tests.
- Claude reviewed the result and added one test: order and local-date attribution in six
  non-Dhaka zones for every day of 2026 and 2027.
- Updated `CLAUDE.md` (layout, commands, status) and the Result column of `spec/04`.

**Verified.**
- `diff -r engine/calctimes/src upstream/.../calctimes/src`: exactly two lines differ, the two
  `internal` modifiers.
- `./gradlew build`: success. From the result XML: `:core` 13 tests, 0 failed;
  `:engine:calctimes` 3 tests, 0 failed.
- Tests that passed, by id: T-PROP-1 (1,500 random Bangladesh cases per Asr convention, plus
  the six-zone test), T-UNIT-2, T-PROP-3, T-UNIT-5, T-PROP-6, T-UNIT-11, continuity (3 min/day),
  purity gate.
- Dhaka, 2026-11-26, zero offsets, as computed (subagent's run; NOT checked against any
  timetable): Sehri end 05:00, Fajr 05:01, sunrise 06:19, Dhuhr 11:46, Asr 15:35,
  Maghrib 17:12, Isha 18:31.

**Not done / follow-up.**
- No comparison with a reference timetable (Q2). No Android, no desugaring check.
- Property tests use fixed seeds, so they are reproducible but do not explore new cases per run.
- Kotlin 2.4.10 warns that Gradle 8.14.3 is deprecated; 8.14.4 becomes the minimum in
  Kotlin 2.5.0. Bump the wrapper when convenient.
- Engine notes: its solar position is the low-precision PrayTimes algorithm; only T-FIX-4 can
  say whether that is close enough. Nothing committed.

## 2026-11-26 18:31 +0600 · Implementation plan written; slice 1 committed; phase 2 decisions

**Did.**
- Committed slice 1 and the context folder as `764dc7a` on the founder's word.
- Founder decided: 48-hour window (G8), skip passed prayers (G16). ADR-3 amended, INV-S1
  reworded.
- Wrote `context/PLAN.md`: nine phases with checklists, a founder track, and the working
  standard. It replaces `spec/05-slices.md`, which is removed. `CLAUDE.md`, `/checkpoint` and
  `README.md` now make ticking the plan part of ending a task.
- Started phase 2 with a Sonnet subagent, from a written design.

**Verified.** `git log` shows `764dc7a`; the tree was clean after it. Phase 0 and 1 ticks in
`PLAN.md` correspond to earlier entries in this log. Phase 2 boxes are deliberately unticked.

**Not done / follow-up.** Phase 2 code is being written and has not been reviewed or re-run.
Commit author is the container default, not the founder.

## 2026-11-26 18:31 +0600 · Phase 2: alarm planner and Bengali digits

**Did.**
- A Sonnet subagent, from a written design, built `core/.../planner/` (`Reminders.kt`,
  `AlarmId.kt`, `Plan.kt`, `Planner.kt`) and `core/.../l10n/BengaliDigits.kt` with tests.
- Claude read the planner, id and digits code line by line. No changes needed.
- Two things the subagent changed from the design, both accepted: the planner looks 120
  minutes past the window end so a pre-reminder inside the window is not lost when its prayer
  is just outside; the replan is "00:05 local today if still ahead, else tomorrow's".
- Ticked phase 2 code and test items in `PLAN.md`; updated `spec/04`; opened G21 and G22.

**Verified.**
- `./gradlew build --rerun-tasks`: success. From the result XML: `CalcTimesClockTest` 12,
  `PurityTest` 1, `BengaliDigitsTest` 3, `PlannerTest` 22, engine 3. No failures, errors or
  skips.
- Subagent's mutation checks (its report, not re-run by Claude): `now < at` changed to
  `now <= at` is caught; dropping the 120-minute look-ahead is caught by a test added for it.
- Example plan, Dhaka, now = 2026-11-26 12:00 local, five Adhans, Fajr pre-reminder 15 min
  (subagent's run): 13 alarms, first is Asr 15:35 on the 26th, replan at 00:05 on the 27th,
  last is Dhuhr 11:47 on the 28th.

**Not done / follow-up.**
- Late rule and health model (phase 2, still open). Nothing on a device. Not committed.
- "Replan at most 24 h 05 m ahead" is asserted for Dhaka only; it is 25 h 05 m on a
  fall-back day in zones with daylight saving, which is correct behaviour.

## 2026-11-26 18:43 +0600 · Phase 2 finished: late rule and health model

**Did.**
- Committed the planner, digits and `PLAN.md` as `da2fd48` on the founder's word.
- Wrote `core/.../planner/Delivery.kt` (`decideDelivery`, `LATE_LIMIT` 10 min) and
  `core/.../health/Health.kt` (`assessHealth`, `HealthFacts`, `DegradedReason`, `Health`), with
  `DeliveryTest` and `HealthTest`. Written by Claude directly, not a subagent: two small files.
- Closed G21 (keep whole-day drop for v1). Ticked the rest of phase 2 in `PLAN.md`. Added a
  phase 3 note about clearing not-yet-due log rows on replan.

**Verified.** `./gradlew build`: success. From the result XML, `:core`: `CalcTimesClockTest`
12, `PurityTest` 1, `PlannerTest` 22, `BengaliDigitsTest` 3, `DeliveryTest` 4, `HealthTest` 8;
50 tests, no failures or errors.

**Not done / follow-up.** The 10-minute and 2-minute thresholds are targets, not measured.
Neither file has been used by a shell yet; phase 3 will show whether `HealthFacts` has the
right fields. These two files are not committed.
