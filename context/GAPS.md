# Gaps in the spec

Found on 2026-11-26 while setting up the repository. Labels as in `spec/00-base-audit.md`.
A closed gap keeps its row with the resolution and date.

## Blocking: settle before slice 1

### G1 · The unmodified engine cannot satisfy INV-T5 or INV-T7

- **KNOWN.** At the pinned commit `a136f9b`, `CalcTimes.getPrayerTimes` returns `PrayerTimes`,
  whose fields are already-rounded `LocalTime`s. Fajr is floored. The unrounded values live in
  `DoubleTimes`, which is `internal`, as is `getDoubleTimes`
  (`calctimes/src/main/kotlin/dev/metinkale/calctimes/DoubleTimes.kt`, `CalcTimes.kt:22-27`,
  fetched from raw.githubusercontent.com).
- **Consequence.** A wrapper outside the engine cannot produce "Fajr begins, rounded up"
  (INV-T5): when Fajr falls exactly on a minute the right answer is that minute, otherwise the
  next one, and the wrapper cannot tell which. It also cannot tell a real `00:00` from the
  `NaN` sentinel (INV-T7). ADR-2 says "unmodified"; these two invariants need the raw values.
- **Options.**
  1. Fork the engine, make `getDoubleTimes` public, publish the fork (LGPL allows this and
     ADR-2 already says "if a change is ever needed, publish it"). Smallest honest change.
  2. Keep it unmodified and approximate: show Fajr-begins as floored Fajr + 1 minute (never
     early, up to a minute late), and treat `00:00` as "not computable" (safe in Bangladesh,
     where no prayer falls at midnight: ASSUMED).
  3. Replace the engine (ADR-2 option b or c), which also removes the LGPL question Q6.
- **Closed 2026-11-26.** Founder chose option 1. ADR-2 amended; engine vendored at `engine/calctimes/` with the one change.

### G2 · Two documents are referenced but missing

- **Open questions.** Q1 to Q8 are cited 29 times and defined nowhere; Q7 is never cited.
  `QUESTIONS.md` holds a reconstruction.
- **Slice plan: closed 2026-11-26**, see `PLAN.md`. ADR-1 has a fallback "if slice 1 takes more than two weeks" and `00` §9 says
  "after the first slice builds". No document says what the slices are.
- **The Sheet.** `02` cites "Sheet #n" findings from a project Sheet that is not in the
  repository. The user evidence for every feature lives there.

### G3 · The engine is not a published artifact

- **KNOWN.** Upstream clones the engine at a pinned commit and publishes it to `mavenLocal()`
  as `1.0-SNAPSHOT` (`00` §1). There is nothing to depend on by coordinates.
- **Needs.** Decide how this repository gets it: git submodule at the pinned commit built as
  an included build, or a vendored copy with its notices. This also decides how G1 option 1
  would be done. INV-P4's allowlist check must know about it.
- **Closed 2026-11-26.** Vendored copy with its notices (ADR-2 amendment).

## Design holes: settle before the feature they touch

### G4 · "Show me another" collides with tomorrow's deed (F4)

The rotation is `index = daysSinceEpoch mod N` and "another" is `index + 1`. Tomorrow's card
is also `index + 1`. A user who swaps today sees the same deed again tomorrow, which breaks
failure mode 6 ("every deed once before any repeats").

**Closed 2026-11-26: founder accepted.** "Another" shows the deed half a cycle away:
`(index + N / 2) mod N`. One swap a day. No stored sequence state, and every phone still
agrees. The skipped deed simply returns on its next turn. Cost: the alternate is a deed last
shown about N/2 days ago and due again in about N/2 days, so F4 failure mode 6 becomes "the
daily deed never repeats within a cycle; an alternate, shown only on request, is the deed
farthest from today". Needs N ≥ 4. Rejected: exchanging with tomorrow (strictly no repeats,
but the deed the user just declined comes back the next morning, and again if declined again);
a per-phone skip counter (phones drift apart for good, against INV-C2).

### G5 · A content update can change today's card (F4)

The spec says a change to N "never changes today's card once shown", but the card is a pure
function of date and content version (INV-C2) and nothing records which deed was shown. An
app update at noon changes N and so the card. Also, changing N shifts the modulo for every
future day, so "once before any repeats" does not survive a content update. Either persist
"shown today" or define the rotation over a stable ordering that an update extends.

