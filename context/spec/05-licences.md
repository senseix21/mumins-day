# 05 · Licences of what ships

Written 2026-11-27 14:10 +0600 (clock, `TZ=Asia/Dhaka date`). Labels are as in `00-base-audit.md`. This is an engineering
inventory, not legal advice: every statement about what a licence *requires* of us is **ASSUMED** unless it quotes
a licence text that was read, and all of them are for a lawyer (Q6).

## 1. What ships

**How this list was made (KNOWN).** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and
`--configuration coreLibraryDesugaring`, run 2026-11-27 at commit `cd502e7` plus the uncommitted working tree. These are the
two configurations the dependency gate (`app/build.gradle.kts`, `CheckDependencies`, T-BUILD-3) reads. The resolved set was
6 libraries. **Updated 2026-11-27 (ADR-14): desugaring removed, the set is now 4 libraries** (`dependency gate: passed, 4 libraries.`), so the two `desugar_jdk_libs` rows below no longer ship.

**Where each licence was read (KNOWN).** In the `<licenses>` element of the library's own `.pom`, in the local Gradle cache
`~/.gradle/caches/modules-2/files-2.1/<group>/<name>/<version>/*/<name>-<version>.pom`, fetched from Maven Central / Google
Maven by the build. Not from memory. The POM names a licence; it does not prove it (a POM can be wrong). The licence *texts* were not read for the
third-party libraries.

| Library | Version | Licence per its POM | POM file read | Reaches the APK how |
|---|---|---|---|---|
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.10 | Apache-2.0 (`http://www.apache.org/licenses/LICENSE-2.0.txt`) | `kotlin-stdlib-2.4.10.pom` | Dexed into `classes.dex` (KNOWN: APK has `kotlin/*.kotlin_builtins` and a 3.1 MB `classes.dex` unminified) |
| `org.jetbrains:annotations` | 13.0 | "The Apache Software License, Version 2.0" | `annotations-13.0.pom` | Pulled in by kotlin-stdlib |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.7.1 | Apache-2.0 | `kotlinx-datetime-0.7.1.pom` | Required by the engine's API (ADR-2) |
| `org.jetbrains.kotlinx:kotlinx-datetime-jvm` | 0.7.1 | Apache-2.0 | `kotlinx-datetime-jvm-0.7.1.pom` | The JVM artifact of the one above |
| `com.android.tools:desugar_jdk_libs` | 2.1.5 | **GNU General Public License, version 2, with the Classpath Exception** (`https://github.com/google/desugar_jdk_libs/blob/master/LICENSE`) | `desugar_jdk_libs-2.1.5.pom` | `coreLibraryDesugaring`: `java.time` for Android 8 and 9; compiled into the app's dex |
| `com.android.tools:desugar_jdk_libs_configuration` | 2.1.5 | BSD-3-Clause (`https://opensource.org/licenses/BSD-3-Clause`) | `desugar_jdk_libs_configuration-2.1.5.pom` | Configuration of the above |

Not shipped (KNOWN, same dependency report): `kotest-property` and `kotlin("test")` are `testImplementation` only
(`core/build.gradle.kts`, `app/build.gradle.kts`) and are not on `releaseRuntimeClasspath`.

**Source in this repository that ships.**

