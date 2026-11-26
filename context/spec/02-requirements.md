# 02 · Requirements

Labels and shorthand are as in `00-base-audit.md`. "Sheet #n" means finding n in the "Muslim app: findings" tab of the project Sheet; those were researched earlier and **not re-fetched in this run**. Numbers marked **(target)** are proposals to confirm on devices.

Each feature starts with the case for cutting it. It stays only if that case fails.

---

## F1 · Reliable Adhan and reminders

**Problem, in the user's words.** "The Adhan didn't ring. I missed the prayer and I don't trust the app any more." Evidence: Adhan reliability is a top Android complaint against Muslim Pro, tied to battery optimisation (Sheet #5). MIUI adds undocumented background limits that break apps with default settings (KNOWN, https://dontkillmyapp.com/xiaomi).

**The case for cutting it.** The phone's own clock app can ring five alarms. *Fails:* prayer times change every day, so a static alarm is wrong within a week. Without this feature the other three have no reason to be opened daily.

**Failure modes first.** What must happen in each:

| # | Situation | Required behaviour | Base today |
|---|---|---|---|
| 1 | Doze / idle | Adhan within the same minute | Uses `setAlarmClock`, which exits low-power mode (KNOWN) |
| 2 | OEM task killer swipes or freezes the app | Next alarm still fires. If the OEM blocks it, the user is told at next open, with the brand-specific fix | No detection, one generic dialog (KNOWN, audit §3) |
| 3 | Exact-alarm permission denied or revoked | Home screen shows a persistent, plain-language warning with a one-tap fix. No silent inexact fallback | Silent fallback (S4) |
| 4 | Notification permission denied | Same: visible warning. Adhan audio does not depend on the notification being shown (ASSUMED possible via the playback service; verify on Android 13+) | Not surfaced |
| 5 | Location unavailable | Works from the stored district. Location is never needed to ring | Needs network for first setup (audit §6) |
| 6 | GPS spoofing / wild fix | A fix outside Bangladesh, or more than 50 km **(target)** from the stored point, asks before changing the district | Auto-updates |
| 7 | Travel between districts | Offer to switch, once, when the nearest district changes. Never switch silently | Auto-updates when "auto location" is on |
| 8 | Phone's time zone is not Asia/Dhaka | Alarms fire at the true instant for the chosen place (INV-S2) | Wrong instant (S3) |
| 9 | Manual clock change, time-zone change | Reschedule within 60 s | Handled (KNOWN) |
| 10 | Reboot | Reschedule on boot, including before first unlock if that proves necessary | Boot handled; pre-unlock not (audit §3) |
| 11 | App update | Reschedule | Not handled |
| 12 | Restore from backup onto a new phone | Exact-alarm permission is denied after restore on Android 14 (KNOWN, alarms doc). Detect and show warning #3 | Not handled |
| 13 | Midnight boundary (Isha after 00:00, Fajr of next day) | Correct day attribution; the 24-hour window always contains the next Fajr | Heuristic in `A/times/times/DayTimesProvider.kt` (`h < 5` → next day) |
| 14 | High latitude | Out of scope: Bangladesh only. Engine returns "not computable" rather than 00:00 (INV-T7) | Returns 00:00 |
| 15 | Silent mode / Do Not Disturb | User picks one of two modes at setup: "Respect silent mode" (default) or "Always ring" (alarm stream). The choice is stated on the settings screen | Per-alarm volume stream setting (KNOWN, `A/times/alarm/sounds/MyPlayer.kt`) |
| 16 | Phone call in progress | No Adhan audio; silent notification | UNKNOWN (read `MyPlayer.kt` audio-focus handling) |
| 17 | Alarm delivered late | INV-S7: play if ≤ 10 min late, else silent notification | Plays whenever delivered |
| 18 | Force stop | Cannot be fixed. Onboarding says so in one sentence | Not mentioned |

**User stories and checks.**