**Closed 2026-11-26: founder accepted.**
1. Record the card the first time it is shown each day: one row per local date holding the
   deed id, the content version, whether it was swapped, and when it was marked done. This
   replaces `deed_done`. Today's card is the row if there is one, otherwise computed and stored.
   An update at noon cannot change it. One exception: if an update retires that deed, today's
   card is recomputed, because retired text must stop showing at once (failure mode 5).
2. Scope the promise honestly: "every approved deed once before any repeats" holds within one
   content version. When an update changes N, the position in the cycle jumps once; a few deeds
   come round early or late that cycle. Content updates ride on app updates, so this is rare.
   INV-C2 becomes: the deed first computed for a date is a pure function of the date and the
   content version; once shown it stays.
3. Rejected: carrying a per-phone position across updates (phones that update on different
   days would never agree again); an anchor date per content version (still only approximate,
   since phones update on different days, for more moving parts).
4. Opens Q9: how many deeds at launch. The build gate should refuse fewer than a stated
   minimum; 4 is the mathematical floor, 30 would give a month without a repeat.

### G6 · Stopping the Adhan when notifications are blocked (F1)

F1 failure mode 4 says audio must still play with `POST_NOTIFICATIONS` denied. Then there is
no visible notification to carry a Stop action. The spec does not say how the user silences
it (volume key, opening the app, a lock-screen surface). UNKNOWN what the platform shows for
a foreground service in that state on Android 13+; test on D6.

**Partly answered 2026-11-26 (KNOWN, developer.android.com/develop/ui/views/notifications/notification-permission).**
"Apps don't need to request the POST_NOTIFICATIONS permission in order to launch a foreground
service", and with the permission denied users "still see notices related to foreground
services in the Task Manager but don't see them in the notification drawer". So playback can
start. Whether the user can stop it from the Task Manager is not on that page (UNKNOWN; fetch
the "handle-user-stopping" page). The app still needs its own stop control: still open.

**Further answered 2026-11-27 (KNOWN, developer.android.com/develop/background-work/services/fgs/handle-user-stopping).**
On Android 13+ the user can press Stop beside the app under "Active apps" in the notification
drawer. That removes the app from memory, stops media playback and removes the service
notification; "Alarms go off at their scheduled time or time window", so later prayers still
ring. The app gets no callback; on next start it can read `REASON_USER_REQUESTED` from
`ApplicationExitInfo`. Test with `adb shell cmd activity stop-app <package>`. So with
notifications blocked the user has two stops: the Task Manager (Android 13+) and the app's own
control. Still UNKNOWN: whether audio keeps playing with `POST_NOTIFICATIONS` denied (the page
is silent; test on D6). **Decision for the alarm path (ASSUMED, to confirm on a device):**
the playback service always posts its notification (platform requirement), carries a Stop
action, and opening the app also stops playback.

### G7 · Starting playback from the background (F1)

The design starts a `mediaPlayback` foreground service from an alarm. Android 12+ restricts
foreground-service starts from the background and exempts exact alarms (ASSUMED from general
knowledge; not fetched). The inexact fallback path, and silent/pre-reminder alarms, may not
have the exemption. Fetch the current rules for target SDK 37 before writing `shell/alarm/`.

**Answered 2026-11-26 (KNOWN, developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
and .../services/fgs/service-types).**
- Background start is allowed when "Your app invokes an exact alarm to complete an action that
  the user requests." The page does not say which exact-alarm methods qualify (UNKNOWN; test
  `setAlarmClock` and `setExactAndAllowWhileIdle` on a device). An inexact alarm is not named,
  so the inexact fallback cannot start playback: one more reason INV-S6 forbids it.
