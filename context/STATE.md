# State

**Last updated:** 2026-11-28 17:16 +0600

## In one paragraph

Every phase has been coded as far as it can go without phones, without the reviewer's deeds,
without a native speaker and without a Play account. `./gradlew build` is green in the cloud:
283 tests (198 `:core`, 70 `:app`, 12 content gate, 3 engine), lint clean, all five gates
passing (no `INTERNET`, 8 permissions, 4 libraries, 203 string keys in both languages, 64 real
districts). The release APK is 208,490 bytes, minified (184,738 before phase 10). **Nothing has run on a real phone, and
nothing changed on 28 Nov has run on any device.**

## Phase 10 in progress (branch `phase-10-widgets-tracker-location`)

- **Widgets built** (slice 1): `NextPrayerWidget` and `FiveTimesWidget`, platform `AppWidgetProvider`, no polling
  (`updatePeriodMillis=0`); `replan` redraws them and sets one alarm at the next prayer or local midnight. The view model
  is pure and tested in `:core`. KNOWN: build green. UNKNOWN: how they look and behave on a launcher; no emulator run.
- **Salah tracker built** (slice 2): `salah_log` is migration 2 of `deed-days.db` (ADR-18, so backup rules are
  unchanged); `core/tracker` holds the model (no field can express a missed count); `TrackerActivity` shows five dots a day
  for eight days, reached by a button on Home; the "Prayed" action is on silent prayer notices (G53: not on the Adhan's own
  notification). Tone is enforced by `TrackerStringsTest` over every `tracker_` string in both languages.
- **"Find my district" built** (slice 3): ADR-19 reverses ADR-12 for `ACCESS_COARSE_LOCATION` only (8 permissions; manifest,
  allowlist and spec table edited together). `OneShotLocation` reads once while `DistrictActivity` is open; `core/districts/Nearest`
  picks the nearest headquarters point; every result needs a tap and the far cases show a warning first. The privacy and
  Data safety drafts in `spec/06` are updated. UNKNOWN: everything on a device (permission dialog, providers, T-INST-11).
- **Phase 10 is built; it is not verified.** Every behaviour item is open until the founder's one emulator pass. Open items
  Claude added: G53 (no "Prayed" on the Adhan's own notification).

## Phase 11 built (branch `phase-11-12-hijri-zakat`)

- **Hijri date, calculated only** (ADR-20). `core/hijri`: the tabular civil calendar, a closed -1/0/+1 adjustment, the day changing
  at Maghrib (ASSUMED; midnight where Maghrib cannot be computed). Home shows the date, the rule in words and where to move it;
  Settings holds the one-day move (in `AppPrefs`, so it is backed up). In the calculated month of Ramadan Home offers Ramadan
  mode and waits for the tap (G33 amended). Upstream's `hijri` module was read and not vendored (its table is Turkey's).
- **Not decided:** whose announcement decides the month (Q10, G54). Measured: the calculated first of Ramadan 1447 is one day
  before the announced one (2026-02-18 against 2026-02-19), and the rule is up to 2 days from Umm al-Qura. No published
  table of Bangladesh month starts was found. KNOWN: build green; UNKNOWN: how it looks on a device.

## What changed on 28 Nov 2026

- **Real districts (Q4).** `content/districts.json`: all 64, Wikidata (CC0) checked against
  Wikipedia, elevations from Open-Meteo, every source in `content/districts-SOURCES.md`.
- **Times follow the Islamic Foundation (ADR-16).** Its permanent Dhaka timetable (a mirrored
  copy of its PDF) showed the app put Iftar about 3 minutes early and let people eat about
  5 minutes past the Foundation's Sehri end. The app now adds the Foundation's own safety
  margins by default (Fajr +2, Dhuhr +3, Maghrib +3, Isha +2, Sehri end 6 minutes before Fajr),
  never more than 1 minute on the unsafe side over a leap cycle. Each district still follows
  its own sun, so outside Dhaka the app can differ from mosques that use the Foundation's
  flat district offsets by up to about 10 minutes (G50).
- **Ramadan mode and the daily deed nudge** built (G33, G35, Claude's rulings).
- **Phone-maker steps** for nine makers plus Infinix and itel.
- **Release:** R8 minification (ADR-15 had recorded it, the build never had it), environment
  signing, `tools/release.sh`.
- **Research recorded:** Play's exact-alarm and foreground-service policies (G51, G52), closed
  testing, Data safety (`spec/06`); Adhan recordings with stated licences (`spec/06` E).

## Copies and branches

- This cloud copy, `main`, is ahead of GitHub until pushed. The Mac's clone
  (`~/dev/mumins-day`) has not been updated by this session.
- The cloud environment `mumins-day` now also allows Wikidata, Wikipedia, Open-Meteo,
  kivabe.com, the Islamic Foundation, dontkillmyapp.com, Android and Play documentation, and
  Wikimedia Commons.

## Next

1. **Run the emulator suite on the Mac** (`tests/inst/run.sh`, `ui.sh`, `deed.sh`,
   `qibla.sh`, `onboard.sh`) against this commit: calibration, Ramadan mode, the nudge and the
   brand screen have only run on the JVM.
2. **Founder and reviewer decisions**, highest leverage first: ADR-16's margins (G48, G49, G50);
   G51 (`USE_EXACT_ALARM`); G52 (`mediaPlayback`); G33 and G35 rulings; an Adhan recording (Q5).
3. **People:** the reviewer's 30 deeds; the native speaker for 169 keys and 64 district names.
4. **Phones** for phase 7.
5. **Part 2** (phases 9 to 17 in `PLAN.md`): phases 9 and 10 are built (10 unverified on a device); phase 11 is built (calculated date;
   authority open, Q10); next is phase 12 (Zakat). Phases 9 to 13 need no new permission beyond location and can be built while v1 waits.

## Waiting on the founder

| What | Why it matters | Id |
|---|---|---|
| Confirm or change the Foundation margins | They decide when people stop eating and break their fast | ADR-16, G48–G50, Q3 |
| Reviewer and native speaker, by name | All deed content; all Bengali | G9 |
| Exact-alarm permission choice | Play may reject `USE_EXACT_ALARM` | G51 |
| Playback service type | Play says media playback "must be user-initiated" | G52 |
| An Adhan recording | Release | Q5 |
| Which authority decides the Hijri month; the Maghrib day change | Phase 11 shows a calculated date only | Q10, G54 |
| Phones | Phase 7, the reliability claim | Q8, G13 |
| Play account, support email, privacy URL | Release | G14, G15 |
