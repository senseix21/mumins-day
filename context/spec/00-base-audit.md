# 00 · Base audit

**What was read.** `metinkale38/prayer-times-android` at commit `0b2a8e7` (17 Nov 2026) and `metinkale38/open-prayer-times` at the pinned commit `a136f9b` (8 Nov 2026).

**Labels.** `KNOWN` = read in a file (path given) or a fetched page (URL given). `ASSUMED` = my reasoning or general knowledge, not checked here. `UNKNOWN` = not established; the "how to find out" is given.

**Path shorthand.** `A/` = `app/src/main/java/com/metinkale/prayer/`. `E/` = `open-prayer-times/calctimes/src/main/kotlin/dev/metinkale/calctimes/`.

## Headline findings

| # | Finding | Label | Evidence |
|---|---|---|---|
| H1 | The calculation engine is **LGPL-3.0**, not Apache-2.0. Only the app and the other engine modules are Apache-2.0. | KNOWN | `open-prayer-times/README.md:13`, `calctimes/NOTICE.txt`, `calctimes/build.gradle.kts` (POM license) |
| H2 | Bengali exists but is about one third done: 107 of 291 translatable keys, and 5 of those 107 contain no Bengali characters. | KNOWN | counted over `app/src/main/translations/values-bn/*.xml` against `app/src/main/res/values/*.xml` |
| H3 | Karachi (Fajr 18°, Isha 18°) and Hanafi Asr both exist. The default Asr is **Shafi**. | KNOWN | `E/Method.kt` (Karachi), `E/CalcTimes.kt` (`asrTime(jdate, 2, …)`), `A/times/times/Times.kt:52` |
| H4 | Only **one** alarm is registered with the system at a time: the next one. Each alarm, when it fires, schedules its successor. | KNOWN | `A/times/times/Alarms.kt` (`setAlarms`), `A/times/alarm/AlarmService.kt:175` (single `PendingIntent`, request code 0) |
| H5 | Using GPS sends the user's coordinates to the author's server, even when times are calculated on the device. | KNOWN | `A/times/LocationUtil.kt:109` → `OpenPrayerTimes.search(lat, lng)`; base URL `https://opt.mk38.de` in `open-prayer-times/client/.../OpenPrayerTimes.kt` |
| H6 | No Adhan audio is bundled. Sounds and the hadith database are downloaded from the author's GitHub Pages site. | KNOWN | `A/times/alarm/sounds/Sounds.kt:90`, `A/hadith/MainActivity.kt:42`, `A/App.kt:91`; no audio files under `app/src/main` |
| H7 | The Play flavour ships Sentry crash reporting with a persistent per-install UUID; the F-Droid flavour has none. | KNOWN | `app/src/play/java/.../CrashReporter.kt`, `A/App.kt:63`, `app/src/fdroid/java/.../CrashReporter.kt` |
| H8 | The build could not be run in this environment. | KNOWN | No Android SDK here; `services.gradle.org`, `dl.google.com` and Maven Central were unreachable |

## 1. Build system

| Item | Value | Label | Evidence |
|---|---|---|---|
| Modules | `:app`, `:wear`, `:sync` | KNOWN | `settings.gradle` |
| Build | Gradle 9.6.1 wrapper, Android Gradle Plugin 9.3.1, Kotlin 2.4.10, KSP 2.3.10 | KNOWN | `gradle/wrapper/gradle-wrapper.properties`, `build.gradle` |
| JDK | 17 | KNOWN | `BUILD_INSTRUCTIONS.md`, `app/build.gradle` |
| SDK | min 26, target 37, compile 37 | KNOWN | `app/build.gradle` |
| Version | 3.9.0 (code 257), application id `com.metinkale.prayer` | KNOWN | `app/build.gradle` |
| Flavours | `fdroid` and `play` (default) | KNOWN | `app/build.gradle` |
| Engine dependency | Not a submodule. Cloned at a pinned commit and published to `mavenLocal()` as `1.0-SNAPSHOT` | KNOWN | `BUILD_INSTRUCTIONS.md`, `ci_build.sh`, `open-prayer-times.commit` |
| Secrets | `secrets.xml` with `GOOGLE_API_KEY` and `SENTRY_DSN`; empty values are enough for F-Droid | KNOWN | `BUILD_INSTRUCTIONS.md` |
| Memory | Gradle heap capped at 3 GB, one worker | KNOWN | `gradle.properties` |
| Does it build today? | **Not run.** | UNKNOWN | Run `bash ci_build.sh fdroid` on a machine with JDK 17 and SDK platform 37 |

