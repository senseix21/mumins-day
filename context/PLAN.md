# Implementation plan

Built from the five design documents in `spec/`. This is the one plan; `STATE.md` says where
we are in it today, `LOG.md` says what happened.

## How to use this file

- `[ ]` not done. `[x]` done **and verified**: the item's evidence is in `LOG.md` under the
  date written after the tick. Never tick on intent, or on a subagent's word alone.
- **(blocked: X)** names the question (`Q`), gap (`G`) or resource that holds an item up.
- Ids in brackets tie an item to its invariant (`INV-*`), test (`T-*`, `M-*`), feature
  failure mode (`F1#3` = F1, row 3 in `spec/02`), or decision (`ADR-*`).
- An item is added, split or struck through when the work teaches us something. Do not
  delete a done item; history is the point.
- Every task ends by updating this file together with `STATE.md` and `LOG.md`.

## The standard every item is held to

The full rules are in `../CLAUDE.md`. In one screen:

**Leave it out (DHH).** No dependency, layer, interface, module or setting without a need on
this page. Platform APIs first. Plain functions over values. Names from the domain. Delete
what is unused. One small diff per checklist item where possible.

**Get it exactly right (Gjengset).** Illegal states do not compile: sealed results, value
classes for units, exhaustive `when` with no `else`. Errors are values; no sentinels, no
`!!` outside tests. Pure code takes `now` and the zone as arguments. Every invariant has a
property test written with the code. Comments say why and cite the invariant.

**Definition of done for any code item:** compiles with warnings as errors; its tests are
green from a clean `./gradlew build`; no new dependency or permission; the spec agrees with
the code; this file, `STATE.md` and `LOG.md` are updated; committed only on the founder's word.

## Phases at a glance

| Phase | Outcome | Where it runs | Status (2026-11-28) |
|---|---|---|---|
| 0 | Repository, rules, context, decisions | Anywhere | Done except one checksum comparison |
| 1 | Prayer times are computed correctly and safely | JVM | Done; calibrated to the Foundation's Dhaka table (ADR-16). Open: reviewer rulings |
| 2 | The alarm plan is computed correctly; Bengali digits | JVM | Done |
| 3 | An Adhan rings on a real phone, on time, after every disruption | Phone | Built; all 10 instrumented tests passed on emulators (API 26, 31, 35, 36) on 2026-11-27, before the 28 Nov changes. Open: latency, a real phone |
| 4 | A Bengali user completes first run offline and trusts what they see | Phone | Built with real districts. Open: native-speaker review, TalkBack, location (removed by ADR-12) |
| 5 | One reviewed deed a day | JVM + phone | Built on fake records. Open: the reviewer's deeds |
| 6 | A Qibla compass that admits doubt | JVM + phone | Built. Open: Bengali wording, a real compass test |
| 7 | Proof on the device matrix; size and speed measured | Phones | Not started: needs phones. Release APK 184,738 bytes |
| 8 | On Google Play | Play Console | Build side ready (signing, store script, policy read). Open: Play account, forms, Adhan audio, closed test |
| 9–17 | Part 2: widgets, Salah tracker, Hijri, Zakat, duas, hadith, Quran, khatam, audio packs | JVM + phone | Planned; see Part 2 below |

Phases 1, 2 and the pure parts of 5 and 6 need nothing but a JVM. From phase 3 on, building
needs the Android SDK (now in the cloud environment) and proof needs phones (G13). The founder track below runs alongside everything.

---

## Founder track: things only the founder can supply

These gate later phases. Starting them now is what keeps phases 3 to 8 from stalling.

- [x] A remote for this repository: `github.com/senseix21/mumins-day`, `main` pushed. 2026-11-26
- [x] CI on the remote (G11). 2026-11-26
- [ ] Git author name and email for commits.
- [x] Android SDK in the cloud environment: environment `mumins-day` allows `dl.google.com`; SDK at `/opt/android-sdk` (platform 37, build tools 37 and 36); an empty probe app builds an APK. 2026-11-26
- [ ] Confirm the saved setup script installs the SDK in a fresh session (unproven until one starts).
- [ ] A physical phone, or an emulator that can run here, for anything that must ring or sense (G13). Gates the instrumented tests of phase 3.
- [ ] Reference timetable for Bangladesh, a full year, eight divisional cities (Q2). Gates T-FIX-4. **Partly answered 2026-11-28:** the Islamic Foundation's permanent Dhaka timetable (12 months, 6 days each) is now the reference, from a mirrored copy; a full-year table for the other districts does not exist, because the Foundation times them as Dhaka plus a fixed offset (ADR-16, G50).
- [ ] Reviewer named, with an agreed way of signing content (G9, INV-C1). Gates phase 5 and three rulings in phase 1.
- [ ] Native Bengali speaker for all copy (G9, INV-L1, INV-C4). Gates phase 4.
- [ ] Reviewer rulings: Sehri margin and two-value display (Q3); Sehri end vs Fajr offset (G19); sunrise rounding (G20).
- [ ] District list: 64 districts, names in both languages, latitude, longitude, elevation, with a licence (Q4). Gates phase 4.
- [ ] Adhan recording and short tone, licensed in writing (Q5). Gates release; phase 3 can use a test tone.
- [ ] Legal opinion on LGPL in a Play app (Q6). Gates any paid tier, not v1.
- [ ] Device facts and the exact test phones (Q8). Gates phase 7.
- [ ] Team, budget, launch date (Q1).
- [ ] What Q7 was, and the original questions file and project Sheet if they exist (G2).
- [ ] UI direction: screens, flows, visual style clearly unlike upstream (G12). Gates phase 4.
- [ ] Play developer account; its type and creation date (G14). Gates phase 8.
- [ ] Privacy policy host and a support email address (G15). Gates phase 8.
- [ ] Repository `LICENSE` (Apache-2.0) and `NOTICE` approved (G10). Both are written; the founder should confirm the copyright line "Copyright 2026 Mehedi Hasan".

