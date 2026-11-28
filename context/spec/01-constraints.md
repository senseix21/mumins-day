# 01 · Constraints and invariants

Labels and path shorthand are as in `00-base-audit.md`. Every number here that I chose is marked **(target)**: it is a proposal to be confirmed on a real device, not a measurement.

## Invariants

Each one is written so a test can fail it. Test ids refer to `04-verification.md`.

### Time engine

| Id | Invariant | Test |
|---|---|---|
| INV-T1 | For any date and any point inside Bangladesh's bounding box, `imsak ≤ fajr < sunrise < dhuhr < asrShafi < asrHanafi < maghrib < isha`, all within the same civil day. | T-PROP-1 |
| INV-T2 | `iftar == maghrib` and `sehriEnd == fajr − sehriMargin`, where `sehriMargin ≥ 0` is a single named constant (default 0, see Q3). **Since 2026-11-28 (ADR-16)** the app adds the Islamic Foundation calibration on top: Sehri end is 6 minutes before the calculated Fajr before the user's own margin. The invariant is tested on the raw clock; the calibration has its own test. | T-UNIT-2 |
| INV-T3 | The engine is a pure function: no clock, no locale, no I/O. Same input, same output, on any phone in any time zone. | T-PROP-3 |
| INV-T4 | For Dhaka and seven other divisional cities, on every day of the reference year, each displayed time differs from the agreed reference timetable by at most 1 minute after default offsets **(target; reference source is Q2)**. | T-FIX-4 |
| INV-T5 | Rounding never tells anyone to act early or late in the unsafe direction: "stop eating" rounds down, "may break fast" and every "prayer may begin" time rounds up. The current engine rounds imsak **and Fajr** down and everything from Dhuhr onward up (KNOWN, `E/DoubleTimes.kt`). So Fajr-as-prayer-start can show up to a minute early. Mu'mins DAY shows two values: Sehri end (rounded down) and Fajr begins (rounded up). Reviewer to confirm (Q3). | T-UNIT-5 |
| INV-T7 | The engine never returns a made-up time. Today it returns 00:00 when a time cannot be computed (KNOWN, `E/DoubleTimes.kt`: `if (isNaN()) return LocalTime(0, 0)`). Mu'mins DAY's wrapper returns an explicit "not computable" result and the scheduler sets no alarm for it. | T-UNIT-11 |
| INV-T6 | A user offset of N minutes moves exactly that one prayer by exactly N minutes and nothing else. | T-PROP-6 |

### Scheduler

| Id | Invariant | Test |
|---|---|---|
| INV-S1 | At any moment when reminders are enabled, the system alarm queue holds an alarm for **every** enabled prayer in the next 48 hours (not only the next one). Fixes S1. (Was 24 hours; founder decision 2026-11-26, ADR-3 amendment.) | T-INST-1 |
| INV-S2 | Each registered alarm's trigger instant equals the engine's time for that prayer, interpreted in the **city's** time zone, to the minute. Fixes S3. | T-PROP-7 |
| INV-S3 | After reboot, time-zone change, manual clock change, app update, or the exact-alarm permission being granted, INV-S1 holds again within 60 s **(target)** with no user action. | T-INST-2…6 |
| INV-S4 | Scheduling is idempotent: running it any number of times leaves exactly the same set of alarms. | T-INST-7 |
| INV-S5 | A prayer never produces two Adhans. | T-INST-8 |
| INV-S6 | If the app cannot guarantee an exact alarm (permission missing, notifications blocked), the home screen says so in words. It never degrades silently. Fixes S4. | T-UI-1 |
| INV-S7 | A late alarm is handled by a stated rule: up to 10 minutes late **(target)** the Adhan still plays; after that only a silent notification is posted. | T-INST-9 |
| INV-S8 | The app records the planned and actual time of each fired alarm on the device, and shows the last seven days to the user. This is how the reliability claim is checked in the field without analytics. | T-INST-10 |

### Privacy

