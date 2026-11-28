# 04 · Verification

This file defines what "done" means. A result other than **Not run** names where it ran and the date; the command is in `context/LOG.md`. "Pass on JVM" means the pure core's tests passed on a desktop JVM, not on a phone.

## Traceability

| Requirement | Invariant | Test | What it does | Result |
|---|---|---|---|---|
| F2 order of prayers | INV-T1 | T-PROP-1 | Property test over random dates and Bangladesh points | Pass on JVM, 2026-11-26 |
| F2 Sehri and Iftar | INV-T2 | T-UNIT-2 | Iftar equals Maghrib; Sehri end equals Fajr minus margin | Pass on JVM, 2026-11-26 |
| F2 determinism | INV-T3 | T-PROP-3 | Same result under different default zones and locales | Pass on JVM, 2026-11-26 |
| F2 matches reference | INV-T4 | T-FIX-4 | Eight cities × 365 days against the reference table, tolerance 1 min | Blocked on Q2. Partial, 2026-11-27: Dhaka only, 30 days of Ramadan 2026, secondary copy; within 1 minute at reference longitude 90.2 E, up to 2 minutes off at the city centre (G48) |
| F2 rounding | INV-T5 | T-UNIT-5 | Sehri end rounds down; Fajr start and all later times round up | Pass on JVM, 2026-11-26 |
| F2 offsets | INV-T6 | T-PROP-6 | Offset N moves one prayer by N | Pass on JVM, 2026-11-26 |
| F2 no invented time | INV-T7 | T-UNIT-11 | Forced NaN yields "not computable", no alarm | Pass on JVM, 2026-11-26 |
| F1 24-hour window | INV-S1 | T-INST-1 | After scheduling, `dumpsys alarm` lists every enabled prayer in the next 24 h | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 right instant | INV-S2 | T-PROP-7 | Planner output equals engine time in the place's zone under any device zone | Pass on JVM, 2026-11-26 |
| F1 reboot | INV-S3 | T-INST-2 | Reboot emulator; window restored within 60 s | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 zone change | INV-S3 | T-INST-3 | Change device zone; instants unchanged | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 clock change | INV-S3 | T-INST-4 | Set clock ±3 h; window correct | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 app update | INV-S3 | T-INST-5 | `adb install -r`; window present | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 permission regrant | INV-S3 | T-INST-6 | Revoke then grant exact alarms; window restored, warning cleared | Pass 2026-11-27 on an API 31 emulator `mumins_api31` (appop deny, queue emptied by the platform, replan registered nothing, banner and grant button shown, re-grant broadcast restored 11 alarms and cleared the banner); evidence gathered by hand and `run.sh`; API 32 and a phone not run |
| F1 idempotence | INV-S4 | T-INST-7 | Schedule 100 times; alarm set unchanged | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 no double Adhan | INV-S5 | T-INST-8 | Fire replan during playback; one playback only | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 late rule | INV-S7 | T-INST-9 | Deliver 5 and 15 min late; Adhan then silent | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 health log | INV-S8 | T-INST-10 | Log rows written with planned and actual times | Pass 2026-11-27, emulator streambert API 36, `tests/inst/run.sh` |
| F1 honest warnings | INV-S6 | T-UI-1 | Each Degraded reason shows its message and fix | Not run |
| Privacy: no network | INV-P1 | T-BUILD-1 | Merged manifest lacks `INTERNET` | Pass, 2026-11-26: release and debug manifests have no `INTERNET`; adding it fails the build |
| Privacy: location | INV-P2 | T-INST-11 | No location request while backgrounded | Not run (the code stops the read in `onPause`; `NearestTest` covers the pure choice on the JVM, 2026-11-28) |
| Privacy: permissions | INV-P3 | T-BUILD-2 | Merged manifest equals allowlist | Pass, 2026-11-28: eight permissions match (seven on 2026-11-27, nine on 2026-11-26; `ACCESS_COARSE_LOCATION` added by ADR-19); an extra, a missing one and a dropped `maxSdkVersion` each fail the build |
| Dependencies | INV-P4 | T-BUILD-3 | Resolved dependency list equals allowlist | Pass, 2026-11-27: four libraries match (six on 2026-11-26, before ADR-14); adding one fails the build |
| F4 review gate | INV-C1, C3 | T-BUILD-4 | Build fails on any deed not approved or missing a field | Pass with fake records, 2026-11-26: the build fails on a draft, a missing reviewer, or fewer than 30 approved. Real content: none yet |
| F4 rotation | INV-C2 | T-PROP-8 | Same date gives same deed; every deed appears once per cycle | Pass on JVM, 2026-11-26 |
| F4 tone | INV-C4 | T-UI-2 | Manual review of every string and screen by the reviewer and a native speaker | Not run |
| Bengali complete | INV-L1 | T-BUILD-5 | Build fails on a missing key | Not run |
| Tracker never judges (phase 10) | INV-C5 | T-UI-2, T-UNIT | No missed count or streak in any tracker type or string | JVM part pass 2026-11-28: `TrackerTest` (no count, streak, percent or miss in any member name of the tracker types; window and date attribution across midnight and a zone change) and `TrackerStringsTest` (every `tracker_` string in both languages free of judging words, digits, percent signs and plurals). Reviewer's and native speaker's read (T-UI-2): Not run |
| Later content kinds pass the gate (phase 13) | INV-C6 | T-BUILD-4 per kind | Fake or unreviewed record fails the build | Pass with fake records, 2026-11-28: `KindGateTest` in `:core` (15) and in the gate (7): a draft, each missing field, a book outside the reviewer's `sources`, a test fixture in the shipping gate, fewer than 30, a direction control each fail; `checkDebugDuaContent` and `checkDebugHadithContent` pass on the fixtures; `-PhadithRequired=true` with no file fails the build. Real content: none |
| Quran text unmodified (phase 14) | INV-QT1 | T-BUILD-6 | Hash of each bundled text equals the recorded hash | Pass 2026-11-28 20:24 +0600 with the two QuranEnc translations (ADR-26): "120 files match their recorded SHA-256; 114 surahs, 6236 ayahs ...; 2 translation(s): bengali_zakaria 6236 ayahs, bengali_rwwad 6236 ayahs"; `QuranGateTest` (10) fails on one changed byte of each translation, an ayah missing with its hash re-recorded, a missing permission line or file, an unknown edition or a flat `translation-*` file; `QuranEncTest` (9) counts 6236 per translation, re-encodes Zakaria's 114 responses to their exact bytes, finds Rowwad's every text as the file's exact CDATA; the 120 files in the release APK have the recorded hashes. Earlier, 2026-11-28: `checkQuranText` "5 files match their recorded SHA-256; 114 surahs, 6236 ayahs"; `QuranGateTest` (6) fails the build on one changed byte in each file, a trailing newline, CRLF line endings, an unrecorded or missing file, a translation without a recorded permission, a fixture translation; `QuranTest` (12) round-trips both texts byte for byte through the parser; the five files extracted from the release APK have the recorded hashes (`shasum -a 256`). The on-phone check and the database read-back: Not run (no device) |
| Unreviewed local content never ships (Hisnul Muslim) | INV-C1, INV-C6 (ADR-27) | T-BUILD-7 | The release APK holds no `assets/hisnul-muslim/` entry | Pass 2026-11-28 21:24 +0600: `checkReleaseHasNoLocalContent` "app-release-unsigned.apk holds no assets/hisnul-muslim/ file"; with the debug-only condition forced off it failed the build listing the 313 entries; `checkHisnulMuslim` "313 files match their recorded SHA-256; 133 chapters, 312 duas, 426 segments", and passes with a notice when `content/local` is absent |
| Unreviewed local content never ships (hadith books) | INV-C1, INV-C6 (ADR-28) | T-BUILD-7 | The release APK holds no `assets/hadith-books/` entry | Pass 2026-11-28 22:17 +0600: `checkReleaseHasNoLocalContent` "holds no file under assets/hisnul-muslim/, assets/hadith-books/"; with the hadith staging forced into release it failed listing the entries; `checkHadithBooks` "18 files match their recorded SHA-256; 8 books, 36432 hadiths, every count as recorded", and passes with a notice when `content/local` is absent |
| Hijri date names its rule, ±1 day (phase 11) | INV-H1 | T-UNIT | Every rendered date carries its rule | Pure part built 2026-11-28 (`HijriTest`, `./gradlew build` green); the screen is not run on a device (T-UI-2 open) |
| Zakat rules come from a signed file (phase 12) | INV-Z1 | T-BUILD-4, T-UNIT | No rule number in code | Built 2026-11-28 (`RulesTest` scans the package source; `ZakatGateTest`; `checkZakatRules`, `checkDebugZakatRules`); the screen is not run on a device |
| Khatam plan never says behind (phase 15) | INV-C4 | T-UNIT, T-UI-2 | Portions sum to the whole Quran; no member or string for a miss, lag or count | JVM part pass 2026-11-28: `KhatamTest` (14: 159 plan lengths cover 6236 ayahs with no gap or overlap by the target; target today; target past is a value; finishing early; travel; 300 random action sequences; member names) and `KhatamStringsTest` (4, both languages). Screen: not run |
| Packs: no request without a tap, used only if the hash matches (phase 16) | proposed INV-P1 (ADR-25) | T-UNIT, T-JVM-NET | Manifest, host, resume, hash and download state; the downloader against a local HTTPS server; the network surface from the sources | JVM pass 2026-11-28: `PacksTest` (14, `:core`), `DownloaderTest` (10) and `NetworkSurfaceTest` (5) in `:app`. On a device: not run; no host exists, so nothing can be downloaded |
| Bengali digits | INV-L2 | T-UNIT-9 | All ten digits map; screenshot test finds no Latin digit | Digit mapping: pass on JVM, 2026-11-26. Screenshot part: not run |
| Arabic in Bengali | INV-L3 | T-UI-3 | Screenshot review on the matrix | Not run |
| Font scale | INV-L4 | T-UI-4 | Screenshots at 200 % | Not run |
| F3 bearing | INV-Q1 | T-UNIT-10 | Bearing for eight cities against independently computed values | Pass on JVM, 2026-11-26 |
| F3 low accuracy | INV-Q2 | T-INST-12 | Inject low accuracy; warning and prompt appear | Partly: field-strength half Pass 2026-11-27 on the emulator (`tests/inst/qibla.sh field`); low-accuracy half Not run on a device (the emulator reports high accuracy); JVM tests cover all lower levels |
| F3 no magnetometer | INV-Q3 | T-INST-13 | Emulator without the sensor; number shown, no needle | Pass 2026-11-27, AVD `streambert_nomag` (`ANDROID_SERIAL=emulator-5556 tests/inst/qibla.sh nomag`) |