Code size: 194 Kotlin and Java source files under `app/src`; Kotlin files total about 16,700 lines (KNOWN, counted).

## 2. Prayer time engine

- **Location.** `E/CalcTimes.kt`, `E/Method.kt`, `E/PrayerTimes.kt`. A Kotlin port of PrayTimes.js 2.3 (KNOWN, `calctimes/NOTICE.txt`).
- **Signature.** `CalcTimes(latitude, longitude, elevation, timezone, method).getPrayerTimes(date): PrayerTimes` (KNOWN, `E/CalcTimes.kt`).
- **Output.** imsak, fajr, sunrise, zawal, dhuhr, asrShafi, asrHanafi, sunset, maghrib, ishaa, the night thirds, midnight, and four "high-latitude adjustment used" flags (KNOWN, `E/PrayerTimes.kt`).
- **Purity.** No I/O, no clock read, no Android imports. Same input gives same output (KNOWN, read in full).
- **Methods.** MWL, ISNA, Egypt, Makkah, Karachi, UOIF, Russia, Malaysia, Singapore, Tehran, Jafari (KNOWN, `E/Method.kt`).
- **Karachi.** `fajrAngle = 18.0`, `imsakAngle = 18.0`, `ishaaAngle = 18.0`, Maghrib at sunset (KNOWN, `E/Method.kt`).
- **Asr.** Both computed every time: shadow factor 1 (Shafi) and 2 (Hanafi) (KNOWN, `E/CalcTimes.kt`). The app picks one via `Times.asrType` (KNOWN, `A/times/times/DayTimesCalcProvider.kt`).
- **Sehri.** For Karachi, imsak equals Fajr: same angle, zero minutes (KNOWN, `E/Method.kt`). The app shows imsak as its "Fajr" slot (KNOWN, `DayTimesCalcProvider.kt`: `fajr = it.imsak`).
- **Offsets.** For calculated times, per-prayer minutes live inside the `Method` (`fajrMinute`, `dhuhrMinute`, …) and are edited in `A/times/calc/PrayTimesConfigurationView.kt` (KNOWN). `Times.minuteAdj` is applied only to downloaded timetables (KNOWN, `A/times/times/DayTimesWebProvider.kt:32`; `DayTimesCalcProvider.kt` never reads it).
- **Elevation.** Sunrise and sunset use `0.833 + 0.0347·√elevation` when `useElevation` is true, which is the default (KNOWN, `E/CalcTimes.kt`). GPS altitude is passed in (KNOWN, `LocationUtil.kt`). Noisy altitude will move Maghrib by seconds to a minute (ASSUMED; measure it).
- **Rounding.** imsak, Fajr and sunrise round down to the minute; Dhuhr, both Asr, Maghrib and Isha round up (KNOWN, `E/DoubleTimes.kt`). Rounding Fajr down is safe as a "stop eating" time and slightly unsafe as a "prayer may begin" time.
- **Uncomputable times.** A NaN becomes `00:00` with no error (KNOWN, `E/DoubleTimes.kt`).
- **High latitude.** Default is `None`. Not relevant at Bangladesh's latitude (ASSUMED).
- **Tests.** Two: preset angles, and one Berlin fixture for one date (KNOWN, `calctimes/src/test/kotlin/CalculationTest.kt`, 47 lines). **No Bangladesh fixture, no property tests.**
- **Agreement with the Islamic Foundation timetable.** UNKNOWN. Get the official district timetable and diff it against Karachi + Hanafi for a full year.

## 3. Scheduling

**Mechanism (KNOWN).**

1. `Times.setAlarms()` finds the single earliest enabled alarm across all cities and hands it to `AlarmService.setAlarm` (`A/times/times/Alarms.kt`).
2. `MyAlarmManager.setExact` picks the API (`A/MyAlarmManager.kt`):
   - `Preferences.USE_ALARM` is true by default (`A/Preferences.kt:63`) → `setAlarmClock`.
   - else, exact permission missing → plain inexact `set`, with no message to the user.
   - else → `setExactAndAllowWhileIdle`.