| Id | Invariant | Test |
|---|---|---|
| INV-P1 | v1 makes **zero** network requests. The manifest does not declare `INTERNET`. | T-BUILD-1 |
| INV-P2 | Location is read only while a screen that needs it is open, is used only on the device, and only the chosen district or coordinates are stored. | T-INST-11 |
| INV-P3 | The merged manifest's permission list equals the allowlist in `03-architecture.md` exactly. A new permission fails the build. | T-BUILD-2 |
| INV-P4 | No dependency outside the allowlist in `03-architecture.md`. A new one fails the build. | T-BUILD-3 |

### Content

| Id | Invariant | Test |
|---|---|---|
| INV-C1 | No religious text ships unless its record has `status = approved`, a named reviewer and a review date. The build fails otherwise. The build also fails with fewer than 30 approved deeds (founder, 2026-11-26, Q9). A `retired` record may stay in the file for the audit trail; it is complete, carries `retired_in`, and is never shown. A `draft` record fails the build. | T-BUILD-4 |
| INV-C2 | The deed first computed for a calendar date is a pure function of that date and the content version: same date, same deed, offline, on every phone. Once a card has been shown it stays for that day, whatever updates arrive, unless its deed is retired. Within one content version the daily deed never repeats inside a cycle. "Show me another" gives the deed half a cycle away. (Reworded 2026-11-26: founder accepted the G4 and G5 proposals.) | T-PROP-8 |
| INV-C3 | Every deed shows its source book and number. No reward, grading or quotation appears that is not in the reviewed record. | T-BUILD-4 |
| INV-C4 | No screen, notification or widget ever shows a missed-day count, a broken streak, or wording that blames the user. | T-UI-2 (manual review) |
| INV-C5 | The Salah tracker (phase 10) is private and never judges. It shows only what the user marked, never a count of prayers not marked, never a streak, never a comparison with another person or another day, and it never sends a notification about itself. Extends INV-C4; nothing it stores is read by any screen except the tracker. Added 2026-11-28 (ADR-17). | T-UI-2, T-UNIT (tracker view model has no field for a missed count) |
| INV-C6 | Every content kind shipped after deeds (dua, hadith) passes the same gate as deeds: source, edition and number, grading as the reviewer wrote it, named reviewer, review date, status approved. Test fixtures are obviously fake and the store build refuses to ship them. Added 2026-11-28 (ADR-17). | T-BUILD-4 (one task per kind) |

### Quran, Hijri and Zakat (added 2026-11-28, ADR-17)

| Id | Invariant | Test |
|---|---|---|
| INV-QT1 | The Quran text, Arabic and any translation, is shipped byte for byte as its source published it. No code edits, normalises, truncates or reorders it. The build checks a hash of each bundled text file against the hash recorded with its licence, and fails on a mismatch. A translation is bundled only with its permission in writing recorded in `content/`. | T-BUILD-6 (new) |
| INV-H1 | Every Hijri date shown names the rule that produced it (for example "calculated" or "announced by X") and can be moved one day either way by the user. The app never presents a calculated date as an announcement. | T-UNIT (Hijri), T-UI-2 |
| INV-Z1 | The Zakat calculator takes every rule (nisab basis, rate, unit conversions) from a file the reviewer signed. Claude's code contains none of those numbers except in test fixtures marked fake. It stores no amount the user typed unless the user taps save (built: there is no save, so nothing typed is stored). A rules file marked `test_fixture` cannot ship: the gate refuses it for `content/zakat-rules.json`. Amounts owed round up and the nisab's value rounds down (ADR-21). | T-BUILD-4 (rules file), T-UNIT |

### Localization

| Id | Invariant | Test |
|---|---|---|
| INV-L1 | Bengali is 100 % complete: every user-visible string has a Bengali value. The build fails on a missing key. English is the fallback language and is also complete. | T-BUILD-5 |
| INV-L2 | When the language is Bengali, every digit the user sees is a Bengali digit (০-৯), including times, dates, counts and offsets. | T-UNIT-9 |
| INV-L3 | Arabic text inside Bengali or English text renders right-to-left and does not reorder neighbouring punctuation or numbers. | T-UI-3 |
| INV-L4 | The layout does not clip or overlap at 200 % font scale **(target)**. | T-UI-4 |

