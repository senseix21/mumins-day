# State

**Last updated:** 2026-11-28 15:16 +0600

## In one paragraph

Every phase has been coded as far as it can go without phones, without the reviewer's deeds,
without a native speaker and without a Play account. `./gradlew build` is green in the cloud:
239 tests (160 `:core`, 64 `:app`, 12 content gate, 3 engine), lint clean, all five gates
passing (no `INTERNET`, 7 permissions, 4 libraries, 169 string keys in both languages, 64 real
districts). The release APK is 184,738 bytes, minified. **Nothing has run on a real phone, and
nothing changed on 28 Nov has run on any device.**

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

## Waiting on the founder

| What | Why it matters | Id |
|---|---|---|
| Confirm or change the Foundation margins | They decide when people stop eating and break their fast | ADR-16, G48–G50, Q3 |
| Reviewer and native speaker, by name | All deed content; all Bengali | G9 |
| Exact-alarm permission choice | Play may reject `USE_EXACT_ALARM` | G51 |
| Playback service type | Play says media playback "must be user-initiated" | G52 |
| An Adhan recording | Release | Q5 |
| Phones | Phase 7, the reliability claim | Q8, G13 |
| Play account, support email, privacy URL | Release | G14, G15 |