3. The alarm starts `AlarmService` through `PendingIntent.getForegroundService`. The service takes a 10-minute wake lock, plays the sound as a `mediaPlayback` foreground service, then calls `Times.setAlarms()` again (`A/times/alarm/AlarmService.kt`).
4. Rescheduling also happens on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` (`A/AppBroadcastReceiver.kt`, `A/times/TimesBroadcastReceiver.kt`), on a preference change, after a location update, and from a daily `WorkManager` job (`A/times/times/SyncTimesWorker.kt`).

**Event table.**

| Event | What the code does | Label | Evidence |
|---|---|---|---|
| Reboot | Receiver reschedules | KNOWN | `AndroidManifest.xml:546-556` |
| Reboot, before first unlock | Not handled. The manifest action string is `android.intent.action.ACTION_LOCKED_BOOT_COMPLETED` and no component is `directBootAware`. | KNOWN (the string and the missing attribute); ASSUMED (that the string is wrong: the platform constant should read `…LOCKED_BOOT_COMPLETED`) | `AndroidManifest.xml:551`; grep for `directBootAware` returns nothing |
| Time-zone change | Reschedules | KNOWN | `AppBroadcastReceiver.kt` |
| Manual clock change | Reschedules | KNOWN | same |
| Date change | Registered in the manifest but falls into the "time tick" branch, not the reschedule branch. `DATE_CHANGED` is also not on Android's list of broadcasts a manifest receiver may get. | KNOWN | `AndroidManifest.xml:554`, `AppBroadcastReceiver.kt`; https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions |
| App update | No `MY_PACKAGE_REPLACED` handler | KNOWN | grep returns nothing |
| Does a pending alarm survive an app update? | | UNKNOWN | Instrumented test: set alarm, `adb install -r`, inspect `adb shell dumpsys alarm` |
| Force stop | Nothing can run until the user opens the app | ASSUMED | Android behaviour; the fetched alarms page does not cover it |
| Exact-alarm permission granted later | No handler for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | KNOWN | grep returns nothing |
| Exact-alarm permission revoked | Android stops the app and cancels its exact alarms | KNOWN | https://developer.android.com/develop/background-work/services/alarms/schedule |
| Doze | `setAlarmClock` leaves low-power mode to deliver | KNOWN | same URL |
| OEM task killers | No detection. One dialog asks the user to disable battery optimisation when an alarm is enabled. No per-brand guidance, no test button. | KNOWN | `A/times/fragments/AlarmsFragment.kt:76-103`, `A/times/utils/BatteryOptimizations.kt` |

**Defects and weak points found by reading (all KNOWN unless marked).**

- **S1 · The chain has one link.** If a fired alarm's process is killed before `Times.setAlarms()` runs, or the OEM drops the alarm, nothing is scheduled until the next boot, clock event, or app open.
- **S2 · The daily self-heal needs internet.** The periodic worker carries a `CONNECTED` network constraint (`SyncTimesWorker.kt:39-43`). Offline users never get it.
- **S3 · Wrong instant when the phone's zone differs from the city's.** The engine returns wall-clock times in the city's stored zone (`A/times/calc/CalcTimesSerializer.kt:14,23`). The scheduler converts them with `ZoneId.systemDefault()` (`AlarmService.kt:169`). A Dhaka city on a phone set to another zone fires at the wrong moment.
- **S4 · Silent downgrade to inexact alarms** when exact permission is missing (`MyAlarmManager.kt:47`).
- **S5 · In-memory dedupe.** `sLastSchedule` skips re-registering an alarm equal to the last one, with a Samsung-specific exception (`AlarmService.kt:158-167`). The reason is undocumented. (UNKNOWN why; ask upstream or drop it and test on Samsung.)
- **S6 · `IntentService`** is used for playback (`AlarmService.kt`). It is deprecated in the platform (ASSUMED).
- **S7 · Both exact-alarm permissions are declared** (`AndroidManifest.xml`). Android says `USE_EXACT_ALARM` is for calendar and alarm-clock apps, and Play restricts the permission to apps whose core function needs it (KNOWN: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms, https://support.google.com/googleplay/android-developer/answer/14554661). Whether Play accepts an Adhan app under `USE_EXACT_ALARM` is UNKNOWN; the base app is on Play with it declared, which is evidence but not a ruling.

**Permissions requested (KNOWN, `AndroidManifest.xml:20-36`).** ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, RECEIVE_BOOT_COMPLETED, USE_EXACT_ALARM, SCHEDULE_EXACT_ALARM, SET_ALARM, WAKE_LOCK, POST_NOTIFICATIONS, VIBRATE, DISABLE_KEYGUARD, ACCESS_NOTIFICATION_POLICY, FOREGROUND_SERVICE_SPECIAL_USE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, INTERNET, INSTALL_SHORTCUT.

**Notification channels (KNOWN, `A/times/utils/NotificationUtils.kt`).** `alarm` (low importance), `ongoing` (low), `sound` (high), `widget` (high). The Adhan is audible because a media player plays it, not because of the channel.

## 4. Qibla

| Item | Finding | Label | Evidence |
|---|---|---|---|
| Sensors | Accelerometer + magnetic field, game-rate sampling | KNOWN | `A/compass/magnetic/MagneticCompass.java:92-93` |
| Filtering | Low-pass filter on the angle | KNOWN | `A/compass/magnetic/DegreeLowPassFilter.java` |
| Bearing | Computed from the user's location to 21.42247 N, 39.826198 E | KNOWN | `A/compass/MainFragment.kt:198-199` |
| Accuracy shown | Yes: low / medium / high, in a calibration panel that opens when accuracy is not high | KNOWN | `MagneticCompass.java:132-135`, `A/compass/magnetic/CalibrationFragment.kt` |
| Magnetic declination | Not corrected | KNOWN | grep for `GeomagneticField` and `getDeclination` returns nothing |
| Map view | Google Maps in the Play flavour only | KNOWN | `app/build.gradle` (`playImplementation … play-services-maps`), `PRIVACY-POLICY.md` |
| Phones with no magnetometer | | UNKNOWN | Read `MagneticCompass.java` null-sensor path; test on such a device |

## 5. Localization

- `values-bn` exists and `bn` is in `locales_config.xml` (KNOWN, `app/src/main/translations/values-bn/`, `app/src/main/res/xml/locales_config.xml`).
- Coverage: 107 of 291 keys (KNOWN, counted). The Bengali app name is "নামাজের সময়" (KNOWN, `values-bn/strings.xml`).
- Strings are managed through Crowdin, project id 203839 (KNOWN, `crowdin.yml`).
- **Digits.** A `numbers` preference supports "normal", Arabic-Indic and Farsi digits only. **There are no Bengali digits** (KNOWN, `A/utils/LocaleUtils.kt:213-231`).
- **Bug.** The digit loop tests `char in '0' until '9'`, which excludes `9`. With Arabic digits on, the digit 9 is left in Latin (KNOWN, `LocaleUtils.kt:225`).
- Times are formatted by hand from `HH:mm` with hard-coded "AM"/"PM" (KNOWN, `LocaleUtils.kt:100-123`).
- No plural resources in the translation files checked (KNOWN, counted for en, bn, tr, ar, ur).
- Fonts: no Bengali or Arabic font is bundled; the only font asset is an icon font (KNOWN, `app/src/main/assets/materialdesignicons-webfont.ttf`). Text uses the phone's system fonts (ASSUMED).
- RTL is enabled app-wide (KNOWN, `android:supportsRtl="true"`).
- Bundled long-form content is Turkish, German and English only (KNOWN, `app/src/main/assets/{tr,de,en}`).

## 6. Data, storage and network

**Storage (KNOWN).** City and alarm settings are JSON in SharedPreferences (`A/times/times/TimesCompanion.kt`, `A/times/utils/Store.kt`). Downloaded timetables sit in one SharedPreferences file per city (`DayTimesWebProvider.kt`). Room is used only for the dhikr counter (`A/dhikr/data/DhikrDatabase.kt`). There is a manual backup/restore screen (`A/settings/BackupRestoreActivity.kt`). No `allowBackup` or backup-rules attribute is set in the app manifest (grep).

**Outbound hosts (KNOWN, from a grep of every URL and HTTP call site).**

| Host | Used for | Sends | Evidence |
|---|---|---|---|
| `opt.mk38.de` | City search, GPS-to-city lookup, downloaded timetables | Search text, or latitude and longitude | `OpenPrayerTimes.kt`, `LocationUtil.kt:109`, `A/times/fragments/SearchCityFragment.kt:233,266` |
| `metinkale38.github.io` | Adhan sound list and files, hadith database | Nothing beyond the request | `A/App.kt:91`, `Sounds.kt:90`, `A/hadith/MainActivity.kt:42` |
| Any URL the user types | CSV timetable import | | `A/times/times/CSVParser.kt:18` |
| Sentry / GlitchTip DSN (Play flavour) | Crash reports with device model, OS, app version, stack trace, install UUID | | `CrashReporter.kt` (play), `PRIVACY-POLICY.md` |
| Google Play services (Play flavour) | Map view, location, Wear sync | | `app/build.gradle` |

Consequence: **first-run city setup needs the network** in the base app, because both search and GPS lookup go through `opt.mk38.de` (KNOWN from the call sites; confirm by running offline).

## 7. Tests and CI

| What | Size | Covers |
|---|---|---|
| `app/src/test/kotlin/HijriTest.kt` | 55 lines | Hijri dates |
| `app/src/androidTest/kotlin/IntroTest.kt`, `TimesTest.kt` | 66 + 83 lines | Intro and times screens |
| `app/src/testPlay/...`, `sync/src/test/...` | 144 lines | Wear payloads |
| `.maestro/smoke.yaml` | UI smoke flow | Launch and onboarding |
| Engine `CalculationTest.kt` | 47 lines | Presets, one Berlin date |

CI builds both flavours and has a Maestro job (KNOWN, `.github/workflows/`). **There is no test of the scheduler at all** (KNOWN, no test file references `AlarmService`, `MyAlarmManager` or `setAlarms`). Pass/fail today is UNKNOWN (not run).

## 8. Licenses

| Component | License | Label | Note |
|---|---|---|---|
| App code | Apache-2.0 | KNOWN (`LICENSE`) | README asks forks to redesign and not publish something too similar (`README.md:159-161`) |
| Engine `calctimes` | **LGPL-3.0** | KNOWN | See H1. You may use it, but you must keep its notices, publish any changes you make to it, and let users swap in a modified copy. How to satisfy that last point inside a signed Android app is a legal question (UNKNOWN; ask a lawyer or replace the engine, see ADR-2) |
| Engine `client`, `hijri` | Apache-2.0 | KNOWN (`build.gradle.kts` POMs) | |
| Downloadable Adhan sounds | | UNKNOWN | Not in the repo. Do not reuse; license your own |
| Downloadable hadith database | | UNKNOWN | Same |
| Bundled Turkish/German/English texts | | UNKNOWN | Being deleted anyway |
| Third-party libraries (Ktor, Ion, Flexmark, gif-drawable, AboutLibraries, AndroidX, Material, Sentry) | Believed permissive | ASSUMED | Generate the AboutLibraries report (`app/build.gradle` applies the plugin) and read it |
| Icon font | | UNKNOWN | Check `app/src/main/assets/license.html` |

## 9. Size and performance

APK size, cold start and memory are **UNKNOWN**: nothing was built. Source assets are 1.5 MB and resources 3.8 MB (KNOWN, `du`). Measure on a 2 GB-RAM phone after the first slice builds.

## 10. What must go for a four-feature v1

| Delete | Why | Evidence that it is separable |
|---|---|---|
| `:wear`, `:sync`, Wear code in both flavours | Not in scope | Separate modules in `settings.gradle` |
| `play` flavour: Sentry, Google Maps, Play location, Wearable | Trackers and Google SDKs contradict the positioning | All `playImplementation` lines |
| Web sources: Diyanet, IGMG, London, NVC, Semerkand, CSV; the `client` module; Ktor; `SyncTimesWorker` | Turkey- and Europe-centred; removes `opt.mk38.de` | `A/times/times/Source.kt` |
| Server-backed city search and GPS lookup | Privacy (H5) and offline | Replace with a bundled district list |
| Hadith, 99 Names, Tesbihat, Dhikr, Missed prayers, Calendar/holidays modules | Not v1 | Each is a `Feature` registered in `A/Application.kt` |
| Sound downloads, Ion | H6 | `A/times/alarm/sounds/` |
| 3D/camera compass, map compass | One compass, done well | `A/compass/` |
| Six of the seven widgets, silenter, face-down-to-stop, export to PDF/image, launcher-icon aliases per language | Detail budget | `AndroidManifest.xml` (7 widget providers, many `activity-alias` entries) |
| 49 non-target translation folders | Bengali and English only | `app/src/main/translations/` |

What is left is small: the engine call, the alarm path, one compass, and settings. That is the argument for treating the base as a **reference and parts donor**, not as a codebase to prune (decided in `03-architecture.md`, ADR-1).
