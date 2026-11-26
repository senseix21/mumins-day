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

## 2026-11-26 18:47 +0600 · Phase 6, pure part: Qibla bearing maths

**Did.**
- Committed the late rule and health model as `62ea747` on the founder's word.
- Wrote `core/.../bearing/Qibla.kt`: `Bearing` (always in [0, 360)), `qiblaDirection`
  (`Toward(bearing)` or `Everywhere`), `magneticBearing`, `Cardinal`, `CompassPoint`,
  `compassPoint`; and `QiblaTest`. Written by Claude directly.
- Ticked the pure items of phase 6 in `PLAN.md`; added two follow-ups (district extents,
  Bengali compass wording). `spec/04` T-UNIT-10 marked.

**Verified.**
- Reference values: `pip install geographiclib`, then
  `Geodesic.WGS84.Inverse(lat, lon, 21.42247, 39.826198)['azi1']` for eight cities. Dhaka
  277.5870, Chattogram 279.2734, Khulna 278.2576, Rajshahi 276.5031, Sylhet 277.0384,
  Barishal 278.6033, Rangpur 275.3735, Mymensingh 276.7123. The spherical formula differs
  from these by at most 0.034 degrees.
- `./gradlew build`: success. `:core` 59 tests (`QiblaTest` 9), no failures or errors.
- Printed by the test: largest bearing change over 50 km in Bangladesh is 0.468 degrees.

**Not done / follow-up.**
- City coordinates in the test are approximate centres from general knowledge (ASSUMED), not
  an official table (Q4). They are inputs to a maths check, so this does not weaken it.
- Declination itself, sensors, the screen: all need a phone. Size of the declination
  correction in Bangladesh is still UNKNOWN. Not committed.

## 2026-11-26 18:50 +0600 · Qibla maths committed; proposals for G4 and G5

**Did.** Committed the Qibla maths on the founder's word. Wrote proposed answers under G4 and
G5 in `GAPS.md` (not closed; they are proposals). Opened Q9 (number of deeds at launch).

**Verified.** `git log` shows the commit. Nothing was built for the proposals.

**Not done / follow-up.** Waiting for the founder to accept or change the proposals before
any deed code is written.

## 2026-11-26 18:56 +0600 · Phase 5, pure part: deed gate, rotation, day card

**Did.**
- Founder accepted the G4 and G5 proposals and set 30 deeds (Q9). INV-C1, INV-C2, F4 failure
  modes 3 and 6, the rotation rule and the table (`deed_day`) are amended; G4, G5, Q9 closed;
  G23 and G24 opened.
- A Sonnet subagent, from a written design, built `core/.../deed/` (`Model.kt`,
  `Provenance.kt`, `Gate.kt`, `Rotation.kt`, `DayCard.kt`) and its tests.
- Claude read the rotation, day-card and gate code. One fix: the subagent reported that a card
  kept across a content update could hold the deed that is the alternate, so a swap would
  show the same deed. `swap` now steps to the next deed in that case; a test covers every
  deed a stored card could hold, for five sizes and forty days.

**Verified.**
- `./gradlew build`: success. From the result XML: `GateTest` 15, `RotationTest` 6,
  `DayCardTest` 14, and the earlier classes unchanged; `:core` 94 tests, engine 3, no failures
  or errors.
- No religious text: `grep -P '[^\x00-\x7F]'` over the deed sources and tests finds one
  emoji used for a code-point test; a keyword grep finds only a code comment naming content
  kinds. Fixtures are `TEST DEED n BN/EN`, `TEST-n`, `TEST EDITION`, `TEST GRADING`,
  `TEST REVIEWER`.

**Not done / follow-up.**
- No `content/deeds.json`, no build task (T-BUILD-4, G24), no screen, no storage.
- The gate accepts a review date in the future and any non-blank source number; added to
  `PLAN.md` as a question for the reviewer.
- `Provenance.kt` and `Model.kt` were skimmed through their tests and the subagent's report,
  not read line by line.

## 2026-11-26 22:40 +0600 · Pushed to GitHub

**Did.** The founder created `github.com/senseix21/mumins-day`. Attached it to the session,
added it as `origin`, pushed `main`.

**Verified.** `git ls-remote origin` showed no branches before the push and
`refs/heads/main` at `0684bc1` after it, equal to local `HEAD`. The GitHub API reports the
repository as public, default branch `main`.