### Qibla

| Id | Invariant | Test |
|---|---|---|
| INV-Q1 | The bearing shown equals the great-circle bearing from the stored location to the Kaaba, corrected for magnetic declination, to within 1° **(target)** before sensor error. | T-UNIT-10 |
| INV-Q2 | Whenever the sensor reports less than high accuracy, the needle is visibly marked unreliable and a calibration prompt is shown. | T-INST-12 |
| INV-Q3 | With no magnetometer, the screen shows the bearing in degrees and a sun-or-landmark hint instead of a needle. It never shows a needle it cannot trust. | T-INST-13 |

## Constraint table

| Area | Constraint | Label | Source or reason |
|---|---|---|---|
| Android versions | minSdk 26 (Android 8.0), as in the base | KNOWN for the base (`app/build.gradle`); ASSUMED sufficient for Bangladesh | Share of Bangladeshi phones below Android 8: UNKNOWN (Q8) |
| Target SDK | 37, as in the base | KNOWN | `app/build.gradle`. Play's current minimum target level: UNKNOWN, check Play Console at submission |
| OEMs in scope | Xiaomi/Redmi/Poco, Samsung, Realme/Oppo, Vivo, Infinix/Tecno/itel, Symphony/Walton | ASSUMED | Bangladesh market shares not fetched (Q8) |
| OEM behaviour | Xiaomi's MIUI adds undocumented background limits; fixes are user settings (autostart, "No restriction" battery, lock in recents) | KNOWN | https://dontkillmyapp.com/xiaomi |
| Minimum device | 2 GB RAM, 16 GB storage **(target)** | ASSUMED | Confirm with Q8 |
| Install size | ≤ 15 MB download **(target)**, including one Adhan recording | ASSUMED | Data cost is a stated product constraint |
| Offline | Everything in v1 works with the radio off, from first launch | Given | Positioning; INV-P1 |
| Languages | Bengali (default), English | Given | |
| Accessibility | Font scale to 200 %, TalkBack labels in Bengali, touch targets ≥ 48 dp **(targets)** | ASSUMED | TalkBack's quality in Bengali: UNKNOWN, test on device |
| Privacy | No ads, no analytics, no crash SDK, no account | Given | |
| License | App code Apache-2.0. Engine is LGPL-3.0 unless replaced | KNOWN | `00-base-audit.md` H1; ADR-2 |
| Upstream courtesy | UI must be a real redesign | KNOWN | `README.md:159-161` |
| Play: testing | New personal accounts (created after 13 Nov 2023) need a closed test with at least 12 testers opted in for 14 continuous days before production | KNOWN | https://support.google.com/googleplay/android-developer/answer/14151465 |
| Play: repetitive content | Apps that only replicate what is already on Play, without unique content or services, are not allowed | KNOWN | https://support.google.com/googleplay/android-developer/answer/9899034 |
| Play: exact alarms | Declare an exact-alarm permission only if core functionality needs it; `USE_EXACT_ALARM` is described for calendar and alarm-clock apps | KNOWN | https://support.google.com/googleplay/android-developer/answer/14554661, https://developer.android.com/about/versions/14/changes/schedule-exact-alarms |
| Play: exact alarms, defaults | `SCHEDULE_EXACT_ALARM` is not pre-granted on fresh installs targeting Android 13+, and is denied after a backup-restore to an Android 14 device | KNOWN | https://developer.android.com/develop/background-work/services/alarms/schedule |
| Play: Data safety form, foreground-service declaration | Required contents | UNKNOWN | Not fetched. Read in Play Console when creating the listing |
| Religious authority | A named, qualified reviewer signs off all content | Given | INV-C1 |
| Team | One or two developers, part-time; no backend operator | ASSUMED | Q1 |
| Budget, launch date | | UNKNOWN | Q1 |
