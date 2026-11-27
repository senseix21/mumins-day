package com.senseix21.muminsday.ui

import android.os.Bundle
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.bearing.Bearing
import com.senseix21.muminsday.bearing.CompassFacts
import com.senseix21.muminsday.bearing.CompassState
import com.senseix21.muminsday.bearing.Degrees
import com.senseix21.muminsday.bearing.compassState
import com.senseix21.muminsday.bearing.QiblaDirection
import com.senseix21.muminsday.bearing.UntrustedReason
import com.senseix21.muminsday.bearing.compassPoint
import com.senseix21.muminsday.bearing.magneticBearing
import com.senseix21.muminsday.bearing.needleAngle
import com.senseix21.muminsday.bearing.qiblaDirection
import com.senseix21.muminsday.bearing.shouldLayFlat
import com.senseix21.muminsday.sensors.CompassReading
import com.senseix21.muminsday.sensors.CompassSensors
import com.senseix21.muminsday.sensors.declinationAt
import com.senseix21.muminsday.store.Stores
import java.time.Instant

/**
 * The Qibla screen (F3): the bearing as a number and in words from the stored district, and a needle only while the
 * compass can be trusted. Every decision is `bearing/`'s (`compassState`, `needleAngle`); this class reads the sensors
 * while the screen is visible and shows what core says. Location is not used: the place is the stored district (F3 #4).
 */
class QiblaActivity : BaseActivity() {

    private lateinit var sensors: CompassSensors
    private lateinit var needle: NeedleView
    private lateinit var status: LinearLayout
    private lateinit var texts: Texts

    private var trueBearing: Bearing? = null
    private var declination: Degrees? = null
    private var shownState: Any? = null
    private var describedDegrees: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texts = Texts(this, language)
        val place = Stores.settings(this).read().place
        declination = declinationAt(place, Instant.now())

        val page = column()
        page.addView(title(getString(R.string.qibla_title)))
        when (val direction = qiblaDirection(place.latitude, place.longitude)) {
            QiblaDirection.Everywhere -> page.addView(body(getString(R.string.qibla_everywhere)))
            is QiblaDirection.Toward -> {
                trueBearing = direction.bearing
                page.addView(
                    body(getString(R.string.qibla_bearing, texts.degrees(direction.bearing))).apply {
                        textSize = 28f
                        contentDescription = getString(R.string.qibla_bearing_spoken, texts.degrees(direction.bearing))
                    },
                )
                page.addView(body(texts.compassPoint(compassPoint(direction.bearing))).apply { textSize = 22f })
                page.addView(body(getString(R.string.qibla_true_note), muted = true))
                declination?.let { page.addView(body(getString(R.string.qibla_magnetic, texts.degrees(magneticBearing(direction.bearing, it.value))), muted = true)) }
            }
        }

        needle = NeedleView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(16) }
        }
        status = column()
        sensors = CompassSensors(this, ::screenRotation, ::onReading)
        // No needle at all when there is no direction to point at, or no sensor to point with (INV-Q3).
        if (trueBearing != null && sensors.hasMagnetometer) page.addView(needle)
        page.addView(status)
        show(page)
    }

    override fun onResume() {
        super.onResume()
        shownState = null
        sensors.start()
    }

    override fun onPause() {
        // The sensors run only while this screen is visible.
        sensors.stop()
        super.onPause()
    }

    @Suppress("DEPRECATION") // Activity.getDisplay() needs API 30; minSdk is 26.
    private fun screenRotation(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) display.rotation else windowManager.defaultDisplay.rotation

    private fun onReading(reading: CompassReading) {
        val qibla = trueBearing ?: return
        val state = compassState(
            CompassFacts(reading.hasMagnetometer, reading.accuracy, reading.field, declination, qibla),
        )
        val heading = reading.heading?.heading
        val flat = reading.pitch != null && reading.roll != null && shouldLayFlat(reading.pitch, reading.roll)

        val angle = when (state) {
            CompassState.NoSensor -> null
            is CompassState.Trusted -> heading?.let { needleAngle(it, state.magneticTarget) }
            is CompassState.Untrusted -> if (heading != null) state.magneticTarget?.let { needleAngle(heading, it) } else null
        }
        needle.show(angle, trusted = state is CompassState.Trusted)
        if (angle != null) {
            val spoken = texts.degrees(angle)
            // Trust is part of the key: the spoken description must change when the needle turns grey, not only when it turns.
            val key = spoken + state.javaClass.name
            if (key != describedDegrees) {
                describedDegrees = key
                needle.contentDescription = getString(
                    if (state is CompassState.Trusted) R.string.qibla_needle_desc else R.string.qibla_needle_desc_unsure,
                    spoken,
                )
            }
        }

        // The text below the needle changes only when the decision does, not at every sample.
        val signature = state to flat
        if (signature != shownState) {
            shownState = signature
            renderStatus(state, flat)
        }
    }

    private fun renderStatus(state: CompassState, flat: Boolean) {
        status.removeAllViews()
        when (state) {
            CompassState.NoSensor -> status.addView(
                panel().apply {
                    addView(body(getString(R.string.qibla_no_sensor)))
                    addView(body(getString(R.string.qibla_no_sensor_hint)))
                },
            )
            is CompassState.Trusted -> status.addView(body(getString(R.string.qibla_ok)))
            is CompassState.Untrusted -> status.addView(
                panel().apply {
                    // Focusable so TalkBack reads the warning as one block.
                    isFocusable = true
                    addView(heading(getString(R.string.qibla_warn_title)))
                    if (state.magneticTarget != null) addView(body(getString(R.string.qibla_warn_unsure)))
                    state.reasons.forEach { addView(body(getString(reasonText(it)))) }
                    if (state.reasons.any { it != UntrustedReason.NoDeclination }) addView(body(getString(R.string.qibla_calibrate)))
                },
            )
        }
        if (flat && state !is CompassState.NoSensor) status.addView(body(getString(R.string.qibla_lay_flat)))
    }

    private fun reasonText(reason: UntrustedReason): Int = when (reason) {
        UntrustedReason.NoReading -> R.string.qibla_reason_no_reading
        UntrustedReason.LowAccuracy -> R.string.qibla_reason_low_accuracy
        UntrustedReason.FieldTooWeak -> R.string.qibla_reason_field_weak
        UntrustedReason.FieldTooStrong -> R.string.qibla_reason_field_strong
        UntrustedReason.NoDeclination -> R.string.qibla_reason_no_declination
    }
}