---

## Phase 0 · Foundation

**Outcome.** Anyone, or any agent, can pick the project up cold.

- [x] Repository and git initialised. 2026-11-26
- [x] `CLAUDE.md`: rules, layout, standards, task protocol. 2026-11-26
- [x] Context folder: spec, `STATE`, `LOG`, `QUESTIONS`, `GAPS`, `/checkpoint`. 2026-11-26
- [x] Upstream app and engine cloned at the audited commits, outside the repository. 2026-11-26
- [x] Spec read for gaps; 20 recorded. 2026-11-26
- [x] Decision: new app, not a pruned fork [ADR-1]. Given
- [x] Decision: vendor the engine with one change [ADR-2 amended, G1, G3]. 2026-11-26
- [x] Decision: plain Kotlin `:core` module [ADR-9, G18]. 2026-11-26
- [x] Application id `com.senseix21.muminsday` [G10]. 2026-11-26
- [x] First commit `764dc7a`. 2026-11-26
- [x] `LICENSE` (Apache-2.0) and `NOTICE` at the root, crediting upstream and pointing to the engine's notices. 2026-11-26
- [x] CI running `./gradlew build` on every push and pull request (`.github/workflows/build.yml`); first run green. 2026-11-26
- [x] Gradle wrapper bumped to 9.9.2 with a pinned distribution checksum; clean build shows no deprecation warning. 2026-11-26
- [ ] Compare the pinned wrapper checksum with the one Gradle publishes (the checksum host is blocked in the cloud container).

## Phase 1 · Prayer-time core (F2)

**Outcome.** `date + place + settings → DayTimes | NotComputable`, pure, tested, in `:core`.

**Exit.** All boxes ticked, including the timetable fixture.

Build
- [x] Gradle scaffold: wrapper, version catalog, `:engine:calctimes`, `:core`. 2026-11-26
- [x] Engine vendored; diff against upstream is exactly two visibility modifiers; `MODIFICATIONS.md` written. 2026-11-26
- [x] Upstream's own engine tests still pass (3). 2026-11-26

Code
- [x] Unit types: `Minutes`, `Meters`, `Latitude`, `Longitude`, `Place`. 2026-11-26
- [x] `PrayerClock` seam and `ClockResult` (`Computed` | `NotComputable`) [ADR-2 reverse path]. 2026-11-26
- [x] `CalcTimesClock`: Karachi 18°/18°, Hanafi default, Shafi available. 2026-11-26
- [x] Offsets through the engine's own minute fields; one mechanism. 2026-11-26
- [x] Safe-side rounding with a documented tolerance [INV-T5]. 2026-11-26
- [x] NaN becomes `NotComputable`, never 00:00 [INV-T7]. 2026-11-26
- [x] Local-day attribution correct in any zone. 2026-11-26

Tests
- [x] T-PROP-1 order and same civil day [INV-T1]. 2026-11-26
- [x] T-UNIT-2 Iftar is Maghrib; Sehri margin [INV-T2]. 2026-11-26
- [x] T-PROP-3 determinism across zones and locales [INV-T3]. 2026-11-26
- [x] T-UNIT-5 rounding [INV-T5]. 2026-11-26
- [x] T-PROP-6 offset linearity [INV-T6]. 2026-11-26
- [x] T-UNIT-11 no invented time [INV-T7]. 2026-11-26
- [x] Continuity: no time moves more than 3 minutes day to day. 2026-11-26
- [x] Six non-Dhaka zones, two years, including daylight-saving changes. 2026-11-26
- [x] Purity gate: no Android, clock or locale reads in `:core` main sources. 2026-11-26

