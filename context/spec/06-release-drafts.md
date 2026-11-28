# 06 · Release drafts: privacy page, Play declarations, open items

Drafted 2026-11-27 14:20 +0600 (clock). **Drafts for the founder and a lawyer, not for publication.** English only. The Bengali
version must be written by a native speaker, not by Claude (INV-L1, CLAUDE.md); nothing here may be machine-translated into the published page.
Labels are as in `00-base-audit.md`. Every statement of what the app does was checked against the manifest and code at
commit `cd502e7` plus the uncommitted working tree on 2026-11-27; the file is named beside each. Whether Play, a court or a regulator
*accepts* a statement is **ASSUMED** or **UNKNOWN** throughout. Nothing legal is settled here.

Placeholders you must fill: `[COMPANY OR DEVELOPER NAME]`, `[SUPPORT EMAIL]`, `[PRIVACY PAGE URL]`, `[DATE]`. None is invented (G15).

## A. What the app does with data (the facts the drafts rest on)

| Fact | Label | Where |
|---|---|---|
| The manifest requests eight permissions and not `INTERNET` (ADR-19 added `ACCESS_COARSE_LOCATION` to the seven of ADR-12) | KNOWN | `app/src/main/AndroidManifest.xml`; gate output "permission gate: passed, 8 permissions, no INTERNET." (2026-11-28 build) |
| No code makes a network request: no `INTERNET`, no HTTP client, no socket code found | KNOWN for the search; ASSUMED complete | `grep -rn -i "http" app/src/main/kotlin` found one hit, a documentation URL in a comment (`sensors/Declination.kt`); without `INTERNET` a normal app cannot open a socket (ASSUMED platform behaviour) |
| No analytics, ads, crash SDK or account: the only libraries are Kotlin and kotlinx-datetime (desugaring removed, ADR-14) | KNOWN | `05-licences.md` section 1; gate "4 libraries" |
| **Location is read only if the user taps "Find my district", once, only while the district screen is open; it is never stored and never sent.** Permission declared: `ACCESS_COARSE_LOCATION` only (ADR-19, reversing ADR-12). The district can always be picked by hand | KNOWN from the code; the on-device behaviour is not run | `app/.../ui/OneShotLocation.kt` (one `LocationManager` call; `stop()` from `DistrictActivity.onPause`), `ui/Districts.kt` (only the nearest district and a distance are kept in a field, never the position), `core/.../districts/Nearest.kt`; `grep -rn -i "LocationManager\|requestLocationUpdates" app/src/main/kotlin` finds only `OneShotLocation.kt` |
| While the phone works out a position for the app, the phone's own location service may use the network. That is the operating system, not this app, which has no `INTERNET` permission | ASSUMED (general Android behaviour; not tested) | Platform documentation not re-read here |
| **Vibration is never used, and `VIBRATE` is not declared** (removed, ADR-12) | KNOWN | `grep -rn -i "vibrator\|VibrationEffect"` found nothing |
| Stored on the phone, in the app's private storage: planner settings and preferences (language, silent-mode choice, onboarded flag, chosen district id, the place coordinates and zone, asr convention, offsets, sehri margin, per-prayer reminder modes and pre-reminder minutes) | KNOWN | `store/Settings.kt`, `store/AppPrefs.kt`; file `settings` (`SharedPreferences`, `MODE_PRIVATE`) |
| Stored: one flag "I did the brand's battery steps" | KNOWN | `store/Settings.kt` (`DeviceStore`, file `device`) |
| Stored: an alarm log, one row per planned alarm: prayer, kind, planned time, delivered time, outcome; rows older than 30 days are deleted on each replan | KNOWN | `store/Schema.kt`, `store/AlarmLog.kt` (`LOG_RETENTION` 30 days, `trim`), `alarm/Replan.kt:36` |
| Stored: deed days, one row per day a card was shown: local date, deed id, content version, swapped flag, time marked done | KNOWN | `store/Schema.kt` (`deed_day`) |
| Nothing identifies the user: no name, email, phone number, advertising id, or install id is collected or generated | KNOWN | Schema and prefs keys read in full; `grep -rn "ANDROID_ID\|UUID\|getDeviceId\|Settings.Secure\|InstanceId\|advertising" app/src/main/kotlin` found nothing (2026-11-27) |
| The only way information leaves the phone by the app's own action: the user taps "Send a report"; the app opens the user's email app with a **draft** the user can read and edit before sending. It has no recipient. It contains: app version, Android version and API level, phone manufacturer and model, the reminder-health result, the last seven days of the alarm log | KNOWN | `ui/SettingsActivity.kt` `sendReport` (`ACTION_SENDTO`, `mailto:`), `ui/Report.kt`. The report text holds no name or identifier |
| **Android's own backup may copy the settings file and the deed days to the user's Google account.** `allowBackup="true"` with rules that include `settings.xml` and `deed-days.db` and exclude the alarm log and the brand-steps flag | KNOWN | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml`, `res/xml/full_backup_content.xml`. **Founder decision (G41, 2026-11-27): backup is KEPT and must be disclosed** on the privacy page (the Android backup paragraph below stays; remove its `[REVIEW]` alternative) |
| The settings file includes the chosen district's coordinates (or the default, Dhaka's, until one is chosen) | KNOWN | `store/Settings.kt` `defaultSettings` and `place.lat/lon` keys. Coordinates are a district centre from the app's list, not the phone's position (ASSUMED from Q4's design; the real list does not exist yet) |
| The compass reads the magnetometer, rotation-vector, accelerometer sensors only while the Qibla screen is open | KNOWN | `sensors/CompassSensors.kt`: `start` from `onResume`, `stop` (`unregisterListener`) from `onPause` |
| The Adhan is a generated tone today; there is no audio file | KNOWN | `alarm/PlaybackService.kt` `sineWave`. This changes when Q5 is answered, and then this page must be re-checked (no new data flows are expected, ASSUMED) |

## B. Privacy page draft (English)

> **Privacy of Mu'mins DAY**
> Last updated: [DATE]
> Published by: [COMPANY OR DEVELOPER NAME]. Contact: [SUPPORT EMAIL]
>
> **The short version.** Mu'mins DAY does not collect your information. It does not need an account, does not show ads, and
> does not use analytics or crash-reporting services. The app has no permission to use the internet, so it cannot send anything
> by itself.
>
> **What stays on your phone.** The app saves your choices on your phone only: your language, your district, your prayer-time
> settings, which reminders are on, and the days you were shown or marked a daily deed, and the prayers you mark in the Salah tracker. It also keeps a list of the last 30 days
> of prayer reminders, with the time each one was planned and the time it arrived, so that the "Reminder health" screen can tell
> you whether reminders are reliable. This is stored in the app's private storage and is removed when you uninstall the app or clear its data.
>
> **Your location.** The app asks for your approximate location only if you tap "Find my district", and only then. It reads your position once, while that screen is open, picks the nearest district on your phone, and forgets the position at once. It is never saved and never sent anywhere; the app has no internet permission. The only thing kept is the district you choose. If you do not allow it, you choose your district from a list and everything works the same. [REVIEW: wording by the founder and, for Bengali, a native speaker.]
>
> **Sending a report.** In Settings, "Send a report" opens your own email app with a message already written. You can read and change
> it before sending, and you choose whether to send it. It contains the app version, your Android version, your phone's make and model, the
> reminder-health result and the last seven days of reminder times. It does not contain your name, your location or any identifier.
> If you send it, the email goes through your email provider to [SUPPORT EMAIL]; we use it only to fix reminders. [REVIEW: how long it is kept and
> who reads it must be decided and written here.]
>
> **Android backup.** If you have Android's backup turned on, Android may copy the app's saved choices and deed days to your Google
> account, and restore them on a new phone. The app does not control or see this; it is governed by your Google account settings.
>
> **Permissions.** The app asks Android for these permissions, only for the reasons given:
> exact alarms, to sound the Adhan at the right minute; start after restart, to set the reminders again after your phone restarts;
> notifications, to show the prayer notification; foreground service for media playback, to play the Adhan; keep awake, to keep the phone awake while
> the Adhan plays; that is all.
>
> **Children.** [UNKNOWN: whether the app is "designed for children" under Play's rules is the founder's and a lawyer's decision.]
>
> **Changes.** If this ever changes, for example if a future version uses the internet, we will update this page before the
> version is released, and say so in the release notes.
>
> **Contact.** [SUPPORT EMAIL]. [REVIEW: any legal contact or data-controller wording Bangladeshi or other law requires; UNKNOWN, for a lawyer.]

The Bengali page: to be written by a native speaker from this English source after the founder resolves the `[REVIEW]` items. Do not translate
by machine.

## C. Play Console drafts

Play's forms were **not read** in Play Console (no account, G14). Their questions and wording below are from the project's own notes and
general knowledge: **ASSUMED**. The only KNOWN Play facts are the ones in `01-constraints.md` (links there). Re-answer each against the live form.

### C1. Data safety form (draft)

| Question (as ASSUMED) | Draft answer | Reasoning |
|---|---|---|
| Does the app collect or share any of the required user data types? | **No** (draft) | The app sends nothing: no `INTERNET` permission (KNOWN). The only outbound path is a draft email the user sends themselves (section A). ASSUMED that a user-composed email through another app is not "collection by the developer"; UNKNOWN how Play's definitions treat the report email |
| Is all of the user data encrypted in transit? | Not applicable if "no data collected" | ASSUMED the question is skipped when nothing is collected |
| Can users request that data be deleted? | Not applicable / data is only on the device and removed by uninstall or "Clear data" | ASSUMED |
| Location | Not collected (draft). If the form asks about approximate location: processed only on the device, not transmitted, not stored | Read once on a user's tap, kept in memory for the duration of one function and discarded; the app has no `INTERNET` (KNOWN from the code and manifest). Whether Play's definition of "collected" excludes data that never leaves the device: UNKNOWN until read in the live form (Play's help page on Data safety says data processed only on the device is not collected, ASSUMED from general knowledge, not fetched here) |
| Backup | Android Auto Backup of settings and deed days to the user's Google account stays on and is disclosed (G41). Whether it counts against "no data collected" in Play's form: UNKNOWN until read there | Disclosed in the privacy page |
| Privacy policy URL | `[PRIVACY PAGE URL]` | G15 |

### C2. Exact alarm declaration (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`)

