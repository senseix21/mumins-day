# Mu'mins DAY

An offline, private Android app for Muslims in Bangladesh. Four features in v1: a reliable
Adhan (F1), Bangladesh-correct prayer times (F2), an honest Qibla compass (F3), and one good
deed a day (F4). Bengali first, English second. No network, no ads, no analytics, no account.

**Status: slice 1 of 6 is built** (the prayer-time core, JVM only). There is no Android app
yet. `context/STATE.md` has the current picture; `context/PLAN.md` has the phases and checklists.

## Start and end of every task

1. **Start.** Read `context/STATE.md`, find the task's items in `context/PLAN.md`, then read
   whichever of `context/spec/*` the task touches.
   Do not work from memory of the spec; it is the source of truth and it is short.
2. **End.** Before reporting the task as done, update the context folder (or run `/checkpoint`):
   - Tick the finished items in `context/PLAN.md` with the date, only if they are verified;
     add any item the work uncovered.
   - Rewrite `context/STATE.md` so it describes the project as it is now.
   - Append one entry to `context/LOG.md`.
   - Update `context/QUESTIONS.md` and `context/GAPS.md` if the task answered, opened or
     closed anything.
   - If a decision changed, edit the spec file and add or amend the ADR in
     `context/spec/03-architecture.md`. Never let code and spec disagree silently.
3. **Timestamps come from the clock, not from you.** Get one with
   `TZ=Asia/Dhaka date '+%Y-%m-%d %H:%M %z'` and paste the output. Never type a time by hand.

## The context folder

| File | What it holds | How it changes |
|---|---|---|
| `context/PLAN.md` | The implementation plan: phases and checklists | Boxes ticked as work is verified; items added as we learn |
| `context/STATE.md` | Where the project stands, what is next, what is blocked | Rewritten each task |
| `context/LOG.md` | What was done, when, and what was verified | Append-only, newest last |
| `context/QUESTIONS.md` | Open questions Q1…Qn for the founder, reviewer or a lawyer | Edited; closed ones keep their answer and date |
| `context/GAPS.md` | Holes and contradictions found in the spec | Edited; closed ones keep their resolution |
| `context/spec/00-base-audit.md` | What was read in the upstream app and engine | Frozen record of a read; append corrections, do not rewrite |
| `context/spec/01-constraints.md` | Invariants (`INV-*`) and constraints | Living spec |
| `context/spec/02-requirements.md` | F1 to F4, failure modes, stubs for later features | Living spec |
| `context/spec/03-architecture.md` | Components, scheduler, data, ADR-1…8 | Living spec; new ADRs continue the numbering |
| `context/spec/04-verification.md` | Traceability table, device matrix, manual tests, release checklist | Living spec; the Result column is filled from real runs only |

## Evidence labels

Every factual claim in the context folder carries one of three labels. Keep using them.

- `KNOWN`: read in a file (give the path) or on a fetched page (give the URL), or produced by
  a command that was run (give the command).
- `ASSUMED`: reasoning or general knowledge, not checked here.
- `UNKNOWN`: not established. Say how to find out.

A number chosen by us is marked **(target)** until a device measures it. A test result is
"Not run" until it ran. Do not upgrade a label without the evidence that earns it.

## Rules that are not up for negotiation

These come from `context/spec/01-constraints.md`. If a task seems to need breaking one, stop
and ask; do not work around it.

- **No network.** The manifest never declares `INTERNET` (INV-P1).
- **Permissions and dependencies are closed lists** in `03-architecture.md`. Adding either
  needs an ADR and the founder's yes first (INV-P3, INV-P4).
- **Claude writes no religious content.** No deed text, no hadith wording, no grading, no
  reward claims, no fiqh rulings, not even as placeholders that could ship. Deeds come from
  the named reviewer through `content/deeds.json` and the review gate (INV-C1, INV-C3). Test
  fixtures use obviously fake records such as `"text_en": "TEST DEED 1"`.
- **Never guilt the user.** No missed-day counts, broken streaks, points or blame (INV-C4).
- **The engine never invents a time.** A time that cannot be computed is a value that says
  so, and it sets no alarm (INV-T7).
- **No silent degradation.** If an exact alarm cannot be guaranteed, the home screen says so
  in words (INV-S6).
- **Bengali is complete or the build fails**, and every digit shown in Bengali is ০-৯
  (INV-L1, INV-L2). Bengali copy is written by a native speaker, not translated by Claude
  from English; a string Claude drafts is marked for native review.
- **Rounding is safe-side.** "Stop eating" rounds down; "may break fast" and "prayer may
  begin" round up (INV-T5).

## Shape of the code

One Android app, no flavours, no backend. A pure core with a thin shell, as three Gradle
modules (ADR-9). Root package `com.senseix21.muminsday`.

```
engine/calctimes/   vendored LGPL engine, one recorded change; do not edit (see below)
core/      plain Kotlin, no Android on the classpath; a test also bans clock and locale reads
  clock/     date + place + settings -> DayTimes | NotComputable
  planner/   now + settings -> list of AlarmSpec (next 24 h, plus one replan)
  deed/      date + content -> Deed
  bearing/   place -> Qibla bearing
  l10n/      Bengali digits, time and date text
  health/    facts about the phone -> Armed | Degraded(reasons)
tools/content-gate/   build-only: fails the build if content/deeds.json is not fully reviewed (ADR-10)
app/       Android only; arrives in slice 3
  ui/        Compose: Home, Qibla, Deed, Settings, Onboarding
  alarm/     apply the plan, receivers, playback service
  sensors/   compass
  store/     SharedPreferences settings + one SQLite database
  health/    permission and OEM checks -> Degraded reasons
content/   deeds.json, districts.json, reviewer notes
```