Still open
- [x] T-FIX-4 for Dhaka: `IslamicFoundationTableTest` against the Foundation's permanent timetable (71 rows, 2025 to 2028) [INV-T4, ADR-16]. Within 1 minute for Dhuhr only; every prayer within 1 minute on the unsafe side, by construction of the calibration. Other districts: no full reference exists (G50). 2026-11-28
- [x] Steady bias found and set as the default: `Calibration.islamicFoundation` (Fajr +2, Dhuhr +3, Maghrib +3, Isha +2, Sehri 6 min before Fajr), fixed in the app, user offsets on top [ADR-16]. Per-district override not built: districts follow their own sun (G50). 2026-11-28
- [ ] Reviewer rulings applied or confirmed [Q3, G19, G20]. (blocked: G9)
- [x] Measured on the JVM: a year of times for Dhaka in about 1 ms (`BudgetTest`); the 100 ms target is for the minimum phone, phase 7. 2026-11-28
- [ ] Per-district spread: confirm one fixed point per district keeps every time within 1 minute east to west, else subdivide [ADR-8]. (blocked: Q4)
- [x] `isInsideBangladeshBoundingBox` in core, named as a box, not a border; edge tests [F1#6]. 2026-11-28

## Phase 2 · Alarm planner and Bengali digits (F1, pure part)

**Outcome.** `now + settings → Plan`, pure and replayable; digits render in Bengali.

**Exit.** All boxes ticked; reviewed by Claude, not only by the agent that wrote it.

Decisions
- [x] Window is 48 hours [ADR-3 amended, G8]. 2026-11-26
- [x] Passed prayers are skipped; alarms strictly after now [G16, INV-S5]. 2026-11-26
- [x] Closed id space of 64; id from local date, prayer and kind. 2026-11-26

Code
- [x] `Salah`, `ReminderMode`, `PreReminder` (1..120), `Reminder`, `Reminders`. 2026-11-26
- [x] `AlarmId` in a closed space; `AlarmId.all` for cancel-all [INV-S4]. 2026-11-26
- [x] `AlarmSpec`: `PrayerAlarm`, `PreReminderAlarm`, `Replan`. 2026-11-26
- [x] `Plan(alarms, gaps)`; a not-computable day is a gap with no alarms [INV-T7]. 2026-11-26
- [x] `Planner.plan(now, settings)`; one replan just after the next local midnight. 2026-11-26
- [x] `toBengaliDigits` [INV-L2]. 2026-11-26

Tests
- [x] Completeness: every enabled prayer in the window, exactly once [INV-S1]. 2026-11-26
- [x] T-PROP-7: each alarm instant equals the clock's, under any device zone [INV-S2]. 2026-11-26
- [x] Stability across replans: same alarm, same id, same instant [INV-S4]. 2026-11-26
- [x] Nothing at or before now; planning at a prayer's instant omits it [INV-S5]. 2026-11-26
- [x] Ids distinct and inside the closed space, including across daylight-saving changes in nine zones. 2026-11-26
- [x] A pre-reminder inside the window whose prayer lies just beyond it is still planned (found by a mutation check). 2026-11-26
- [x] Exactly one replan; `Off` plans nothing; gaps isolate one day. 2026-11-26
- [x] T-UNIT-9 (unit part): all ten digits; other characters untouched [INV-L2]. 2026-11-26
- [x] Claude's review and an independent re-run of `./gradlew build`. 2026-11-26

Late rule and health model
- [x] Late rule as a pure function: `decideDelivery(plannedAt, deliveredAt) → Ring | SilentNotification`, 10 minutes **(target)** [INV-S7, F1#17]. In `:core` so phase 3 only calls it. 2026-11-26
- [x] Decided: one uncomputable prayer drops that day's alarms, as built. It cannot happen inside Bangladesh, and a finer result would complicate the clock's contract for no v1 user [G21]. 2026-11-26
- [x] Health model as values: `assessHealth(now, facts) → Armed | Degraded(reasons)`, five reasons as a sealed type, and the "late or missed in the last 7 days" rule over log rows [INV-S6, INV-S8]. Pure; phase 3 supplies the facts. 2026-11-26
- [x] Tests: late-rule boundaries; each fact gives exactly its reason; fixed order; log window and thresholds; property that Armed holds exactly when nothing is wrong. 2026-11-26

## Phase 3 · Android shell: the Adhan rings (F1)

**Outcome.** On a real phone, with the app swiped away and the screen off, a test Adhan and
the next real prayer both ring on time, and keep ringing after reboot, update, zone and
clock changes.

**Exit.** T-INST-1…10 and T-BUILD-1…3 green; M-1 steps 1 to 4 pass on one phone.

**Needs.** One phone (G13); the SDK is available in the cloud environment, so the app can be built and its build gates run without one. Read first: G6, G7.

Before code
- [x] Fetched and recorded Android's rules: foreground-service start from an exact alarm, `mediaPlayback` type, notification-denied behaviour (in `GAPS.md` G6, G7). 2026-11-26
- [ ] Still to fetch: direct boot [G6, F1#10]. Done 2026-11-27: the user can stop a foreground service from the Task Manager on Android 13+ (G6).
- [x] Decided (ASSUMED, confirm on a device): the service always posts its notification with a Stop action; opening the app also stops it; on Android 13+ the Task Manager stops it too [G6, G31]. 2026-11-27
- [x] `SCHEDULE_EXACT_ALARM` carries `maxSdkVersion=32`; Android 13+ relies on `USE_EXACT_ALARM` [ADR-4]. 2026-11-26
- [ ] The grant flow for Android 12 (API 31, 32), where the user must allow exact alarms [ADR-4, F1#3].

Build
- [x] `:app` module: application id, minSdk 26, target and compile 37, one build, no flavours, desugaring for `java.time`; lint with warnings as errors; a placeholder screen and icon. 2026-11-26
- [x] The debug APK installs and opens on the `streambert` emulator (Pixel 8, API 36, arm64): `MainActivity` resumed, cold start 334 ms, no crash in logcat. First time the app has run. 2026-11-27
- [x] Manifest with exactly the allowlisted permissions, no `INTERNET`; confirmed inside the built APK with `aapt2`. Nine on 2026-11-26; seven since 2026-11-27 (ADR-12).
- [x] T-BUILD-1: fail on `INTERNET` in the merged manifest [INV-P1]; seen failing. 2026-11-26
- [x] T-BUILD-2: fail if merged permissions differ from `app/allowlist/permissions.txt`, either way, including `maxSdkVersion` [INV-P3]; seen failing three ways. 2026-11-26
- [x] T-BUILD-3: fail if packaged libraries differ from `app/allowlist/dependencies.txt`, transitive ones included [INV-P4]; seen failing. 2026-11-26
- [ ] Backup is switched off for now (nothing is stored). Phase 4 writes the real rules.
- [x] Closed 2026-11-27 by ADR-11 (platform Views, no new dependency). Was: Compose and Material 3 bring dozens of transitive AndroidX libraries, and `androidx.core` adds a permission of its own to the merged manifest. Both allowlists will need an ADR-backed update then [G25].

Store
- [x] Settings in one SharedPreferences file with versioned keys, parsed once into typed values [ADR-5]. JVM tests pass (7); the Android `SettingsStore` shell has not run on a device. 2026-11-27
- [ ] SQLite via the platform helper: `alarm_log`, `deed_day`; numbered migrations and a migration test. Written; JVM tests check the migration list (7) and log logic (4) and pass 2026-11-27; the SQL ran only as raw `sqlite3` on the emulator, never through the app. Tick when an on-device run opens and upgrades a real database.
- [x] Alarm log trimmed to 30 days: `trim(now)` and pure `logCutoff(now)`, unit-tested. 2026-11-27

Alarm path
- [x] `apply(plan)` (emulator only): cancel all 64 ids, register the plan [INV-S4]. Adhan via `setAlarmClock`; silent and pre-reminder via `setExactAndAllowWhileIdle`.
- [x] Replan on (emulator only): boot, time set, zone changed, package replaced, exact-alarm permission changed, app foreground, settings change, alarm delivered, the replan alarm [INV-S3, F1#9, 10, 11].
- [x] Delivery (emulator only): late rule, silent-mode choice, phone-call check, then play or post silently [F1#15, 16, 17].
- [x] Playback (emulator only) as a `mediaPlayback` foreground service; wake lock for the recording plus a margin; a Stop control.
- [x] One place builds notifications; channels defined once (`alarm/Notifications.kt`).
- [x] Log planned and delivered instants and the outcome [INV-S8]. On replan, delete log rows for alarms that were planned but not yet due, or a reminder the user switched off will later read as missed.
- [x] "Test Adhan": a real alarm 30 seconds ahead through the same path (adb-triggered in debug; no screen yet).
- [x] Health checks (gather facts, call `assessHealth`; no screen, not run on a phone): exact alarms, notifications, battery optimisation, late or missed alarms, manufacturer [INV-S6, F1#2, 3, 12].
- [x] A test tone (440 Hz sine generated in code, no third-party audio) until the real recording is licensed. (real audio blocked: Q5)

Instrumented tests
- [x] T-INST-1 window present in `dumpsys alarm` [INV-S1]. Emulator API 36 2026-11-27
- [x] T-INST-2 reboot, T-INST-3 zone, T-INST-4 clock, T-INST-5 update pass on the emulator 2026-11-27.
- [ ] T-INST-6 permission regrant [INV-S3]. NOT RUN: needs an API 31 or 32 device (on API 36 `USE_EXACT_ALARM` keeps the permission true).
- [x] T-INST-7 schedule 100 times, set unchanged [INV-S4]. 2026-11-27
- [x] T-INST-8 replan during playback, one playback [INV-S5]. 2026-11-27
- [x] T-INST-9 late rule [INV-S7]. 2026-11-27
- [x] T-INST-10 log rows [INV-S8]. 2026-11-27

Measure and decide
- [ ] Alarm-to-audio latency (target 3 s); planner run (target 50 ms).
- [x] Does a pending alarm survive `adb install -r`? Yes, on the emulator (API 36) 2026-11-27; a phone is still to check.
- [ ] Is before-first-unlock support worth it? Default no [F1#10].
- [ ] Does the alarm-clock icon bother users? Fallback is exact-while-idle for all kinds.
- [ ] Port note: upstream's Samsung dedupe stays out unless D2 shows it is needed [S5].

## Phase 4 · First run, home and settings, in Bengali (F1, F2)

**Outcome.** Fresh install in airplane mode: pick a district, see today's times in Bengali
digits, turn the Adhan on, be told plainly about anything that could stop it.

**Exit.** M-4 passes; T-UI-1, T-BUILD-5 green; a native speaker has read every string.

**Needs.** District data (Q4), native speaker (G9), UI direction (G12).

Content and data
- [x] `content/districts.json`: 64 districts from Wikidata (CC0) checked against Wikipedia, elevation from Open-Meteo; sources per row in `content/districts-SOURCES.md`; gate passes with the file required; the release APK ships it [Q4]. Bengali names need a native speaker. 2026-11-28
- [x] T-BUILD-5 (`:app:checkStrings`) fails on a missing key either way, an empty English value, or a Latin digit in Bengali; seen failing eight ways 2026-11-27.
- [ ] Bengali strings reviewed by a native speaker: 16 of 16 drafted by Claude are listed unreviewed in `app/l10n-review.txt` (blocked: G9).
- [ ] `l10n`: time text (12-hour with day-part words, 24-hour option), Gregorian date in Bengali; wording from the native speaker. Pure code and property tests done 2026-11-27 (words come in as data); the day-part hour boundaries and all words need the native speaker.
- [ ] Plural resources for every counted phrase. Selection logic done; phrases come with the screens and the native speaker.
- [x] Arabic runs wrapped in directional isolates [INV-L3]: `isolateArabicRuns` in `:core`, property-tested 2026-11-27. Screen check T-UI-3 not run.

Screens (platform Views, no Compose, no Material, no AndroidX, no navigation library) [ADR-11]
- [x] Onboarding (platform Views; emulator, airplane mode, Bengali; location left out by choice): language, district (list or coarse location), reminder choices, silent-mode choice, per-brand steps, the force-stop sentence [F1#18].
- [x] Home in Ramadan mode: Sehri end and Iftar as the two largest items with one countdown, switched on by the user; no Hijri date is computed, because Bangladesh follows the moon-sighting announcement (G33, Claude's ruling) [F2#5, 6]. Not seen on a device. 2026-11-28
- [x] Settings (seen on emulator 2026-11-27; offset and Asr convention changed times as expected): per-prayer mode and pre-reminder, per-prayer offset ±30 framed as "matches my mosque" [F2#1], Asr convention under Advanced, language, "Send a report".
- [x] Reminder health 2026-11-27 (emulator): seven days of planned and actual times [INV-S8].
- [ ] Open-source licences screen built 2026-11-27; wording and the repo URL shown need the founder's confirmation.
- [x] "—" for a time that cannot be computed [F2#4]: `times_unavailable`, JVM-tested; not seen on a device 2026-11-27.

Behaviour
- [ ] Location: NOT built (district by hand only); the three location items remain. Location read only while a screen needs it; coarse; only the district or rounded coordinates stored [INV-P2]; T-INST-11.
- [ ] A fix outside Bangladesh or over 50 km away asks before changing; a new nearest district is offered once, never switched silently [F1#6, 7].
- [x] Per-brand guidance for Xiaomi, Realme, Samsung, Oppo, Vivo, Tecno (also Infinix and itel), OnePlus, Huawei, Motorola from dontkillmyapp.com, in our own words; one map drives screen and health check (`alarm/Brands.kt`). No deep links: none are named on the pages for current versions. Not checked on a phone. 2026-11-28
- [ ] "Send a report" (draft intent and `ReportTest` done 2026-11-27; recipient empty until G15; the draft text itself not seen): plain-text email draft with versions, model, health results, 7-day log; no identifiers [ADR-7].
- [ ] Backup rules written, lint-clean, never run (settings in; alarm log, database and device flag out; deed completions share the alarm log's database, so they are out too: G34). Original item: settings and deed completions in, alarm log out; health check after restore [F1#12].
- [x] `entitlements` interface that returns "everything" (`core/.../entitlements`). 2026-11-27

Tests
- [ ] T-UI-1 each Degraded reason shows its message and fix. JVM half passes; on the emulator 2 of 5 reasons seen 2026-11-27.
- [x] T-UNIT-9 (screen part): no Latin digit in text or content-desc on Home, Settings, onboarding in Bengali (`tests/inst/ui.sh`) 2026-11-27.
- [ ] T-UI-3 Arabic inside Bengali NOT RUN (no Arabic on any screen yet). [x] T-UI-4 at 200 % font scale: Home, Reminder health, Licences, Settings seen not clipping 2026-11-27 [INV-L3, INV-L4]; Bengali Licences/Health/Settings at 200 % not seen.
- [ ] TalkBack labels in Bengali not tested. Touch targets >= 48 dp: passed on Home and Settings 2026-11-27 (`ui.sh`).

## Phase 5 · One good deed a day (F4)

**Outcome.** One reviewed deed per day, with its source, and no way for unreviewed text to ship.

**Exit.** T-BUILD-4, T-PROP-8 green; reviewer's signed note committed; T-UI-2 done.

**Needs.** Reviewer (G9). Claude writes no deed text, ever; fixtures say `TEST DEED n`.

Before code
- [x] Settled: the alternate is the deed half a cycle away [G4]. 2026-11-26
- [x] Settled: the card is recorded when first shown; the no-repeat promise is per content version; 30 deeds minimum [G5, Q9]. 2026-11-26

Pure, in `:core` (can start now)
- [x] Deed record type with every field in `spec/02`; `RawDeed` at the edge, `Deed` only out of the gate. 2026-11-26
- [x] Rotation: `deedFor` and `alternateFor` (half a cycle away) [INV-C2, G4]. 2026-11-26
- [x] Review gate: `checkContent` collects every problem; the provenance check knows nothing about deed text, so duas and hadith can reuse it [INV-C1, INV-C3]. 2026-11-26
- [x] Day card: `cardFor`, `swap`, `markDone`, `weekDots` [G5, INV-C4]. A swap always changes the deed, including for a card kept across an update. 2026-11-26
- [x] Gate tests: every required field, draft, bad values, duplicates, fewer than 30, text length in code points. 2026-11-26
- [x] T-PROP-8 rotation properties. 2026-11-26
- [x] Fixtures checked by Claude: fake values only, no religious text. 2026-11-26
- [ ] Review-date sanity (a date in the future is accepted today) and source-number format: ask the reviewer whether the gate should check them.

Build and app
- [x] T-BUILD-4: `tools/content-gate` parses `content/deeds.json` and runs the core gate as part of `./gradlew build`; fails on a draft, a missing field, reviewer or date, or fewer than 30 approved; lists every problem [INV-C1, INV-C3, ADR-10]. 2026-11-26
- [ ] (packaging done 2026-11-27: release stages `content/deeds.json` when present and prints a notice when missing; flag stays off until the file exists) When the app module packages the deeds file, set `deedsRequired=true` so a missing file fails the build.
- [x] On the phone (2026-11-27, debug build on the emulator; Rejected path and release/Unavailable path not run on a device): parse the same file with the platform parser into `RawDeed` and call `checkContent`; refuse to show a card if it is rejected.
- [ ] `content/deeds.json` and the reviewer's signed note, both from the reviewer. (blocked: G9)
- [x] Card: text, source book and number, Bengali digits; one tap marks done; quiet done state. Seen on the emulator with 30 fake records, 2026-11-27 (`tests/inst/deed.sh`, T-UI-2 screen part PASS, 200 % font seen).
- [x] Week view: seven dots, no numbers [INV-C4]; 3 of 7 filled after seeding, seen 2026-11-27.
- [x] Daily nudge built: off by default, a time the user picks, one notification a day, none if the deed is done, never re-posted, not counted in reminder health (G35, Claude's ruling). Not seen on a device. 2026-11-28
- [ ] `deed_day` table keyed by local date, one row per card shown; moving the clock back does not undo a day [F4#4].
- [ ] Retiring a deed is a content change only [F4#5].
- [ ] T-UI-2: tone review of every string and screen by the reviewer and the native speaker.

## Phase 6 · Qibla (F3)

**Outcome.** One screen: which way to face, the bearing as a number, and an honest warning
when the sensor cannot be trusted. First phase to move to v1.1 if time runs short.

**Exit.** T-UNIT-10, T-INST-12, T-INST-13 green; M-3 passes on one phone.

Pure, in `:core` (can start now)
- [x] Great-circle bearing from a place to the Kaaba (21.42247 N, 39.826198 E), as a `Bearing` in [0, 360); `Everywhere` at the Kaaba and its antipode [INV-Q1]. 2026-11-26
- [x] T-UNIT-10: eight cities within 0.05° of WGS84 geodesic azimuths from geographiclib; plus agreement with a vector derivation at 5,000 random points. 2026-11-26
- [x] Bearing spread: at most 0.468° over 50 km anywhere in Bangladesh, so the stored district is good enough [F3#4]. 2026-11-26
- [ ] Re-check the spread against real district extents once the district table exists; the largest districts are wider than 50 km. (blocked: Q4)
- [x] Compass point as a structure (`Exactly(West)`, `Between(West, 8, North)`); Dhaka gives the spec's own "8° north of west". 2026-11-26
- [x] `magneticBearing(trueBearing, declinationEast)`, so the app only has to supply the declination [F3#6]. 2026-11-26
- [ ] Bengali wording for compass points: 26 Claude drafts (`qibla_*`, `cardinal_*`, locative forms) listed unreviewed; the native speaker must review. (blocked: G9)

App
- [x] Declination from the platform's `GeomagneticField`, offline; measured on the emulator 2026-11-27: -0.25 to -0.87 degrees across 11 Bangladesh points (-0.61 at Dhaka) [F3#6]. Phone value not measured.
- [x] Rotation-vector heading with accelerometer+magnetometer fallback, and a circular low-pass filter written new in `:core` (not ported from upstream, so no NOTICE entry), property-tested, 2026-11-27. Behaviour on real hardware unmeasured; the 200 ms time constant is a (target).
- [ ] Accuracy below high: needle greyed, warning, calibration prompt [INV-Q2]. JVM-tested for all lower levels; T-INST-12 passes on the emulator for field strength only (the emulator always reports high accuracy), so the accuracy half is NOT RUN on a device.
- [x] Field strength outside 30 to 60 microtesla (the platform's own `MAGNETIC_FIELD_EARTH_MIN/MAX`): interference warning, seen at 200 and 5 microtesla on the emulator 2026-11-27 [F3#2]. Treating them as hard limits is our choice (target).
- [x] No magnetometer: number and compass point only, no needle [INV-Q3]; T-INST-13 PASS on the AVD `streambert_nomag` (no magnetometer), 2026-11-27.
- [x] "Lay the phone flat" prompt [F3#5]: shown above 25 degrees tilt (our number, target); seen upright on the emulator, untested on a phone.

## Phase 7 · Proof on real phones

**Outcome.** The reliability claim is measured, not hoped for.

**Exit.** M-1 to M-4 pass on D1 to D6; budgets met or consciously changed.

- [ ] Device matrix bought or borrowed: Xiaomi, Samsung, Realme/Oppo, Infinix/Tecno, a 2 GB phone on Android 8, Pixel or emulator. (blocked: Q8)
- [ ] M-1 on every device, with and without the per-brand steps.
- [ ] M-2 seven-day soak: 35 of 35 Adhans per phone.
- [ ] M-3 Qibla at a surveyed point; magnet and laptop tests.
- [ ] M-4 first run offline on every device.
- [ ] Bengali conjuncts and Arabic diacritics render on every phone; bundle a font only if one fails.
- [ ] Measured 2026-11-27 (emulator and JVM only, never a 2 GB phone): release APK 1,055,071 bytes after R8 and the bundled licence texts (target 15 MB; no Adhan recording yet; 1,034,164 before); cold start median 2600 ms (debug, emulator, no target); PSS 42.6 MB; a year of times 1.21 ms and one plan 0.03 ms on an Apple M5 Pro (targets 100 ms and 50 ms). Still to measure on phones: cold start, memory on 2 GB, battery under 1 % a day.
- [ ] Every miss filed with its health log and fixed or documented.

## Phase 8 · Release

**Outcome.** On Google Play, staged.

Licences
- [ ] Dependency report drafted in `context/spec/05-licences.md` 2026-11-27 (licences read from POMs, not licence texts); lawyer review open (Q6).
- [ ] Engine notices in the app (LGPL, GPL-3.0, NOTICE and Apache texts ship as assets and show on the Licences screen, 2026-11-27, ADR-13) and repository; modified engine source public. Lawyer's reading of LGPL 4(b) open (Q6); Apache notices for Kotlin and kotlinx-datetime not yet shown.
- [ ] `NOTICE` credits upstream for ported code.
- [ ] Adhan recording licence in writing (Q5). District data licence recorded (Q4). District data licence recorded 2026-11-28 (Wikidata CC0; Open-Meteo licence ASSUMED CC BY 4.0, credit still to add). Adhan candidates with stated licences listed in `06` section E; none chosen.

Content
- [ ] Reviewer's signed note for the shipped content version.
- [ ] Native-speaker review and tone review complete.

Privacy and policy
- [ ] Privacy page: English draft in `context/spec/06-release-drafts.md` 2026-11-27 with placeholders for developer name, support email, URL, date; Bengali must come from the native speaker.
- [ ] Play Data safety form; exact-alarm and foreground-service declarations [ADR-4]: ASSUMED drafts in `06-release-drafts.md` 2026-11-27; Play Console forms never read. Policy pages read 2026-11-28 and quoted in `06` (exact alarms, foreground service, Data safety, closed testing); two release risks opened (G51, G52). Forms themselves not filled.
- [ ] Side-by-side review against upstream: clearly a different product.

Testing
- [ ] Every `T-*` green in CI; every `M-*` passed.
- [ ] Closed test: 12 testers for 14 continuous days, if the account type requires it (G14).

Listing and rollout
- [ ] Title and descriptions in both languages; screenshots from the release build.
- [ ] Upload key generated, kept offline, backed up; Play App Signing on. Build side ready 2026-11-28: release signs only with the four `MUMINS_UPLOAD_*` environment values; `tools/release.sh` makes the store bundle with both content files required.
- [ ] Staged rollout 10 → 50 → 100 %, 48 hours each **(target)**, watching the report inbox and Play vitals.

---

## Part 2 · Features after v1 (phases 9 to 17)

Written 2026-11-28 at the founder's request ("we also need to add all of these"). Same rules as the
rest of this file: a box is ticked only when verified, ids tie items to invariants and
decisions, and nothing here may break a v1 rule without an ADR and the founder's yes.

### How the order was chosen

1. **v1 ships first.** Phases 7 and 8 are not skipped. Phases 9 to 13 need no new permission
   and almost no new people, so they can be built while v1 waits on the reviewer and phones,
   but they ship in an update after v1, not inside it.
2. **No network until it cannot be avoided.** Quran text, translation, duas and hadith are
   small enough to bundle (ASSUMED sizes below; measure). Only recitation audio and long
   tafsir are too large, so `INTERNET` arrives last, in phase 16, for one host, with its own
   ADR, privacy page and Data safety change. If the founder prefers, phase 16 can stay a
   separate, optional download app, keeping the main app permission-free (decide in phase 9).
3. **One content platform, not five.** Deeds already have a review gate whose provenance
   check knows nothing about deed text (ADR-10). Duas and hadith reuse it; the Quran gets an
   integrity check instead, because its text must be exact and unmodified, not reviewed.
4. **Content blocks code.** Every content feature names the licence and the person it needs
   in the founder track below. Code without the content is built on obviously fake records,
   as with deeds, and the store build refuses to ship fake records.

| Phase | Feature(s) | Needs from people | New permission |
|---|---|---|---|
| 9 | Foundations: navigation, content kinds, decisions | Founder decisions | None |
| 10 | Home-screen widgets; Salah tracker; "find my district" | Founder yes on location | Location (optional, ADR-12 reversal) |
| 11 | Hijri date, ±1 day | Founder: which authority | None |
| 12 | Zakat calculator (bhori, ana) | Reviewer signs the rules | None |
| 13 | Daily duas (Hisnul Muslim); daily hadith | Text licences; reviewer | None |
| 14 | Quran: Arabic and Bengali text offline; reader; verse search | Translation permission in writing; Arabic font licence | None |
| 15 | Khatam planner | None | None |
| 16 | Tafsir and recitation audio as downloadable packs | Tafsir and audio permissions; a host | `INTERNET`, one host |
| 17 | Proof and release of the update | Phones; Play | None |

---

## Founder track, part 2

- [ ] Decide: after v1 ships, which phases form the first update (suggested: 10 to 12).
- [ ] Decide: is network ever allowed in the main app (phase 16), or do audio packs live in a separate app? [ADR-6]
- [ ] Decide: bring back "find my district" with coarse location (reverses ADR-12) [INV-P2, INV-P3].
- [ ] Hijri authority: whose announcement decides the month (ASSUMED candidates: the National Moon Sighting Committee convened by the Islamic Foundation; confirm) [G33].
- [ ] Zakat: reviewer signs the rules (nisab basis gold or silver, rate, what counts as wealth, the hawl), and the bhori and ana conversions [F-Zakat].
- [ ] Duas: the source text (Hisnul Muslim, Arabic) and a Bengali translation, each with a written licence; the reviewer for selection and wording.
- [ ] Hadith: a source collection whose terms allow offline redistribution, a Bengali translation with a licence, and the reviewer for selection and grading.
- [ ] Quran: an Arabic text source with its terms (ASSUMED: Tanzil Uthmani, which permits verbatim redistribution with credit; fetch and confirm) and a **Bengali translation with written permission** (KNOWN from earlier research: Tanzil's Bengali texts are non-commercial only; QuranEnc requires unmodified text with credit).
- [ ] Quran: an Arabic Uthmani font with a licence that allows bundling (candidates and licences UNKNOWN; research in phase 14).
- [ ] Tafsir: a Bengali tafsir with written permission (phase 16).
- [ ] Recitation: at least one reciter's audio with written permission, and a host for the packs (phase 16).
- [ ] A reviewer for every new content kind; may be the same person as for deeds (G9).

---

## Phase 9 · Foundations for part 2

**Outcome.** The app can hold more than four features without a navigation library, and the
content gate takes more than one kind of record.

**Exit.** Decisions recorded as ADRs; the gate generalised with tests; v1 behaviour unchanged.

- [ ] ADR-17: release order and the network question (founder track, first two items).
- [ ] Navigation: a home screen with one entry per feature, platform Views, no library (ADR-11); back behaviour tested.
- [ ] Content kinds: `checkProvenance` reused by a `ContentKind` per type (deed, dua, hadith); one rotation helper shared; existing deed tests unchanged [INV-C1, ADR-10].
- [ ] Gate task per kind in `tools/content-gate`, each required in the store build only when that feature ships.
- [ ] Storage plan: one SQLite database for user data (tracker, khatam); read-only bundled databases for Quran and hadith; numbered migrations continue [ADR-5].
- [ ] Size budget per feature, measured against the 15 MB total (INV, `01` constraints).
- [ ] New invariants written into `spec/01` before their code: tracker tone (INV-C4 extended), Quran integrity (INV-Q-text), Hijri attribution (INV-H1).

## Phase 10 · Widgets, Salah tracker, "find my district"

**Outcome.** The next prayer at a glance on the home screen; a private, guilt-free record of
prayers; an optional "find my district" button.

**Widgets** (platform `AppWidgetProvider`, no new dependency)
- [ ] Next-prayer widget and a five-times widget, Bengali digits [INV-L2].
- [ ] Read the planner and clock only; the widget computes nothing [spec 03 forward path].
- [ ] Updated from the alarm path's replan and at each prayer, never by a polling job; no new permission [INV-P3].
- [ ] Shows "—" and a warning state when reminders are Degraded [INV-S6, INV-T7].
- [ ] Tests: the widget's view model as a pure function; instrumented check on an emulator.

**Salah tracker**
- [ ] Table `salah_log(local_date, salah, prayed_at)`, one migration, backed up with deed days [ADR-5, G41].
- [ ] "Prayed" action on the prayer notification, built by the one notification builder.
- [ ] Manual tick on the home screen for today and the past 7 days.
- [ ] Week view like the deed's: dots only. No counts, streaks, percentages or "missed" wording anywhere [INV-C4].
- [ ] Never prompts about a prayer not marked; never a notification about the tracker.
- [ ] Tests: tone rules as a string check over every tracker string; date attribution across midnight and travel.

**Find my district** (only with the founder's yes)
- [ ] ADR reversing ADR-12 for `ACCESS_COARSE_LOCATION` only; allowlist edit [INV-P3].
- [ ] Read once while the district screen is open; never in the background; nothing stored but the chosen district [INV-P2, T-INST-11].
- [ ] Nearest district from the 64 headquarters points; outside the bounding box or more than 50 km away asks before changing [F1#6, F1#7].
- [ ] Privacy page and Data safety draft updated.

## Phase 11 · Hijri date, ±1 day

**Outcome.** The Hijri date on the home screen, following Bangladesh's announcement, with a
one-tap ±1 day correction.

- [ ] Vendor the engine repository's `hijri` module (Apache-2.0, KNOWN from its POM; depends only on kotlinx-datetime, already allowed) with a MODIFICATIONS note if changed [ADR-2 pattern].
- [ ] `clock` gains `hijriDate(localDate, adjustment)`; adjustment is -1, 0 or +1, set by the user [spec 03 forward path].
- [ ] The Hijri day changes at Maghrib, not midnight (ASSUMED convention; reviewer confirms) [INV-H1].
- [ ] Optional: Ramadan mode suggests itself when the Hijri month is Ramadan, but still waits for the user's tap (G33 ruling stands).
- [ ] Formatting only in `l10n`; Bengali month names from the native speaker.
- [ ] Tests: against a published table of Hijri month starts for Bangladesh (source to fetch), and the ±1 property.

## Phase 12 · Zakat calculator

**Outcome.** A pure, offline calculator in local units that shows its working.

- [ ] Pure `zakat` package in core: inputs in taka, bhori, ana, ratti and grams; outputs nisab, whether it is reached, and the amount.
- [ ] Conversions fetched from a source and recorded (ASSUMED until then: 1 bhori = 11.664 g, 1 bhori = 16 ana, 1 ana = 6 ratti) [data rule].
- [ ] Gold and silver prices typed by the user, with the date; the app never fetches them (no network) and says so.
- [ ] Rules exactly as the reviewer signs them; the reviewer's note committed beside the rules, like deeds [INV-C1 pattern].
- [ ] Shows every step of the working; no rounding against the payer's obligation.
- [ ] Property tests: unit round trips, monotonicity, nisab boundary.

## Phase 13 · Daily duas and daily hadith

**Outcome.** A short reviewed dua and a reviewed hadith each day, with source and grade,
through the same gate as deeds.

- [ ] Record shapes for dua (Arabic, transliteration, Bengali, English, source, occasion) and hadith (Arabic, Bengali, English, collection, number, edition, grading).
- [ ] Gates per kind, reusing provenance; store build requires the approved files [INV-C1, INV-C3].
- [ ] Arabic shown right-to-left inside Bengali screens with isolates [INV-L3].
- [ ] Rotation per kind, same rules as deeds (stable for the day, no repeats within a cycle) [INV-C2].
- [ ] Optional nudge per kind? Decide; default off, at most one notification a day across all content kinds.
- [ ] Fake records in debug only, like deeds.

## Phase 14 · Quran: text offline, reader, verse search

**Outcome.** The whole Quran in Arabic with a Bengali translation, readable and searchable
with the radio off.

- [ ] Fetch the chosen Arabic text and translation; record source, version, licence and hash (founder track).
- [ ] Bundled read-only SQLite database built at build time from the source files; the build fails if the text's hash differs from the recorded one (INV-Q-text: the Quran text is never modified).
- [ ] Size measured against the budget (ASSUMED: text and one translation under 5 MB with an index; measure).
- [ ] Reader: surah list, ayah view, Arabic in the licensed Uthmani font, translation below, last position remembered.
- [ ] Verse search with SQLite FTS: Bengali and English words, Arabic with and without diacritics, surah:ayah lookup.
- [ ] Credits and licence shown on the Licences screen and with the text as the licence requires.
- [ ] Tests: every surah and ayah count matches the source; search finds known verses; the text round-trips byte for byte.

## Phase 15 · Khatam planner

**Outcome.** A plan to finish the Quran by a date the user picks, with gentle progress.

- [ ] Table for the plan and progress (by juz, page or ayah: decide), one migration.
- [ ] Daily portion computed from today to the target date; recomputed, never "behind" language [INV-C4].
- [ ] Optional single daily reminder through the existing nudge path, off by default.
- [ ] Opens the reader at today's portion.
- [ ] Tests: portions sum to the whole Quran; date changes and travel; finishing early.

## Phase 16 · Tafsir and recitation audio as downloadable packs

**Outcome.** Optional downloads, verified and stored on the device, without weakening the
privacy promise for anyone who never downloads.

- [ ] ADR reversing ADR-6 for one host only, with the founder's yes; or the separate-app option.
- [ ] `INTERNET` added; allowlist edit; T-BUILD-1 changed to "no host other than the pack host is ever contacted", tested [INV-P1 replaced by a written new invariant].
- [ ] Pack manifest bundled in the app (names, sizes, SHA-256); a downloaded pack is used only if its hash matches; no other request is ever made.
- [ ] No analytics, no identifiers in requests, no request without a user tap.
- [ ] Downloads resume, can be cancelled, and can be deleted; storage shown in Settings.
- [ ] Audio player for recitation with verse sync (if the source provides timings).
- [ ] Privacy page and Data safety form updated before release (`spec/06`).

## Phase 17 · Proof and release of the update

- [ ] Every new invariant tested in CI; the emulator suite extended for widgets, tracker, Quran reader.
- [ ] Device matrix pass for the new screens, including Arabic rendering on every phone [INV-L3].
- [ ] Size, cold start and memory re-measured with the Quran database.
- [ ] Native-speaker and reviewer passes for all new strings and content.
- [ ] Play listing, privacy page and Data safety updated; staged rollout as for v1.