Draft answer, to be adapted to the live form:

> Core functionality of the app is sounding the Adhan, the Muslim call to prayer, and prayer reminders at the exact minute a prayer begins. A
> prayer time that is minutes late is a missed prayer, so inexact or batched alarms do not serve the user. The app sets only alarms
> for the next 48 hours of enabled prayers and cancels and re-creates them on every change, and does not use exact alarms for anything
> else (no advertising, no polling, no sync).

Reasoning and risks:
- KNOWN (Android docs, read 2026-11-27, `03-architecture.md` ADR-4): `USE_EXACT_ALARM` is granted automatically, cannot be revoked, and is "subject to an upcoming Google Play policy"; Play requires exact alarms to be core functionality (`01-constraints.md`, with URLs).
- **KNOWN, Play policy read 2026-11-28** (https://support.google.com/googleplay/android-developer/answer/16558241, "Permissions and APIs that Access Sensitive Information", section "Exact Alarm Permission"): `USE_EXACT_ALARM` "is a highly restricted permission used only for apps whose core, user-facing functionality genuinely requires precise timing, like dedicated alarm, timer, or calendar applications with event notifications". Acceptable use cases listed: "The app is an alarm or timer app" and "The app is a calendar app that shows event notifications". "If you have a use case for exact alarm functionality that's not covered above, you should evaluate if using SCHEDULE_EXACT_ALARM as an alternative is an option." The developer must "Complete Play Console declaration to indicate app functionality". Apps that do not meet the criteria "will be disallowed from publishing on Google Play".
- Reading of that text (ASSUMED, the founder decides, G51): an Adhan app's core is alarms at prayer times, so "an alarm app" is a fair description, but it is not named. Declaring `SCHEDULE_EXACT_ALARM` alone (on every Android version, with the in-app grant step already built) removes the review risk at the cost of one permission step for the user on Android 13 and later.
- UNKNOWN (unchanged): whether Play treats an Adhan or prayer-reminder app as an alarm-clock-type app for `USE_EXACT_ALARM`. The base (upstream) declares both and is on Play (KNOWN it declares both; its review history UNKNOWN).
- Fallback already built (KNOWN, ADR-4): the Degraded state and the grant flow for `SCHEDULE_EXACT_ALARM`, so dropping `USE_EXACT_ALARM` is a one-line manifest change plus an allowlist edit. A rejection costs a review cycle.
- Attach evidence: the Reminder-health screen and the late-alarm rule (INV-S7) show the app handles the case where exact alarms are denied honestly (T-UI-1: Not run).

### C3. Foreground service declaration (`mediaPlayback`)

**KNOWN, read 2026-11-28** (https://support.google.com/googleplay/android-developer/answer/13392821, "Understanding foreground service and full-screen intent requirements"): for each type the declaration asks to "Describe the user impact if: the task is deferred by the system … and/or the task is interrupted by the system", and to "Include a link to a video demonstrating each foreground service feature. The video should demonstrate the steps the user needs to take in your app in order to trigger the feature." For `TYPE_MEDIA_PLAYBACK`: "Continue audio or video playback from the background … Show a user starting video or audio playback. **Playback must be user-initiated**; any automatic soundtracks that play when you open an app do not qualify."

Risk (G52, founder): the Adhan starts from an alarm, not from a tap. It was set up by the user, which may or may not count as "user-initiated". The fallback is the `systemExempted` type, which Android documents for "Apps holding SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM permission" (KNOWN, developer.android.com/develop/background-work/services/fgs/service-types, read 2026-11-28 by a research subagent). The video for either: turn a prayer's Adhan on, then show it playing at the prayer time with the Stop control.


Draft answer:

> The foreground service, type mediaPlayback, plays the Adhan audio for its length (about a minute or less) when a prayer alarm fires and shows a
> notification with a Stop button. It starts only from a user-enabled prayer alarm or from the user's own "Test Adhan" button, runs only while
> the audio plays, and stops itself when the audio ends, when the user taps Stop, or when the user opens the app. It does not run in the
> background for any other purpose.

KNOWN facts for the answer: `alarm/PlaybackService.kt` declares `foregroundServiceType="mediaPlayback"` in the manifest, calls `startForeground` first, plays
a single `AudioTrack` with `USAGE_ALARM` or `USAGE_NOTIFICATION`, holds a partial wake lock no longer than the sound plus 10 seconds, requests audio focus, and stops itself with a handler timeout
(`sound.length + 300 ms`), the Stop action, or opening the app. The sound length of the real recording is UNKNOWN (Q5; the tone's length is set by `Sound.length`, not re-read here).
Whether Play wants a demo video of the feature for this declaration: ASSUMED yes for some types; UNKNOWN. M-1 on a phone can supply it.
Not KNOWN to be accepted: UNKNOWN until submitted.

## D. UNKNOWNs and decisions for the founder or a lawyer

1. **Backup.** DECIDED (G41): kept and disclosed. The manifest has `allowBackup="true"` with rules that back up settings and deed days (KNOWN).
2. DECIDED: `VIBRATE` removed (ADR-12). `ACCESS_COARSE_LOCATION` was removed there and came back on 2026-11-28 for "find my district" only (ADR-17 item 3, ADR-19). Play's permission declaration for location (when it applies, and whether a prominent in-app disclosure is required before the system prompt): UNKNOWN, to read in the live form; the screen already says what is read and what is kept before the button.
3. Support email, privacy page URL, company or developer name, last-updated date (G15, G14). Founder.
4. Whether the report email's retention, reader and purpose must be stated more fully, and by what law. Lawyer.
5. Whether Bangladeshi, Play, or other rules require any further statement (data-controller identity, a data-protection act). **UNKNOWN; for a lawyer.** Nothing here is legal advice.
6. Whether the Play Data safety form's definitions count the user-sent report email, device backup, or the on-device alarm log as "collection". Partly answered 2026-11-28 (https://support.google.com/googleplay/android-developer/answer/10787469): for data a user uploads "directly to their own external drive or cloud storage account … governed by the external drive or cloud storage provider's terms … and your app never collects or accesses the data in question, then your app does not need to declare the collection of this data." Android's own backup to the user's Google account looks like that case (ASSUMED by analogy; the page does not name Auto Backup).
6a. **Closed testing, KNOWN 2026-11-28** (https://support.google.com/googleplay/android-developer/answer/14151465): "Developers with personal accounts created after November 13, 2023, must run a closed test for their app with a minimum of 12 testers who have been opted in continuously for at least 14 days." Production access is applied for on the Dashboard after that.
6b. **Target API, KNOWN 2026-11-28** (developer.android.com/google/play/requirements/target-sdk, research subagent): "Starting August 31 2026: New apps and app updates must target Android 16 (API level 36) or higher". The app targets 37.
7. Whether Play accepts `USE_EXACT_ALARM` for this app (ADR-4), and what the foreground-service declaration asks for in a demo. UNKNOWN until submitted; the plan has a fallback.
8. "Designed for children" status and the content-rating questionnaire. UNKNOWN; founder.
9. The Bengali privacy page: native speaker, after items 1 to 5 are decided.
10. LGPL text is now in the app (ADR-13); the Classpath-Exception library is removed (ADR-14). Apache notices and the lawyer's reading: see `05-licences.md`, Q6.
11. Whether the Adhan recording or the district data licence adds any statement the page must make (Q4, Q5).

## E. Adhan recording candidates (Q5), found 2026-11-28

Searched Wikimedia Commons by its API (`list=search` in the File namespace for adhan, azan, athan, "call to prayer", then `imageinfo` with `extmetadata`). Licences are as the file page states them (KNOWN for the statement, ASSUMED for its truth: Commons licences are given by the uploader). **Nothing was downloaded or bundled.** The founder chooses after listening; a recording made for the app, licensed in writing, is still the safest answer.

| File (commons.wikimedia.org/wiki/…) | Licence stated | Author / uploader | Length | Note |
|---|---|---|---|---|
| `File:Beautiful_adhan.ogg` | CC0 | Adam-synagda / same | 2 min 34 s, 1.2 MB | Best fit so far: CC0 needs no credit; author and uploader are the same account. Reciter not named |
| `File:Adhan.ogg` | CC0 | Aishatu98 / same | 42 s | Short; may be partial. Same uploader also gives `File:Iqamah.ogg` (CC0) |
| `File:Adhan_in_Istanbul.webm` | CC0 | Viceskeeni2 / same | 50 s | A street recording inside a 65 MB video; audio would need extracting |
| `File:Azan.ogg` | CC BY-SA 4.0 | Andrewler / same | 3 min 3 s | Share-alike: the credit and licence must travel with the audio; a lawyer should say whether that reaches the app (Q6) |
| `File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3` | CC BY-SA 4.0 | Atcovi / same | 1 min 27 s | Reciter named in the title; same share-alike question |
| `File:Call_to_prayer_by_Sabah_Fakhry.mp3` | "Public domain" | Sabah Fakhri / Maher27777 | 2 min 38 s | **Risky:** uploaded by someone other than the performer; a modern recording is unlikely to be public domain |
| `File:Oldest_recording_of_the_Quran_1885.oga` | Public domain | 1885 recording | 1 min 30 s | Historic; too poor for a phone alarm |

No short notification tone was searched for; the app's tone can be a synthesised beep made for it.

## F. When `INTERNET` lands for packs (ADR-25): drafts that do not apply yet

**Not in force.** Today the app has no `INTERNET` permission, `PACK_HOST` is empty and the pack manifest is empty
(KNOWN: `app/src/main/AndroidManifest.xml`, `app/.../packs/PackHost.kt`, `app/src/main/assets/packs/manifest.txt`,
`NetworkSurfaceTest`, 2026-11-28). Sections A to C above stay true until the one commit ADR-25 describes. Then these
replace the matching parts. `[HOST]` is the founder's to fill; nothing here names a host.

**F1. Facts the drafts would rest on** (KNOWN from the code as built 2026-11-28; on a phone: not run)

| Fact | Where |
|---|---|
| A request is made only after a tap on Download or Continue on the "Downloadable packs" screen; nothing downloads in the background or on a schedule | `PacksActivity` is the only caller of `PackDownloads.start` (`NetworkSurfaceTest`) |
| Requests go only to `[HOST]`, over https, for files listed in the manifest bundled in the app; redirects are not followed; plain http is refused by the code and by the network security config | `fetchFile`, `packUrl`, `network_security_config.xml` |
| A request carries the file's path, `User-Agent: packs`, `Accept-Encoding: identity`, and `Range` when continuing; no identifier, cookie, account or query string | `fetchFile`, `DownloaderTest` |
| The server necessarily sees the phone's IP address and the time of the request (ASSUMED: true of any internet request; what `[HOST]` logs and keeps is the host's policy, UNKNOWN) | — |
| Downloaded packs are stored in the app's private files, are not backed up, are deleted by "Delete from the phone", by "Clear data" or by uninstalling | `PackFiles`, backup rules exclude the `file` domain |
| A pack is used only if every file's SHA-256 matches the manifest; otherwise it is deleted and the screen says so | `PackFiles.accept`, `PackFiles.verified` |

**F2. Privacy page, replacing "The short version" and adding a section (draft)**

> **The short version.** Mu'mins DAY does not collect your information. It does not need an account, does not show ads,
> and does not use analytics or crash-reporting services. Everything works without the internet. The app uses the
> internet for one thing only: if you tap Download on the "Downloadable packs" screen, it downloads that pack from
> [HOST].
>
> **Downloadable packs.** A download starts only when you tap. The app asks [HOST] for the pack's files and sends
> nothing about you: no name, no account, no identifier of your phone. Like any server, [HOST] can see your internet
> address and when you downloaded. [REVIEW: what [HOST] logs and how long it keeps it, from the host's own terms.] Packs
> stay on your phone, are not backed up, and are removed when you delete them in the app, clear its data or uninstall
> it. If you never tap Download, the app never connects to the internet.

The Permissions paragraph gains: "internet, only to download a pack you chose".

**F3. Data safety form (draft; Play's live form not read, G14)**

| Question (as ASSUMED) | Draft answer | Reasoning |
|---|---|---|
| Does the app collect or share user data? | **No** (draft) | Requests carry no user data; the IP address reaching a server is a property of any connection. UNKNOWN whether Play counts a download server's access logs as collection by the developer; read the live form's definitions |
| Is data encrypted in transit? | Yes | https only, cleartext refused (ADR-25) |
| Does the app use the internet? | Yes, for optional downloads the user starts | |

**F4. Open before that commit:** the host and who runs it (`[HOST]`); its logging policy; the pack's written permission
in `content/`; the founder's yes recorded in the ADR; Play's live form re-read; the Bengali page by a native speaker.

