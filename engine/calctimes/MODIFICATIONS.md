# Modifications to the vendored engine

Upstream: https://github.com/metinkale38/open-prayer-times, module `calctimes/`
Commit: `a136f9b883c3becd5dc9c0b118b4aa2c51e8d2a2`
Licence: LGPL-3.0 (see `LICENSE`, `LICENSE.GPL-3.0`, `NOTICE.txt`)
Date of modification: 2026-11-26

## The change

Two visibility changes, nothing else in `src/main`:

1. `src/main/kotlin/dev/metinkale/calctimes/DoubleTimes.kt`, declaration `DoubleTimes`
   - before: `internal data class DoubleTimes(`
   - after: `data class DoubleTimes(`
2. `src/main/kotlin/dev/metinkale/calctimes/CalcTimes.kt`, declaration `CalcTimes.getDoubleTimes`
   - before: `internal fun getDoubleTimes(date: LocalDate): DoubleTimes {`
   - after: `fun getDoubleTimes(date: LocalDate): DoubleTimes {`

## Why

Callers need the unrounded hours to round on the safe side themselves (INV-T5) and to see NaN, which the engine's
`toTimes` turns into 00:00 (INV-T7).

## Other differences from upstream's `calctimes/`

- `build.gradle.kts`: the `maven-publish` plugin and publishing block are removed; the Kotlin plugin version comes from
  the repository's version catalog. The Jar task still packs the licence files into `META-INF`.