| Component | Licence | Evidence |
|---|---|---|
| The app (`app/`, `core/`, `tools/`) | Apache-2.0 | KNOWN: `LICENSE` (Apache text), `NOTICE` |
| `engine/calctimes/` | LGPL-3.0 (with the GPL-3.0 it references) | KNOWN: `engine/calctimes/LICENSE` begins "GNU LESSER GENERAL PUBLIC LICENSE Version 3, 29 June 2007"; `LICENSE.GPL-3.0`; `NOTICE.txt` |
| PrayTimes.js 2.3 (the engine is a port of it) | LGPL v3.0, (C) 2007-2011 PrayTimes.org, Hamid Zarrabi-Zadeh | KNOWN: `engine/calctimes/NOTICE.txt`. Whether PrayTimes.js's own licence text says "v3.0" or "v3 or later": UNKNOWN, not fetched (http://praytimes.org/) |
| Ideas and constants from `prayer-times-android` | Apache-2.0 (upstream's licence: ASSUMED from the base audit, `00-base-audit.md` H1) | KNOWN: `NOTICE` lists what was taken (how the engine is called; the Kaaba coordinates) |

Not shipping and not allowed to: upstream's downloadable Adhan sounds and hadith database (CLAUDE.md; licences unknown). The
Adhan in the build today is a generated sine tone (KNOWN: `app/.../alarm/PlaybackService.kt`, `sineWave`), so there is **no
recording and no recording licence yet** (Q5). The district list is not delivered (Q4).

## 2. Findings from reading the packaged app

**Update 2026-11-27.** Findings 1 and 2 are resolved by ADR-13 and ADR-14: `assets/licences/` in the APK holds `calctimes-LGPL-3.0.txt`, `GPL-3.0.txt`, `calctimes-NOTICE.txt` and `Apache-2.0.txt` (KNOWN: `unzip -l app/build/outputs/apk/release/app-release-unsigned.apk`), shown on the Licences screen (KNOWN: seen on the emulator, release build). `desugar_jdk_libs` is no longer packaged. Finding 3 (Apache notices for Kotlin and kotlinx-datetime) is still open. The text below is the state before this change.

1. **The LGPL licence text is not in the APK; only the GPL text is.** KNOWN: `unzip -l app/build/outputs/apk/release/app-release-unsigned.apk`
   lists `META-INF/LICENSE.GPL-3.0` and no `META-INF/LICENSE` and no `META-INF/NOTICE.txt`, although the engine jar contains all three
   (KNOWN: `unzip -l engine/calctimes/build/libs/*.jar`). The reason is ASSUMED: the Android packaging step drops `META-INF/LICENSE`
   and `META-INF/NOTICE*` by default. The in-app Licences screen names the repository files but does not contain the text
   (KNOWN: `ui/LicencesActivity.kt`). LGPL section 4(b) reads "Accompany the Combined Work with a copy of the GNU GPL and this license
   document" (KNOWN: quoted from `engine/calctimes/LICENSE`); whether pointing to a public repository satisfies "accompany" for a Play download is
   ASSUMED not settled. **Decision for the founder / lawyer, and a small fix in `app/` if wanted:** ship the LGPL text as an asset or raw resource and show it
   on the Licences screen. Not done here (a code change outside this task's remit).
2. **`desugar_jdk_libs` is GPLv2 with the Classpath Exception, not a permissive licence.** KNOWN from its POM. ASSUMED: the Classpath
   Exception is what lets an Apache-2.0 app include it without the app becoming GPL, as for the OpenJDK class library it derives from.
   Not in `NOTICE` and not on the Licences screen today (KNOWN: both read). Lawyer: Q6. Worth testing: `minSdk` is 26 (KNOWN, `app/build.gradle.kts`) and `java.time` is in the platform from API 26 (ASSUMED), so the library may be needed only for
   newer `java.time` or Java 9+ methods that `:core` calls. UNKNOWN: try removing `coreLibraryDesugaring`, compile, and see what fails; if nothing,
   the only GPL-family library leaves the app and the allowlist loses two lines (needs the founder's yes and an ADR note, INV-P4).
3. **No third-party notices for the Apache-2.0 libraries are shown in the app.** KNOWN: `LICENCE_NOTICES` lists three entries (the app,
   the engine, prayer-times-android). Apache-2.0 section 4 asks that a recipient get a copy of the licence and that NOTICE files be kept
   (ASSUMED reading, not re-read this task). Whether Kotlin's own `NOTICE` needs reproducing: UNKNOWN; the Kotlin POM and jar were not
   searched for a NOTICE file.

## 3. LGPL-3.0 obligations for the engine: checklist

Source of the requirements: `engine/calctimes/LICENSE` section 4 (read, quoted where marked KNOWN). Whether each is *satisfied* is our status.
How they apply to a Play-distributed app is ASSUMED and for a lawyer (Q6); a legal opinion is also a release-checklist item
(`04-verification.md`).

The engine is statically compiled into the app's `classes.dex` (KNOWN: it is a Gradle `project(":engine:calctimes")`, not a separate library the user can swap),
so this is a "Combined Work" under option 4(d)(0), not 4(d)(1). (ASSUMED classification.)

| # | Obligation | Text | Status |
|---|---|---|---|
| 1 | Prominent notice with each copy that the Library is used and covered by the LGPL (4a) | KNOWN: 4(a) text above | Partly: `NOTICE` and the Licences screen say so (KNOWN, read). Play listing text: not written (add a line, ASSUMED sufficient) |
| 2 | Accompany the app with a copy of the GPL and the LGPL texts (4b) | KNOWN: 4(b) | **GPL yes, LGPL no, in the APK** (finding 1). Both are in the public repository |
| 3 | If the app displays copyright notices at run time, include the Library's copyright notice and a reference to the licence copies (4c) | KNOWN: 4(c) | The Licences screen displays PrayTimes.org's copyright line and the repository paths (KNOWN: `LICENCE_NOTICES`). Looks met; ASSUMED |
| 4 | Convey the Minimal Corresponding Source of the engine, including our change, under the LGPL (4d0) | KNOWN: 4(d)(0) | The directory `engine/calctimes/` is in the public repository `github.com/senseix21/mumins-day` (KNOWN: STATE.md says public; not re-checked this task). The change is two visibility modifiers in `MODIFICATIONS.md`. Release builds must be tagged so the source for *that* build is identifiable (not done, ASSUMED needed) |
| 5 | Convey the Corresponding Application Code in a form that lets the user recombine or relink with a modified engine (4d0) | KNOWN: 4(d)(0) | The app is Apache-2.0 and its whole source is in the same repository, so a user can edit `engine/calctimes/` and rebuild with `./gradlew :app:assembleRelease` (ASSUMED sufficient; the build ran here from a clean checkout of the tree). Build instructions are in `CLAUDE.md`; a user-facing "how to rebuild" sentence is not written |
| 6 | Installation Information, only if GPLv3 section 6 would require it for a "User Product" (4e) | KNOWN: 4(e) | UNKNOWN whether a phone is a User Product here and whether signing keys count. A user can install their own build of the app, but not over ours with a different signature (ASSUMED Android behaviour). Lawyer |
| 7 | Do not restrict modification and reverse engineering for debugging (4) | KNOWN: 4 opening | Nothing in the app licence restricts this (Apache-2.0). Play Console terms: not read, UNKNOWN |
| 8 | Keep upstream notices; publish the change | `MODIFICATIONS.md`, `NOTICE.txt` | Done (KNOWN: both files exist and say so). If a later change is made to the engine, a new line in `MODIFICATIONS.md` and an ADR note (CLAUDE.md) |
| 9 | Replace the engine later without a licence step | ADR-2 | The `PrayerClock` seam lets the engine be swapped; the dependency allowlist then loses the two `kotlinx-datetime` lines |

## 4. Release-gate summary for Phase 8 "Licences"

- [x] Dependency report generated and licences read from POMs: this file, 2026-11-27. The allowlist matches (now 4 = 4 after ADR-14).
- [x] Engine licence texts in the app: yes, 2026-11-27 (ADR-13). Lawyer's reading of whether that satisfies 4(b): Q6.
- [ ] `NOTICE` credits upstream: yes for two items; keep updating as code is ported.
- [ ] Notices for the Apache-2.0 libraries (Kotlin, kotlinx-datetime): not shown in app yet (finding 3). The Classpath-Exception library is gone (ADR-14).
- [ ] Lawyer's opinion on the LGPL engine, the Classpath-Exception library and Play distribution: Q6, not started.
- [ ] Adhan recording licence in writing (Q5); district data licence (Q4): nothing to read yet.