**Not done / follow-up.** No CI workflow yet. Commits are authored under the container's
default identity, not the founder's. This session cannot create repositories; it can only
use ones that exist and are attached.

## 2026-11-26 22:59 +0600 · CI workflow, LICENSE, NOTICE

**Did.**
- `.github/workflows/build.yml`: on push to `main` and on pull requests, JDK 17 (Temurin),
  `./gradlew build`, test reports uploaded on failure. Read-only token.
- `LICENSE`: the Apache-2.0 text, copied from upstream's `LICENSE` file (the standard text).
- `NOTICE`: copyright line, the LGPL engine and its PrayTimes.js origin with pointers to the
  licence files and `MODIFICATIONS.md`, and what has been taken from prayer-times-android.

**Verified.** `./gradlew build --no-daemon --console=plain` exits 0 locally, on JDK 21. The
workflow uses JDK 17, which has not been run here.

**Not done / follow-up.** The copyright holder's name is taken from the founder's GitHub
profile and needs the founder's confirmation. Result of the first CI run: see the next entry.

## 2026-11-26 23:01 +0600 · First CI run is green

**Verified.** GitHub Actions run 45863768896 for commit `670b4d8`: completed, success. Steps:
checkout, setup-java (Temurin 17), `./gradlew build --no-daemon --console=plain`, all
success; the failure-only artifact upload was skipped. This is also the first time the build
has run on JDK 17 rather than 21.

**Not done / follow-up.** Nothing.

## 2026-11-26 23:10 +0600 · Gradle wrapper 8.14.3 to 9.9.2

**Did.** Ran `./gradlew wrapper --gradle-version 9.9.2 --gradle-distribution-sha256-sum …`
twice (the second run regenerates the wrapper jar and scripts with the new version). Chose
9.9.2, the current release, over the last 8.14 patch because phase 3 will need a 9.x Gradle
for the Android plugin upstream uses (9.3.1 on Gradle 9.6.1), so this avoids a second bump.

**Verified.**
- `./gradlew --version`: Gradle 9.9.2.
- `./gradlew build --rerun-tasks --no-daemon --warning-mode all`: success, 97 tests, none
  failed, and the "Deprecated Gradle Version" warning from Kotlin 2.4.10 is gone. No other
  deprecation warnings.
- Second CI run (commit `d1b93b8`) was green before this change.

**Not done / follow-up.** The pinned checksum
`971fcd71…914c76b` is the SHA-256 of the file downloaded here from
`services.gradle.org`, computed locally. It was NOT compared with Gradle's published
checksum: `downloads.gradle.org` is blocked in this container. It protects later downloads
against change, but someone should compare it once at gradle.org/release-checksums.

## 2026-11-26 23:15 +0600 · T-BUILD-4: the content gate build task

**Did.**
- CI run for the wrapper bump (`4c685f5`, run 45865360833) confirmed green.
- New module `tools/content-gate` (`Gate.kt`, `Main.kt`, `GateTest.kt`, `build.gradle.kts`):
  reads `content/deeds.json` with Gradle's bundled Groovy JSON reader, refuses wrong types and
  unknown keys, then calls `checkContent` from `:core`. Task `checkDeedContent` is wired into
  `check`, so `./gradlew build` and CI run it. Written by Claude directly.
- ADR-10 written; G24 closed; `CLAUDE.md` layout and commands updated.
- Committed as `bb64986`. A script error left `STATE.md`, this entry and the `spec/04` row out
  of that commit; they follow in the next one.

**Verified.**
- `./gradlew build --rerun-tasks --warning-mode all`: success, 109 tests (94 core, 12 gate,
  3 engine), none failed, no warnings.
- By hand, with a temporary file of 30 fake records generated by a script and then deleted:
  no file: passes with "nothing to check"; no file with `-PdeedsRequired=true`: build fails;
  30 fake records: "passed. Version 1: 30 approved, 0 retired."; one draft and one record
  without a reviewer: build fails, listing both by index and id plus "fewer than 30 approved".
- `content/` does not exist in the repository after the test.

**Not done / follow-up.** No real content. The phone-side parse is phase 5 work. The task
has no declared outputs, so it runs on every build; that is intended for a gate.