- *As a user I get the Adhan at each prayer I chose.* Check: with the app swiped away and battery saver on, each of five alarms fires within 60 s of the displayed time on every phone in the device matrix, for 7 consecutive days **(target: 100 % on reference phones, ≥ 99 % elsewhere; any miss is a bug with a log entry)**.
- *As a user I can prove to myself it works.* Check: "Test Adhan" schedules a real alarm 30 s ahead through the same code path; it rings with the screen off.
- *As a user on a Xiaomi/Realme/Infinix phone I am shown exactly which switches to flip.* Check: onboarding shows steps for the detected manufacturer in Bengali and deep-links to the settings screen where one exists. (Per-brand steps: KNOWN for Xiaomi from dontkillmyapp.com; other brands UNKNOWN, fetch each page.)
- *As a user I can see whether last week's alarms fired on time.* Check: a "Reminder health" screen lists planned and actual times for 7 days (INV-S8).
- *As a user I choose per prayer: Adhan, short tone, silent notification, or off; and an optional reminder N minutes before.*

**Data.** One Adhan recording and one short tone, bundled. License: UNKNOWN until one is commissioned or licensed in writing (Q5). The base's downloadable sounds must not be reused (audit H6).

**Non-functional (targets).** Alarm-to-audio latency ≤ 3 s. Scheduler run ≤ 50 ms. No foreground service outside playback. Battery: below 1 % per day attributable to the app with no screen-on use.

**Out of scope.** Multiple cities. Custom sound import. Auto-silencing the phone during prayer. Face-down to stop. Wear OS. Jamaat times.

**Tests.** Unit and property tests on the planner (pure). Instrumented tests T-INST-1…10. Manual device matrix M-1.

---

## F2 · Bangladesh-correct prayer times