Dependencies point inward: `app` may use `core`; `core` never knows `app` exists, and engine
types never appear in `core`'s public API. The UI
never computes a time, never builds a notification, and never formats a date itself; it asks
`clock`, `alarm` and `l10n`. The application id and root package are
`com.senseix21.muminsday`; it cannot change after the first Play release.

The scheduler holds every enabled prayer in the next 24 hours, is recomputed on every
trigger, and is never persisted. `apply(plan)` is "cancel the whole closed id space, then set
the plan", which is what makes it idempotent (ADR-3, INV-S4).

## How to write the code

Two voices, and they agree more than they differ: DHH for what to leave out, Jon Gjengset for
what must be exactly right.

**Leave it out (DHH).**

- The default answer to a new dependency, layer, interface, module, flag or setting is no.
  Refused by name: a DI framework, a navigation library, Room, DataStore, WorkManager,
  kotlinx-serialization, any network or crash SDK. Platform APIs first.
- No interface with one implementation, with two exceptions the spec asks for: the `clock`
  seam (so the engine can be swapped, ADR-2) and `entitlements`.
- No repository/use-case/mapper ceremony. A function that takes values and returns values is
  the unit of design. Three similar lines beat a premature abstraction.
- Convention over configuration: one obvious place for each kind of thing, as in the tree
  above. If you have to ask where a file goes, the tree is wrong; fix the tree.
- Code reads top to bottom like prose. Names are full words from the domain: `sehriEnd`,
  `asrHanafi`, `Degraded`, not `t1`, `mgr`, `helper`, `util`.
- Delete what is not used. No commented-out code, no "for later" stubs, no TODO without a
  question id or gap id next to it.
- Small diffs that do one thing. A slice is finished when it runs on a phone.

**Get it exactly right (Gjengset).**

- Make illegal states unrepresentable. Sealed types for outcomes (`DayTimes` or
  `NotComputable(prayer, reason)`), value classes for units (`Minutes`, `Degrees`,
  `DistrictId`), enums for closed sets. A raw `Int` of minutes or a raw `Double` of degrees
  does not cross a function boundary.
- Errors are values. Pure code does not throw for expected failures and never returns a
  sentinel (no `00:00`, no `-1`, no `null` meaning "failed"). `!!` is banned outside tests.
- Time is the place where this app is wrong or right. Pure code takes `now` and the zone as
  arguments; it never reads the clock, `ZoneId.systemDefault()` or the default `Locale`
  (INV-T3). Instants cross boundaries; wall-clock times stay inside `clock` and `l10n`.
- Parse at the edge, trust inside. `deeds.json`, `districts.json`, preferences and intents
  are validated once into typed values; nothing downstream re-checks.
- Every `when` over a sealed type or enum is exhaustive with no `else`, so adding a case is a
  compile error where it matters.
- State an invariant, then test it as a property. Each `INV-*` has a test id in
  `04-verification.md`; write that test with the code, not after. Example tests show intent;
  property tests find the bug.
- Comments say why, never what: the constraint, the platform quirk, the invariant id. If the
  reason is a platform behaviour, cite the doc URL.
- Understand before you port. Code taken from upstream is read line by line, credited in
  `NOTICE`, and any behaviour whose reason is unknown (for example the Samsung dedupe, S5) is
  left out until a test shows it is needed.

**Where they pull apart, the rule is:** rigor in `core/` and in `app/…/alarm/`, plainness
everywhere else. A settings screen does not need a type-level proof. The planner does.

## Tests

- Pure code: unit and property tests on the JVM, no emulator. This is most of the test suite.
- Shell code: instrumented tests for the scheduler (`T-INST-*`), driven through
  `adb shell dumpsys alarm`.
- Build gates (`T-BUILD-*`) fail the build on: `INTERNET` in the merged manifest, a permission
  or dependency outside the allowlist, an unapproved or incomplete deed, a missing Bengali or
  English string.
- A bug fix starts with a test that fails.
- Never mark a row in `04-verification.md` as passed unless you ran it and saw it pass. Record
  the command and the date in `context/LOG.md`.

## Licences and upstream

- App code is Apache-2.0. The `calctimes` engine is **LGPL-3.0**: keep its notices, and
  publish any change made to it. It is vendored at `engine/calctimes/` with exactly one change,
  recorded in `engine/calctimes/MODIFICATIONS.md` (ADR-2). Any further edit there needs the
  founder's yes, a new line in that file, and an ADR note.
- Upstream (`metinkale38/prayer-times-android`) is a reference and parts donor, not a base to
  prune (ADR-1). Its author asks forks for a real redesign: copy no UI, assets, sounds, texts
  or store material.
- Do not reuse upstream's downloadable Adhan sounds or hadith database; their licences are
  unknown.

## Git

- `main` is always buildable once a build exists. Work on short branches.
- Commit messages: imperative subject under 70 characters, then a body that says why. Mention
  the invariant, feature or gap id the change serves (`INV-S1`, `F2`, `G3`).
- The context update for a task goes in the same commit as the task.
- Never commit signing keys, keystores, `local.properties` or `secrets.xml`.
- Commit and push only when asked.

## Commands

```bash
./gradlew build                  # compile everything, run every test
./gradlew :core:test             # the pure core's unit and property tests
./gradlew :engine:calctimes:test # upstream's own engine tests
./gradlew :tools:content-gate:checkDeedContent   # the review gate over content/deeds.json (part of build)
```

Gradle 9.9.2 wrapper (checksum pinned), Kotlin 2.4.10, JVM target 17. Repositories are Maven Central and the
Gradle plugin portal only. There is no Android module yet, so no instrumented tests and only one
build gate (T-BUILD-4, the content gate); add the others in phase 3.
