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

### G7 · Starting playback from the background (F1)

The design starts a `mediaPlayback` foreground service from an alarm. Android 12+ restricts
foreground-service starts from the background and exempts exact alarms (ASSUMED from general
knowledge; not fetched). The inexact fallback path, and silent/pre-reminder alarms, may not
have the exemption. Fetch the current rules for target SDK 37 before writing `shell/alarm/`.

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
| G11 | A remote for this repository, and CI | The spec leans on CI for the build gates; none is chosen. This checkout lives in a temporary cloud container |
| G12 | UI design: screens, flows, Bengali copy | Upstream asks for a real redesign and Play checks for it; the spec lists screen names only |
| G13 | A build machine and physical phones | Nothing was ever built (H8); every target in the spec is unmeasured |
| G14 | Play developer account type and its date | Decides whether the 12-tester, 14-day closed test applies |
| G15 | Privacy policy text and a support email | Play listing needs both; "Send a report" needs the address |
| G16 | **Closed 2026-11-26: skip silently; see ADR-3 amendment.** Rule for a prayer that is already past when the user enables reminders, and for two alarms within a minute of each other (pre-reminder vs Adhan) | Planner edge cases not stated |
| G17 | Numbering slips: `INV-T7` sits before `INV-T6` in `01`; test ids skip (no T-PROP-2, 4, 5; no T-UNIT-1, 3, 4, 6, 7, 8) | Harmless, but ids are the traceability key; fix or note as intentional |
| G19 | INV-T2 and INV-T6 pull against each other. T2 says `sehriEnd == fajr − margin`; T6 says an offset moves one prayer and nothing else. A Fajr offset would have to move Sehri end too, or break T2. Slice 1 takes the safe reading: Sehri end follows the *calculated* Fajr and has its own offset; the user's Fajr offset does not move it. Also, with Sehri end rounded down and Fajr rounded up (INV-T5) the two can show one minute apart at margin 0 | Reviewer to confirm with Q3 |
| G20 | Sunrise rounding is not stated in INV-T5. Slice 1 rounds it down, as the end of the Fajr window | Reviewer to confirm with Q3 |
| G21 | **Closed 2026-11-26: keep as built for v1.** The clock reports only the first prayer it cannot compute, so the planner drops every alarm for that day, including prayers that were computable. Follows INV-T7 as written; never happens inside Bangladesh | Decide before any use outside Bangladesh; the app must show the gap's date |
| G22 | Alarm ids assume a 48-hour window touches at most four local dates. True for every one-hour daylight-saving change (tested in nine zones); false only where a zone skips a whole day, as Samoa did in 2011 | Out of scope for Bangladesh; note for the forward path |
| G23 | The spec does not say whether `retired` or `draft` records may sit in `deeds.json`. Taken as: retired may stay (complete, with `retired_in`, never shown); draft fails the build. Written into INV-C1 | Founder or reviewer to object if wrong |
| G24 | Who parses `deeds.json` for the build gate. `:core` has no JSON parser and may not add one; the app uses the platform's. The gate (T-BUILD-4) needs a build-time parser: Gradle's bundled one in a build task is the likely answer | Decide when the gate task is written (phase 5) |