- `mediaPlayback` needs `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and has no runtime prerequisite.
- "Apps that target Android 15 or higher are not allowed to launch a media playback
  foreground service from a BOOT_COMPLETED broadcast receiver." **Design rule:** the boot
  receiver only replans; it never starts playback.
- Re-read 2026-11-27: the exemption is conditional on the alarm "completing an action that the
  user requests"; whether `setAlarmClock` and `setExactAndAllowWhileIdle` both qualify is still
  UNKNOWN, to be measured on the emulator (T-INST). The Android 15 page
  (developer.android.com/about/versions/15/behavior-changes-15) lists `mediaPlayback` among
  the types a `BOOT_COMPLETED` receiver may not launch, and says it throws
  `ForegroundServiceStartNotAllowedException`. The restrictions page words the target-SDK
  condition as "14 or higher", the Android 15 page as "15 or higher": we target 37, so both apply.

### G8 · What happens after 24 hours with no trigger (F1)

INV-S1 holds a 24-hour window, refilled on each trigger. If every trigger is lost for a day
(OEM freezes the app after the last alarm), the window empties exactly as the base's chain
does, one day later. The spec should say this plainly and decide whether the midnight replan
alarm is enough or the window should be 48 hours (the constant in ADR-3).

**Closed 2026-11-26.** Founder chose 48 hours. ADR-3 amended, INV-S1 reworded.

### G9 · Reviewer and native speaker are roles without names

INV-C1, INV-C4, Q3 and the release checklist all depend on "the reviewer" and "a native
speaker". No person, no agreement, no turnaround time. F4 cannot ship a single card without
them, and F4 is the answer to the Play "repetitive content" risk.

## Missing, smaller

| Id | Missing | Why it matters |
|---|---|---|
| G10 | Repository `LICENSE` and `NOTICE`. **Application id: closed 2026-11-26**, founder chose `com.senseix21.muminsday` (typed with a comma; read as a dot) | Needed before the first file of code; the id cannot change after Play release |
| G18 | **Closed 2026-11-26: split, ADR-9.** Where the pure core is built. `dl.google.com` is blocked in the cloud container, so an Android module cannot build there; Maven Central and the Gradle plugin portal are reachable (KNOWN, `curl` on 2026-11-26). The spec says one Gradle module | Either split the pure core into a plain Kotlin module that builds anywhere, or do all building on the founder's machine |
| G11 | **Remote: closed 2026-11-26** (`github.com/senseix21/mumins-day`, public). **CI: closed 2026-11-26**, first run green. A remote for this repository, and CI | The spec leans on CI for the build gates; none is chosen. This checkout lives in a temporary cloud container |
| G12 | UI design: screens, flows, Bengali copy | Upstream asks for a real redesign and Play checks for it; the spec lists screen names only |
| G13 | **Build machine: closed 2026-11-26** (Android SDK in the `mumins-day` cloud environment). Phones still open. A build machine and physical phones | Nothing was ever built (H8); every target in the spec is unmeasured |
| G14 | Play developer account type and its date | Decides whether the 12-tester, 14-day closed test applies |
| G15 | Privacy policy text and a support email | Play listing needs both; "Send a report" needs the address |
| G16 | **Closed 2026-11-26: skip silently; see ADR-3 amendment.** Rule for a prayer that is already past when the user enables reminders, and for two alarms within a minute of each other (pre-reminder vs Adhan) | Planner edge cases not stated |
| G17 | Numbering slips: `INV-T7` sits before `INV-T6` in `01`; test ids skip (no T-PROP-2, 4, 5; no T-UNIT-1, 3, 4, 6, 7, 8) | Harmless, but ids are the traceability key; fix or note as intentional |
| G19 | INV-T2 and INV-T6 pull against each other. T2 says `sehriEnd == fajr − margin`; T6 says an offset moves one prayer and nothing else. A Fajr offset would have to move Sehri end too, or break T2. Slice 1 takes the safe reading: Sehri end follows the *calculated* Fajr and has its own offset; the user's Fajr offset does not move it. Also, with Sehri end rounded down and Fajr rounded up (INV-T5) the two can show one minute apart at margin 0 | Reviewer to confirm with Q3 |
| G20 | Sunrise rounding is not stated in INV-T5. Slice 1 rounds it down, as the end of the Fajr window | Reviewer to confirm with Q3 |
| G21 | **Closed 2026-11-26: keep as built for v1.** The clock reports only the first prayer it cannot compute, so the planner drops every alarm for that day, including prayers that were computable. Follows INV-T7 as written; never happens inside Bangladesh | Decide before any use outside Bangladesh; the app must show the gap's date |
| G22 | Alarm ids assume a 48-hour window touches at most four local dates. True for every one-hour daylight-saving change (tested in nine zones); false only where a zone skips a whole day, as Samoa did in 2011 | Out of scope for Bangladesh; note for the forward path |
| G23 | The spec does not say whether `retired` or `draft` records may sit in `deeds.json`. Taken as: retired may stay (complete, with `retired_in`, never shown); draft fails the build. Written into INV-C1 | Founder or reviewer to object if wrong |
| G24 | **Closed 2026-11-26: ADR-10.** Who parses `deeds.json` for the build gate. `:core` has no JSON parser and may not add one; the app uses the platform's. The gate (T-BUILD-4) needs a build-time parser: Gradle's bundled one in a build task is the likely answer | Decide when the gate task is written (phase 5) |
| G25 | **Closed 2026-11-27: ADR-11, platform Views.** The dependency allowlist in the spec lists a handful of libraries, but it is checked transitively. Today six ship (the spec's four plus `org.jetbrains:annotations` and the desugaring configuration). Compose and Material 3 will add dozens, and AndroidX core adds its own permission to the merged manifest | Before phase 4: an ADR that accepts the real transitive list, or a decision to build the UI with platform Views |
| G26 | **Closed 2026-11-27: silent mode is a setting, default respect silent mode (spec default); `audioUseFor` JVM-tested; not heard on a phone.** Silent-mode choice (F1 #15) has no setting. Playback uses `USAGE_ALARM`, which ignores silent mode, the opposite of the spec's default "Respect silent mode". Found in the alarm path, 2026-11-27 | Founder: pick the default; add the setting in phase 4 |
| G27 | **Closed 2026-11-27: the brand-steps flag has its own `device` prefs file; `settingsEdits` no longer wipes other keys; `AppPrefsTest`.** `BrandSteps.Confirmed` is never produced: `SettingsStore.write` clears the file, so a stored "I did the steps" flag would be wiped, and nothing stores it. `brandNeedsSteps` covers only Xiaomi, Realme and Infinix (the spec's three); other brands UNKNOWN | Decide where the flag lives (phase 4); extend the brand list from the device matrix |
| G28 | `Outcome.RingFailed` (service start refused) is stored, but `assessHealth` reads only `deliveredAt`, so a failed ring counts as on time. Never seen to happen on a device | Decide whether health counts it; change `core/health` with a test first (INV-S6, INV-S8) |
| G29 | Two alarms at the same instant: the replan after the first delivery keeps only alarms strictly after now, so the second is dropped. ADR-3 anticipates it without resolving it | Decide: keep strictly-after, or keep alarms at exactly now that were not yet delivered |
| G30 | Pre-reminders are delivered as silent notifications; the spec does not say | Founder to confirm (F1) |
| G31 | **Closed 2026-11-27: Home shows a Stop button while playing; seen on the emulator.** If the app is already in the foreground when an alarm rings, `onResume` never fires, so only the notification's Stop action silences the Adhan | Handle in phase 4: stop playback when the foreground app receives the delivery, or add an in-app Stop |
| G32 | **Per-brand battery guidance (phase 4), read 2026-11-27 from dontkillmyapp.com** (community site; no page dated except Samsung; it says "very hard to keep up with all the changes"). Facts, English only, for the native speaker to turn into steps. **Xiaomi (MIUI):** lock the app in recents; Settings > Apps > app > App permissions > Background autostart (MIUI 14); Security > Battery > App Battery Saver > app > No restriction. HyperOS UNKNOWN. **Oppo/Realme (ColorOS):** App info > Allow Auto Start-up; App info > Battery usage > Run in background; realme: Battery > Power saving settings > App battery management (allow auto-launch, foreground, background; optimise off); Apps > Special access > "Display pop-up windows of applications running in the background" on for alarm apps; avoid power-saving modes. The one component named: `com.coloros.safecenter` / `com.coloros.safecenter.permission.Permission` (kind of component not stated; UNKNOWN). **Tecno/Infinix (XOS):** Settings > Battery Lab > Battery Saving Settings > disable power saving management for apps; Phone Master > Toolbox > Auto-start management on; lock in recents. The `/infinix` page is 404: Infinix steps UNKNOWN. **Samsung:** Samsung says One UI 6.0 guarantees foreground services of apps targeting Android 14+ work as intended (07/2024, unconfirmed); still advised: battery optimisation off, "Put unused apps to sleep" off, app not in Sleeping/Deep sleeping lists (unused apps stop starting from background after 3 days). **No deep-link intents are given for any brand**, so the app can show steps but cannot promise a deep link; treat any intent as UNKNOWN until tried on a device (D1..D6). Each vendor's developer section says "No known solution" | Native speaker writes the steps (G9); steps checked on real phones (Q8) before M-4 |
| G33 | **Closed 2026-11-28 (Claude's ruling, founder to confirm): a user-switched Ramadan mode. Amended 2026-11-28 (phase 11): Home may suggest it when the calculated Hijri month is Ramadan, but only the user's tap turns it on; the calculated date is labelled as such (ADR-20, G54).** Ramadan emphasis (F2 #5, #6: Sehri end and Iftar largest) needs a Hijri calendar and the Bangladesh moon-sighting rule; core has neither. A computed Hijri date can be a day off from the national announcement | Founder: which source decides Ramadan's first day (Q-new); no emphasis is shown until then |
| G34 | **Closed 2026-11-27: the alarm log has its own file `alarm-log.db`, deeds `deed-days.db`; backup includes only the deed file; the old `mumins-day.db` is deleted once (no release had shipped).** Deed completions share one SQLite file with the alarm log, and backup rules cannot split a file by table. Including the database would restore stale log rows that health reads as "not arrived"; excluding it loses deed completions. Found 2026-11-27 | Move the alarm log to its own database file before phase 5 ships deed completions |
| G35 | **Closed 2026-11-28 (Claude's ruling, founder to confirm): built on alarm slot 10, the same exact path as pre-reminders, off by default, not logged.** Daily deed nudge (F4, optional, off by default) needs a new alarm kind: slot 10 of the closed `AlarmId` space, a change to `PlannerSettings` and `AlarmSpec`, amended ADR-3/INV-S4 tests, a choice between the exact path and an inexact alarm (`USE_EXACT_ALARM` has an open Play policy question, ADR-4) and a rule for "all prayers Off". Not built | Founder: allow it, and on which path |
| G36 | T-INST-3 failed once in one full `tests/inst/run.sh` (zone event fired, queue read empty 4 s later) and passed alone and in two later full runs. Cause not found (ASSUMED timing) | Re-run in the next suite; if it recurs, add a wait-until instead of a fixed sleep |
| G37 | The G7 "audio plays with notifications denied" check read `AudioTrack=1` in one run and `0` in a later one, while `playback_started=1` both times. The emulator is headless, so audio is inferred, not heard | Listen on a phone (D6) |
| G38 | Qibla number: the screen shows the true bearing first (Dhaka 278 degrees, "8 degrees north of west") and a muted magnetic-compass line second (also 278 at Dhaka, the correction being about -0.6). INV-Q1 says "corrected for declination", which is ambiguous | Founder: confirm which is the main number |
| G39 | Qibla sensor limits are ours: trusted only at High accuracy (INV-Q2 literal; many phones sit at Medium, so warnings may be common), field 30 to 60 microtesla as hard limits, tilt 25 degrees, filter 200 ms. All (target) until M-3 on a phone | Measure on devices (Q8); loosen only with evidence |
| G40 | `screenOrientation=portrait` was removed from `QiblaActivity` (lint, warnings as errors; Android 16 ignores it on large screens); axes are remapped for screen rotation, tested only upright | Test rotation on a phone |
| G41 | **Closed 2026-11-27: kept and disclosed by the founder; privacy draft updated.** **Backup contradiction (found 2026-11-27).** `STATE.md` said backup off; the manifest has `allowBackup="true"` with rules that back up `settings.xml` and `deed-days.db` (phase 4, G34). STATE is corrected. A "no data collected" Play answer has to account for Android's own backup of the user's settings and deed completions | Founder: keep and disclose, or switch backup off |
| G42 | **Closed 2026-11-27: both permissions removed, ADR-12; gate passes with 7.** `ACCESS_COARSE_LOCATION` and `VIBRATE` are declared but nothing calls a location or vibrator API (location is not built; no vibration setting). Play review may question both. Removing either needs an ADR (INV-P3) | Founder: keep for the planned location feature and vibration, or remove |
| G43 | **Closed 2026-11-27: the four licence texts ship as assets and show on the Licences screen, ADR-13; the lawyer still has to say whether this satisfies LGPL 4(b) (Q6).** The LGPL licence text is not in the APK: `unzip -l` shows only `META-INF/LICENSE.GPL-3.0`; the engine jar's `META-INF/LICENSE` and `NOTICE.txt` are dropped by default packaging excludes, and the Licences screen only names repository files | Fix in the app (small) and put to the lawyer (Q6) |
| G44 | **Closed 2026-11-27: desugaring removed, ADR-14; lint and all tests pass at minSdk 26; not run on a real API 26 device.** `desugar_jdk_libs` is GPLv2 with the Classpath Exception (its POM); it is not in `NOTICE` or on the Licences screen. With minSdk 26 it may be removable | Try dropping `coreLibraryDesugaring`: needs an ADR and the founder's yes |
| G45 | **Closed 2026-11-27: R8 and resource shrinking enabled, ADR-15; minified release smoke-tested on the emulator, Adhan playback in a minified build not tested.** R8 and resource shrinking cut the release APK from 1,034,164 to 148,644 bytes in a trial, then were reverted: size is already far under the 15 MB target and the minified build was never run | Founder's yes, then a release smoke test on an emulator and phones |
| G46 | **Closed 2026-11-27: PR #1 ran `./gradlew build` on GitHub Actions (run 46036080102, ubuntu-latest, JDK 17): success. The log shows 4 libraries, 7 permissions, 152 strings and `:app:testDebugUnitTest` running.** CI has not run the app tests: the last green run (`cd502e7`) shows `:app:testDebugUnitTest NO-SOURCE`; all work since is uncommitted | Commit and push when the founder asks; read the first CI result |
| G47 | On 2026-11-27 `tests/inst/qibla.sh field` failed all four checks once because a "System UI isn't responding" dialog covered the emulator after hours of runs; it passed 7 of 7 after dismissing it. Scripts read the screen, so they fail on any system dialog | Scripts should dismiss system dialogs or restart the emulator before a run |
| G48 | **Closed 2026-11-28 by ADR-16, reviewer to confirm.** The Foundation's permanent Dhaka timetable explains it: Dhuhr and Maghrib carry a 3-minute precaution, so the Ramadan table's Iftar matched only when the point moved west. **Reference point (found 2026-11-27, `DhakaTableComparisonTest`).** Against the Dhaka Ramadan 2026 table that follows the Islamic Foundation (secondary copy, https://kivabe.com/ramadan/en/?zilla=1): with Karachi and Hanafi Asr the engine reproduces the table's Iftar on 30 of 30 days and its Fajr within one minute, at a reference longitude of 90.2 E. At the usual city centre (23.8103, 90.4125) the engine is 1 to 2 minutes earlier on Fajr (mean -1.55) and 0 to 1 minute earlier on Iftar (mean -0.90): if the table is right, that is the unsafe direction for Iftar (INV-T5). So the method matches; the coordinates differ. A district's coordinates therefore decide the times to the minute, and the district list (Q4) must be reconciled with the Foundation's per-district tables, not taken as geographic centres | Founder: whether "Bangladesh-correct" means the Foundation's reference points (then fit each district to its table) or true coordinates (then accept up to a minute's difference); the Foundation's own tables (Q2) |
| G49 | **Closed 2026-11-28 by ADR-16, reviewer to confirm.** The permanent table puts Sehri end about 5 minutes before the app's old Sehri end; the default is now 6 minutes before the calculated Fajr. The same table gives Sehri end as Fajr minus 3 minutes on 25 of 30 days (2 on 4 days; one day is a typo on the page). Our default `sehriMargin` is 0, so by default the app's Sehri end equals Fajr. Whether the Foundation's 3-minute gap is a margin the app should default to is for the reviewer (Q3) | Reviewer with Q3 |
| G50 | **Districts differ from the Foundation's flat offsets by latitude.** The Foundation times every district as Dhaka plus a fixed number of minutes. Against the engine at each headquarters point on 28 Nov 2026 (site snapshot of all 64 districts), table minus raw engine for Maghrib ranges from -5 (Cox's Bazar) to +11 (Panchagarh), and for sunrise from +10 (Cox's Bazar) to -10 (Panchagarh). The Foundation's own note warns its offsets can be off, "especially the Asr of the north". The app follows each district's own sun (ADR-16) | Reviewer and founder: accept that the app will not match flat-offset mosque timetables outside Dhaka, or add a "Foundation's district offsets" mode |
| G51 | Play's exact-alarm policy names only alarm or timer apps and calendar apps for `USE_EXACT_ALARM` (KNOWN, quoted in `06` C2). An Adhan app is not named | Founder: keep both permissions (ADR-4 as built) and argue "alarm app" in the declaration, or ship `SCHEDULE_EXACT_ALARM` alone on every version with the existing grant step |
| G52 | Play's foreground-service declaration says media playback "must be user-initiated" and asks for a video (KNOWN, quoted in `06` C3). The Adhan starts from an alarm | Founder: declare `mediaPlayback` with the set-up shown in the video, or move to `systemExempted` (an ADR, a manifest change and a device test) |
| G53 | **The "Prayed" action only exists on silent notices.** When the Adhan or tone rings, the only notification is the foreground playback one, which ends with the audio; a silent reminder, a respected silent mode, a phone busy on a call, or a late alarm leaves a notice that stays and carries "Prayed". So with Adhan on, a user marks on the tracker screen, not from the shade (KNOWN from the code, `alarm/Deliver.kt`, `Notifications.kt`). Found 2026-11-28 | Founder: accept, or add a short-lived notice after the Adhan ends (a F1 behaviour change and a device test) |
| G54 | **No authority for the Hijri month is decided.** Phase 11 built a calculated date only (ADR-20). It was one day from the one 2026 announcement tested and up to 2 days from Umm al-Qura. A table of announced month starts for Bangladesh was searched for and not found as a file; only per-month press reports exist (KNOWN: Dhaka Tribune, 2026-02-18). The Maghrib day change is ASSUMED | Founder and reviewer: Q10; if announced dates are wanted, a source and a way to get them into an offline app (a data file shipped in an update) |
| G55 | **Zakat conversions have only a secondary lead; every rule is the reviewer's.** students.bd (https://students.bd/calculator/vori-to-gram, read 2026-11-28) states 1 vori = 11.664 g = 16 ana = 96 ratti (6 ratti per ana) and attributes it to BAJUS without a link; the same site also lists 11.6638 g and an "approx 11.66" setting, so sources disagree (KNOWN: read on that page; not an official standard). It is a lead for the reviewer, not a value: it is in no code and in no file. The nisab weights, rate, what counts, debts, the year and the boundary are not researched at all (INV-C1) | Reviewer: Q11; a primary source for the conversions (BAJUS or a government standard) |
| G56 | **Today's dua and hadith are not stored.** Deeds keep the shown card in `deed-days.db` so a content update cannot change it the same day (INV-C2, G5); duas and hadith are recomputed from the date and the content each time the screen opens, so an app update that changes `duas.json` during a day can change that day's dua (KNOWN from the code, `ui/TodayActivity.kt`; ADR-22 item 4). Found 2026-11-28 | Founder: accept (content changes only with an app update), or add a table as for deeds |
| G57 | **The Quran database is built on the phone, and nothing about that has run on Android.** ADR-23: first open parses about 2.2 MB and writes 6236 rows plus an FTS4 index, then reads every ayah back. Time on a low-end phone, storage used, FTS4 queries on SQLite 3.18 (API 26), and memory during the build are UNKNOWN; the SQL ran only on desktop SQLite 3.54.1 (`fts_check.py` by hand, 2026-11-28) | Emulator pass (API 26 image if possible), then phones (phase 17) |
| G58 | **Search normalisation is Claude's choice, for the index only.** Arabic marks, tatweel and small letters are dropped; alef forms fold to alef and alef maksura to yeh; teh marbuta, hamza on waw and yeh are kept. Words are prefix matched, all required. Whether this matches how people in Bangladesh type Arabic is UNKNOWN. The displayed text is never changed (INV-QT1) | Reviewer or native users: try searches on a device; adjust `quran.indexWords` with a test |
| G59 | **Arabic rendering is unchecked.** Amiri Quran covers every code point of the text (measured), but whether stacked marks, the small high letters and the sajdah sign render well on Bangladeshi phones, and at 200 % font, is UNKNOWN (T-UI-3) | Look on the device matrix |
| G60 | **The reader does not show where a khatam portion ends.** "Open today's portion" opens `SurahActivity` at the first unread ayah; the end of the range is only on the khatam screen, and a portion that crosses surahs needs the user to open the next surah from the Quran list (ADR-24) | After a device look: a "portion ends here" line in the reader, or a "next surah" button, if users ask |
| G61 | **A khatam reminder needs the deed nudge to be on.** The mention rides on the one daily nudge (ADR-24, at most one content notification a day); with the nudge off there is no khatam reminder, and with the deed done and no portion waiting nothing is sent. A user who wants only a khatam reminder gets a nudge that also names the deed when the card is not done | Founder: accept, or allow the nudge to carry only the portion (still one notification) |
| G62 | **Packs cannot be downloaded in any build.** By design (ADR-25): no host, no `INTERNET`, empty manifest. The downloader was tested only on the JVM against a local server with the JDK's client; Android's `HttpsURLConnection` may add headers the JDK does not (UNKNOWN) | When the host is named: run `DownloaderTest`'s checks against the real host from a device, and widen or not the header list with evidence |
| G63 | **Recitation plays only while its screen is in front.** Background playback would need a foreground service, which raises the same Play question as the Adhan (G52) | Founder: whether recitation must continue with the screen off; if so, an ADR for the service type |
| G64 | **No storage check before a download.** A pack larger than the free space fails as a network-style failure (an `IOException`) and keeps its partial file | Before a real pack: compare the pack's size with `filesDir`'s free space and say so in words |
| G65 | **Zakaria's markup is shown as characters.** 322 footnotes and one translation in `bengali_zakaria` contain `<b>` and `</b>` (KNOWN, counted 2026-11-28). INV-QT1 and the task said "exactly the source string", so the reader shows the tags literally; QuranEnc's own site renders them bold. Rendering exactly those two tags as bold would change no character of the content but is an interpretation (ADR-26 item 4) | **Closed 2026-11-28:** founder said yes; `boldMarkup` (core) draws paired tags as bold, shows any other markup literally; property test that only tag characters go, and a test that every bundled tag pairs. Seen bold on the API 36 emulator. |
| G66 | **Zakaria makes the Quran large and the first open slower.** Its 114 API responses are 30.6 MB raw (every Bengali character escaped), 3.9 MB compressed in the APK; the Quran text allowance of ADR-17 (4 MB) is exceeded by about 1.1 MB. Every open hashes about 36 MB of assets, and the first open after install or update builds two translations' rows and indexes. Time on a low-end phone UNKNOWN | Measure first open and later opens on the slowest phone (phase 17). If too slow: hash once per install and keep the result, or ask QuranEnc for a file download of Zakaria (Q15) |
| G67 | **Hisnul Muslim is unreviewed, of unknown translation, and local only.** 312 duas sit in git-ignored `content/local/hisnul-muslim/` and in debug APKs (ADR-27). Nobody has reviewed any of it; the Bengali's translator is UNKNOWN (Q16); a fresh clone or CI has none of it, so the screen there says "not in this build". One note (dua 107, chapter 28) carries Latin digits and is shown as written, so `ui.sh` T-UNIT-9 would fail on that chapter in Bengali. The Arabic full stop U+06D4 is not in Amiri Quran (KNOWN, cmap read); Android draws it from a system font (ASSUMED; not seen) | Founder: settle Q16. Reviewer: read the collection or name a reviewed source; then it moves to the reviewed dua path (ADR-22) or a new ADR, and T-BUILD-7 is revisited |
| G68 | **The hadith books are unreviewed, of unknown translation, and local only.** 36,432 hadiths of 8 books sit in git-ignored `content/local/hadith/` and in debug APKs (ADR-28). Nobody has reviewed the texts or the grades; the Bengali translators are UNKNOWN (Q17); a fresh clone or CI has none of it. Source texts carry Latin digits (KNOWN: "[1]" footnote marks in Abu Dawud's Bengali), shown as written, so `ui.sh` T-UNIT-9 fails on such screens. Section names are English (the source's); some sections have none (shown as "no section name in the source"). Some numbers have no text in either edition (Muslim 167, an-Nasai 83 and others); the screen says so. The API's `section_details` ranges disagree with its own section files; the app follows the section files. Scrolling on the emulator reported 98.8 % janky frames (`dumpsys gfxinfo`; emulator rendering, phone UNKNOWN) | Founder: settle Q17. Reviewer: read or name a reviewed source; then the reviewed hadith path (ADR-22) or a new ADR, and T-BUILD-7 is revisited. Measure scrolling and first open (up to 4 s on the emulator) on a low-end phone |