**Problem.** "The time in the app doesn't match my mosque." A wrong method or Asr convention makes the app untrusted locally (Sheet #12). The base defaults to Shafi Asr (KNOWN, `Times.kt:52`).

**The case for cutting it.** It is F1's input. Not cuttable. What *is* cut: every method except Karachi, and every non-Bangladesh city.

**Failure modes first.**

| # | Situation | Required behaviour |
|---|---|---|
| 1 | The calculated time differs from the local mosque by a few minutes | Per-prayer offset, ±30 min, on one screen, with "matches my mosque" as the framing (INV-T6) |
| 2 | The user never grants location | Pick a district from a bundled list of 64. Works fully offline |
| 3 | Noisy GPS altitude | Elevation is fixed per district, not read from GPS (base passes GPS altitude: KNOWN, audit §2) |
| 4 | Engine cannot compute | Show "—", set no alarm, log it (INV-T7) |
| 5 | Ramadan | Sehri end and Iftar are the two largest items on the home screen; countdown to the next one |
| 6 | Displayed Fajr is rounded down | Show Sehri end and Fajr start separately (INV-T5, Q3) |
| 7 | An official timetable disagrees with Karachi + Hanafi | UNKNOWN whether it does. Decide after the year-long diff (Q2). The design allows a bundled timetable to override calculation per district without a code change |

**User stories and checks.**

- *On first launch I see today's five times for my district in Bengali within one screen.* Check: fresh install, airplane mode, choose "ঢাকা", times appear; all digits Bengali.
- *Defaults are right for Bangladesh.* Check: method Karachi (18°/18°), Asr Hanafi, zone Asia/Dhaka, with no setting touched.
- *I can match my mosque.* Check: set Asr +2; only Asr moves, by 2 minutes, on screen and in the alarm.
- *In Ramadan I see Sehri end and Iftar.* Check: both shown with countdown; Iftar equals Maghrib (INV-T2).

**Data.** (a) The engine: LGPL-3.0 today (ADR-2). (b) District list with latitude, longitude, elevation and Bengali/English names: source and license UNKNOWN (Q4). (c) A reference timetable to test against: UNKNOWN (Q2).

**Non-functional (targets).** A full year of times for one district computed in under 100 ms on the minimum device. Zero network.

**Out of scope.** Other countries. Other methods (one hidden "advanced" switch for Shafi Asr only). Forbidden-time indicators. Tahajjud, Ishraq and other extra times. Monthly export.

**Tests.** T-PROP-1, 3, 6; T-UNIT-2, 5, 11; T-FIX-4.

---

## F3 · Qibla compass that admits when it is wrong

**Problem.** "The arrow pointed a different way each time." Phone compasses drift, and a wrong Qibla costs trust (Sheet #13).

**The case for cutting it.** In Bangladesh most people pray at home or at a mosque where the direction is already known, so daily use is low. *This case is strong.* It survives for three reasons: it is expected of any app in this category; the travelling or newly-moved user needs it exactly when nothing else helps; and the base already has the sensor code. It is kept at the smallest honest size: **one screen, no map, no camera view.** If the schedule slips, this is the first feature to move to v1.1.

**Failure modes first.**

| # | Situation | Required behaviour |
|---|---|---|
| 1 | Sensor accuracy below high | Needle greyed, text "কম্পাস ঠিক নেই" style warning, calibration animation (INV-Q2). Base opens a calibration panel (KNOWN) |
| 2 | Magnetic interference (metal, magnets, cases) | Detect a field strength outside the plausible Earth range and warn. Thresholds: UNKNOWN, take from sensor documentation |
| 3 | No magnetometer | Show the bearing as a number and compass point; no needle (INV-Q3) |
| 4 | No location | Use the stored district. Bearing varies little across one district (ASSUMED; compute the spread) |
| 5 | Phone not held flat | Prompt to lay it flat |
| 6 | True vs magnetic north | Apply declination on the device (base does not: KNOWN, audit §4). Size of the error in Bangladesh: UNKNOWN, compute with the platform's geomagnetic model |

**User stories and checks.**

- *I see which way to face and how sure the app is.* Check: on a calibrated phone the needle is within 5° **(target)** of a surveyed bearing at a known point; with a magnet nearby the warning appears within 2 s.
- *I always see the bearing as a number*, e.g. "পশ্চিম থেকে ৮° উত্তরে", so I can check against a physical compass.

**Data.** Kaaba coordinates (base uses 21.42247, 39.826198: KNOWN, `A/compass/MainFragment.kt:198-199`). Geomagnetic model from the Android platform (ASSUMED available offline; verify).

**Out of scope.** Map view, camera/AR view, distance to Makkah, sun-position method.

**Tests.** T-UNIT-10, T-INST-12, T-INST-13, manual M-3.

---

## F4 · One good deed a day

**Problem.** "I want to be better but I don't know where to start, and long checklists make me give up." This is the product's signature; nothing in the competitor set was found doing it this way (earlier research, not re-fetched).

**Basis.** Earlier in this project three narrations were verified on sunnah.com as the grounding: daily charity due on every joint (Sahih al-Bukhari 2989, also in Muslim), not belittling any good deed (Sahih Muslim 2626), and the most beloved deeds being the consistent ones even if small (Sahih Muslim 782/783, Sahih al-Bukhari 6464/6465). **I do not write the deeds, their wording, or their gradings.** The reviewer does (INV-C1).

**The case for cutting it.** It adds a content pipeline, a reviewer dependency and theological risk to an otherwise mechanical app. *Fails:* without it the app is one more prayer-times fork, which is also the Play "repetitive content" risk (KNOWN policy, https://support.google.com/googleplay/android-developer/answer/9899034) and ignores the upstream author's request. This is the unique content. It stays, at one card.

**Failure modes first.**

| # | Situation | Required behaviour |
|---|---|---|
| 1 | A deed with a wrong or weak source ships | Impossible by construction: build fails without `approved` status, reviewer and date (INV-C1) |
| 2 | The user misses days | Nothing happens. No count of missed days, no "streak lost". Today's card is simply today's card (INV-C4) |
| 3 | A deed is impossible for this user today (no money, housebound, no one nearby) | "Show me another" swaps once per day to the deed half a cycle away (amended 2026-11-26, G4; was "the next deed", which is tomorrow's). No penalty |
| 4 | Clock change or travel | The deed follows the local calendar date. Moving the clock back does not un-complete a day |
| 5 | A deed is later found to be wrong | It can be retired in a content update without a code change. Past completions remain; the text is no longer shown |
| 6 | The same deed keeps appearing | Within one content version the daily deed appears once per cycle. A requested alternate is the deed farthest from today, so it was last seen about half a cycle ago. A content update that changes the count moves the position once (amended 2026-11-26, G4, G5) |
| 7 | Translation copyright | Deed text is original wording written for the app in Bengali and English. Hadith are cited by book and number, not quoted from a copyrighted translation |
| 8 | Reward-chasing design | No points, badges, leaderboards or sharing of counts |

**Data model.**

| Field | Type | Rule |
|---|---|---|
| `id` | stable string | Never reused |
| `text_bn`, `text_en` | short text | One action, doable today, ≤ 90 characters **(target)** |
| `source_book` | enum: Bukhari, Muslim | v1 allows only these two |
| `source_number` | string | As numbered in the edition named in `source_edition` |
| `source_edition` | string | Numbering differs between editions, so it must be named |
| `grading` | text | As recorded by the reviewer |
| `reviewer` | name | Required |
| `review_date` | date | Required |
| `status` | draft · approved · retired | Only `approved` ships and shows |
| `added_in`, `retired_in` | content version | For audit |

**Rotation rule.** `index = daysSinceEpoch(localDate) mod N` over the approved deeds in file order for that content version, where N is the count (at least 30). "Show me another" uses `(index + N/2) mod N`. Deterministic, offline, identical on all phones (INV-C2). The card is recorded the first time it is shown each day, so an update cannot change it; the one exception is a card whose deed the update retired, which is recomputed. (Amended 2026-11-26, G4, G5.)

**Review gate.**

1. Deeds live in one file in the repository: `content/deeds.json`.
2. A build check rejects any record missing a field or not `approved` (INV-C1).
3. The reviewer signs a dated note listing content version and record ids; the note is committed beside the file.
4. Adding or retiring a deed is a change to that file plus a new signed note. Nothing else.

**User stories and checks.**

- *Each day I see one small deed, in Bengali, with where it comes from.* Check: card shows text, book and number; all digits Bengali.
- *One tap marks it done, and the card rests until tomorrow.* Check: after tapping, the card shows a quiet "done" state; no further prompt today.
- *An optional single daily nudge at a time I choose.* Check: off by default; when on, exactly one notification per day, no repeat if ignored.
- *A soft week view.* Check: seven dots, filled or empty, no numbers, no percentages.

**Non-functional (targets).** Card renders in under 100 ms. Content file under 50 KB.

**Out of scope.** Custom deeds, social sharing, streak counts, deed categories, more than one deed a day, hadith text display (that is the later Hadith feature).

**Tests.** T-BUILD-4, T-PROP-8, T-UI-2.

---

## "Next" features: stubs

Design seams for these are in `03-architecture.md`. None is built in v1.

| Feature | Problem | Blocking unknown | Seam |
|---|---|---|---|
| Bangla Quran with tafsir, audio, offline | Core need; existing Bangla apps are cluttered (Sheet #7) | Written permission for a Bengali translation. Tanzil's Bengali texts are non-commercial only; QuranEnc requires unmodified text with credit (both KNOWN from earlier fetches) | Content packs (downloaded, verified, stored on device). First feature that needs `INTERNET` |
| Verse search | Users missed it when a competitor removed it (Sheet #3) | Depends on the Quran text | On-device full-text index |
| Daily duas, Hisnul Muslim | Short daily practice (Sheet #7) | Source text and translation license; reviewer | Same content model and review gate as deeds |
| Daily hadith with source and grade | Weak or mis-cited narrations (Sheet #13) | Data source terms (sunnah.com terms unverified) | Same content model |
| Salah tracker | Help staying consistent (Sheet #11) | None technical. Tone rules of INV-C4 apply | Local table; "Prayed" action on the Adhan notification |
| Khatam planner | Few finish (Sheet #6) | Depends on Quran | Local table |
| Zakat with bhori/ana | Local units (Sheet #7) | Reviewer sign-off on the calculation rules | Pure calculator, no data |
| Hijri calendar, ±1 day | Moon sighting differs by country (Sheet #12) | Which authority's announcement to follow | `hijri` engine module is Apache-2.0 (KNOWN) |
| Widgets | Next prayer at a glance (Sheet #1) | None | Reads the same planner output |
