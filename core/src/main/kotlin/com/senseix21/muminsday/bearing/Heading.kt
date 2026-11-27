package com.senseix21.muminsday.bearing

import java.time.Duration

/** One heading reading and when it was taken, as time since any fixed start (the platform's sensor clock). */
public data class HeadingSample(val heading: Bearing, val at: Duration)

/**
 * How slowly the needle follows the phone. 200 ms is our choice (target): quick enough to feel live, slow enough to
 * hide the jitter of a hand-held sensor. To be tuned on a phone (M-3).
 */
public val DEFAULT_TIME_CONSTANT: Duration = Duration.ofMillis(200)

/**
 * A first-order low-pass filter on a heading, taken the short way round the circle so that 359 then 1 moves through 0
 * and never through 180. [previous] is the last output (null for the first sample, which is taken as it is). Samples that
 * arrive out of order or at the same instant change nothing. The step is `dt / (timeConstant + dt)`, so it does not
 * depend on how often the sensor reports.
 */
public fun smoothHeading(
    previous: HeadingSample?,
    sample: HeadingSample,
    timeConstant: Duration = DEFAULT_TIME_CONSTANT,
): HeadingSample {
    if (previous == null) return sample
    val seconds = sample.at.minus(previous.at).toNanos() / NANOS_PER_SECOND
    if (seconds <= 0.0) return previous
    val share = seconds / (timeConstant.toNanos() / NANOS_PER_SECOND + seconds)
    return HeadingSample(previous.heading.turned(shortestTurn(previous.heading, sample.heading) * share), sample.at)
}

/** Degrees to turn clockwise from [from] to reach [to] the short way: in `[-180, 180)`. */
public fun shortestTurn(from: Bearing, to: Bearing): Double = (to.degrees - from.degrees + 540.0).mod(360.0) - 180.0

private const val NANOS_PER_SECOND = 1e9