## Device matrix

Exact models are a founder decision (Q8); the brands follow the brief.

| Slot | Brand / skin | Why | Android |
|---|---|---|---|
| D1 | Xiaomi or Redmi (MIUI/HyperOS) | Undocumented background limits (KNOWN, dontkillmyapp.com) | Newest available |
| D2 | Samsung (One UI) | The base carries a Samsung-specific scheduling workaround (KNOWN, `AlarmService.kt:158-167`) | Newest available |
| D3 | Realme or Oppo (ColorOS) | Brief | Mid-age |
| D4 | Infinix or Tecno (HiOS/XOS) | Brief; common low-cost phones (ASSUMED) | Mid-age |
| D5 | Any phone with 2 GB RAM | Minimum device | Oldest supported: Android 8 |
| D6 | Pixel or emulator | Reference behaviour | Android 12, 13, 14 and the current target level |

## Manual tests

### M-1 · "Adhan fires with the app swiped away and battery saver on"

Run on every device, in Bengali, with no settings changed after onboarding unless a step says so.

1. Factory-default power settings. Install the release build. Complete onboarding, following the per-brand steps the app shows.
2. Tap **Test Adhan**. Lock the screen. Expect audio within 30 s ± 3 s. If it fails, stop: the result is FAIL.
3. Enable Adhan for all five prayers. Note the five displayed times.
4. Open recents and swipe the app away.
5. Turn on the phone's battery saver.
6. Lock the screen and put the phone face up, unplugged, on mobile data off and Wi-Fi off.
7. Leave it untouched until at least 10 minutes after the next prayer time, and for at least 60 minutes in total so that idle mode can begin (ASSUMED sufficient; confirm with `adb shell dumpsys deviceidle`).
8. Record whether the Adhan played and the second it began, from a separate clock.
9. Repeat across all five prayers of one day, including Fajr after a full night idle.
10. Reboot the phone, do **not** open the app, and repeat steps 6-8 for the next prayer.
11. Open the app. The Reminder-health screen must show exactly what you observed.

