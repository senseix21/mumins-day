# State

**Last updated:** 2026-11-28 20:24 +0600

## In one paragraph

Every phase has been coded as far as it can go without phones, without the reviewer's deeds,
without a native speaker and without a Play account. `./gradlew build --rerun-tasks` is green on the Mac
(branch `quran-bengali-translations`): 428 tests (288 `:core`, 101 `:app`, 36 content gate, 3 engine), lint clean, all gates
passing (no `INTERNET`, 8 permissions, 4 libraries, 329 string keys in both languages, 64 real
districts, 120 Quran files matching their recorded SHA-256 including two Bengali translations of 6236 ayahs each; Zakat rules, duas and hadith absent so passed with a notice). The release APK is 5,440,616 bytes, unsigned, minified (900,104 before the translations, 879,473 before phase 16). **Nothing has run on a real phone; the translations have not run on any device.**

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

## Phase 12 built (same branch)

- **Zakat calculator on a fake rules file** (ADR-21). `core/zakat` holds no rule: a test reads its source and fails on any number
  but 0, 1, 2, 30, 100. The rules are `content/zakat-rules.json`, signed by the reviewer, which does not exist yet; the gate
  `checkZakatRules` passes with a notice while it is absent, refuses an unsigned, incomplete or `test_fixture` file. Debug
  builds ship a FAKE fixture (banner on screen); a release says "no signed rules" and calculates nothing. The screen shows every
  step, takes prices the user types with a date, never fetches, saves nothing. Amounts round up, the nisab's value down.
- **Open:** Q11 (reviewer signs every rule), G55 (conversions have only a secondary, unattributed lead: 11.664 g, 16 ana,
  6 ratti; in no file or code). KNOWN: build green; UNKNOWN: how it looks on a device.

## Phase 13 built (branch `phase-13-14-content-quran`)

- **Daily dua and daily hadith on FAKE records** (ADR-22). One provenance check (`checkProvenanceOf`) now serves deeds, duas and
  hadith; the deed gate and its tests are unchanged. `core/content` holds the kinds, one key table both parsers read, `checkKind`
  and the shared rotation (`pickForDay`, also behind `deedFor`). The books a kind may cite are the reviewer's `sources` list in
  each file; no book name is in code. Gates `checkDuaContent` and `checkHadithContent` pass with a notice while the files are
  absent (`-PduasRequired`, `-PhadithRequired` make absence fail) and refuse a `test_fixture` file. `TodayActivity` (Home button)
  shows the day's dua and hadith, Arabic right to left in an isolate (`l10n.rightToLeft`), source and grading as written.
- **No new notification:** the deed nudge stays the only content notification (ADR-22 item 5, to confirm, Q12). The day's record
  is not stored (G56). KNOWN: build green. UNKNOWN: how the screen looks on a device.

## Phase 14 built (same branch)

