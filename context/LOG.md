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