**Pass:** every Adhan starts within 60 s of the displayed time, and step 11 matches. **Fail:** any miss or mismatch; file it with the health log.

Then repeat steps 3-11 once **without** following the per-brand steps. A miss here is expected on some brands; the requirement is that the app's warning was on screen beforehand and step 11 reports the miss.

### M-2 · Seven-day soak

Daily use for seven days on D1-D5 with one tester each. Pass: 35 of 35 Adhans per phone on phones where the brand steps were followed.

### M-3 · Qibla

At a point with a surveyed bearing: needle within 5° after calibration on each device; with a magnet beside the phone the warning appears within 2 s; on a table next to a laptop the warning appears.

### M-4 · First run, offline

Fresh install in airplane mode: choose a district, see times, enable Adhan, see today's deed. No error, no spinner, no English fallback.

## Release checklist

**Licences**
- [ ] Dependency report generated; every licence read; allowlist matches.
- [ ] LGPL notices for the engine in the app and the repository; engine unmodified, or changes published.
- [ ] Legal opinion on LGPL in a Play app (Q6) before any paid tier.
- [ ] `NOTICE` credits prayer-times-android for ported code.
- [ ] Adhan recording licence in writing (Q5).
- [ ] District data licence recorded (Q4).

**Content**
- [ ] Reviewer's signed note for the shipped `contentVersion`, listing every deed id.
- [ ] Bengali wording reviewed by a native speaker; INV-C4 tone review done.
- [ ] No Quran translation text in v1, so no translation permission is needed yet. Permission in writing remains a gate for the Quran release.

**Privacy and policy**
- [ ] Privacy page published in Bengali and English: no data collected, no network permission.
- [ ] Play Data safety form completed. Expected answer is "no data collected or shared", to be confirmed against the form's own definitions (UNKNOWN until read in Play Console).
- [ ] Exact-alarm and foreground-service declarations completed in Play Console (ADR-4).
- [ ] UI and listing reviewed side by side with the upstream app: clearly a different product.

**Testing**
- [ ] All T-* tests green in CI.
- [ ] M-1 passed on D1-D6; M-2 complete; M-3 and M-4 passed.
- [ ] Closed test: at least 12 testers opted in for 14 continuous days (KNOWN requirement).

**Listing**
- [ ] Title, short and full description in Bengali and English.
- [ ] Screenshots in both languages from the release build.
- [ ] Upload key backed up offline.