- **The real Quran text, verbatim** (ADR-23). Tanzil Quran Text v1.1 (Uthmani, shown; Simple Clean, indexed only) and Tanzil's
  metadata are bundled byte for byte: their terms were read and quoted ("Permission is granted to copy and distribute verbatim
  copies of this text, but CHANGING IT IS NOT ALLOWED", source and a link to tanzil.net) in `content/quran-SOURCES.md` with
  each file's SHA-256. T-BUILD-6 (`checkQuranText`) fails the build on any changed byte, unrecorded file, or translation
  without recorded permission, and checks 114 surahs and 6236 ayahs against the metadata; asset staging depends on it.
- **Font:** Amiri Quran 1.003 (SIL OFL 1.1, read in full), covering every code point of the text; OFL on the Licences screen.
- **Reader and search:** `QuranActivity` (continue, search, 114 surahs) and `SurahActivity` (ayahs, translation slot, remembers
  the first ayah on screen in `AppPrefs`). The search database is built on the phone with `SQLiteOpenHelper` and FTS4 (no
  build-time JDBC library, INV-P4); the phone re-checks every asset's hash and reads every ayah back after building.
- **Two Bengali translations ship** (ADR-26, founder's decision 2026-11-28), see the next section.
- KNOWN: build green; the release APK's Quran files have the recorded hashes. UNKNOWN: everything on a device (first-open
  time, FTS4 on SQLite 3.18, rendering), G57 to G59; Q13 (the offline "link" term).

## Bengali translations added (branch `quran-bengali-translations`, ADR-26)

- **What:** QuranEnc's `bengali_zakaria` v1.1.1 (Dr. Abu Bakr Muhammad Zakaria; 114 sura API responses) and `bengali_rwwad`
  v1.1.4 (Rowwad Translation Center; QuranEnc's XML download), byte for byte in `content/quran/translations/`, hashed;
  6236 ayahs each, per-surah counts equal the metadata (KNOWN: gate output, `QuranEncTest`).
- **Permission:** QuranEnc's seven published terms, quoted in `content/quran-SOURCES.md` and `content/quranenc-terms.txt`;
  INV-QT1 now counts published terms as the writing. No letter from QuranEnc. **Zakaria is not offered for download by
  QuranEnc** (no buttons, not in its list API): coverage UNKNOWN, ask QuranEnc (Q15).
- **App:** the user picks (null until then; Arabic alone meanwhile); text and footnotes shown exactly, `<b>` tags literal
  (G65); credit with QuranEnc.com and version on reader and Licences; Bengali search over the chosen translation (FTS4,
  migration 2 of `quran.db`). The FAKE debug translation is deleted.
- KNOWN: JVM tests and gates, the release APK's 120 Quran files have the recorded hashes, schema and search SQL on desktop
  SQLite. UNKNOWN: everything on a device, first-open time with 36 MB to hash (G66).

## Phase 15 built (branch `phase-15-16-khatam-packs`)

- **Khatam plan, counted in ayahs** (ADR-24). `core/khatam`: `QuranLayout` from the verified metadata's surahs and its 30 juz
  starts (`parseJuzStarts`), `khatamToday` recomputed every day from where the day began to the target (rounded up, so the
  plan ends by the target), sealed `KhatamDay` (`ToRead`, `Read` with an optional next stretch, `Complete`, `NewDateNeeded`).
  No field holds a day without reading. `khatam` is migration 3 of the backed-up `deed-days.db`; backup rules unchanged.
- **Screen:** `KhatamActivity` (Home button): finish date, today's surah:ayah range and juz, open in the reader, "I have read
  it", read on, take back, change date, remove. `KhatamStringsTest` scans the 28 `khatam_` strings for behind, missed, late,
  counts and digits.
- **Reminder:** no new notification. An off-by-default switch lets the one daily nudge mention a waiting portion (`nudgeFor`);
  with the nudge off there is none (G61). KNOWN: build green. UNKNOWN: the screen and the nudge on a device; Q14, G60, G61.

## Phase 16 built, without `INTERNET` (same branch)

- **Pack machinery on a TEST PACK only** (ADR-25). `core/packs`: manifest parse, host (`packSource`, empty means none), resume
  (`startFrom`, `readReply`), SHA-256 streaming and check, the download's sealed state machine (only a tap starts requests,
  only a matched hash makes a pack ready), the verse timings format. `app/.../packs`: `fetchFile` on `HttpsURLConnection` (https
  only, no redirects, fixed `User-Agent: packs`, `Range` to resume), `PackFiles` (re-hash before use), one worker per tapped
  download. `PacksActivity` (from Settings, with the space used) and `RecitationActivity` (`MediaPlayer`, ayah from timings).
- **Nothing can be downloaded:** `PACK_HOST` is empty, the bundled manifest is empty, no `INTERNET`, and the permission gate
  still fails on it. The one commit that adds it, and the proposed new INV-P1, are written in ADR-25; `spec/01` is unchanged.
  Privacy and Data safety drafts for that day are `spec/06` F. Cleartext is refused by `network_security_config.xml`.
- KNOWN: `DownloaderTest` against a local HTTPS server (JDK `SSLServerSocket`, `keytool` certificate) and `NetworkSurfaceTest`
  pass on the JVM. UNKNOWN: anything on Android (G62); background recitation (G63); free-space check (G64).

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

- On the Mac (`~/dev/mumins-day`): branch `quran-bengali-translations`, from `phase-15-16-khatam-packs` (dd179b5), pushed
  to GitHub; not merged, no PR. Earlier notes said the cloud copy's `main` was ahead of GitHub.
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
5. **Part 2** (phases 9 to 17 in `PLAN.md`): phases 9 and 10 are built (10 unverified on a device); phases 11 (calculated date; authority open, Q10) and 12 (Zakat on a fake
   rules file; Q11) 13 (duas and hadith on fake records; Q12) and 14 (Quran Arabic text, reader, search, two QuranEnc Bengali translations; Q13, Q15) 15 (khatam plan; Q14) and 16 (pack machinery, no `INTERNET`, ADR-25) are built; next is phase 17 (proof and release), which needs phones. Phases 9 to 13 need no new permission beyond location and can be built while v1 waits.

## Waiting on the founder

| What | Why it matters | Id |
|---|---|---|
| Confirm or change the Foundation margins | They decide when people stop eating and break their fast | ADR-16, G48–G50, Q3 |
| Reviewer and native speaker, by name | All deed content; all Bengali | G9 |
| Exact-alarm permission choice | Play may reject `USE_EXACT_ALARM` | G51 |
| Playback service type | Play says media playback "must be user-initiated" | G52 |
| An Adhan recording | Release | Q5 |
| Which authority decides the Hijri month; the Maghrib day change | Phase 11 shows a calculated date only | Q10, G54 |
| Reviewer signs the Zakat rules | Phase 12 calculates nothing in a release without them | Q11, G55 |
| Reviewer: books, texts and licences for duas and hadith; the no-nudge decision | Phase 13 shows nothing in a release without them | Q12, G56 |
| Ask QuranEnc whether Zakaria (not offered for download) may be republished; notify QuranEnc of the use (condition 5); check for newer versions before each release (condition 6); Tanzil's offline "link" term | Zakaria in a store release; keeping the terms | Q13, Q15, `PLAN` founder track |
| `<b>` tags shown literally in Zakaria's footnotes, or rendered bold | How the footnotes look | G65 |
| Khatam unit (ayah) and the reminder riding on the deed nudge | Phase 15 built that way | Q14, G61 |
| A pack host, and a tafsir or recitation with written permission | Phase 16 downloads nothing until then; `INTERNET` waits (ADR-25) | ADR-25, G62 |
| Phones | Phase 7, the reliability claim | Q8, G13 |
| Play account, support email, privacy URL | Release | G14, G15 |
