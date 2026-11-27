package com.senseix21.muminsday.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.senseix21.muminsday.bearing.Bearing
import com.senseix21.muminsday.bearing.Degrees
import com.senseix21.muminsday.bearing.HeadingSample
import com.senseix21.muminsday.bearing.MicroTesla
import com.senseix21.muminsday.bearing.SensorAccuracy
import com.senseix21.muminsday.bearing.smoothHeading
import java.time.Duration
import kotlin.math.sqrt

/** What the phone's sensors say right now. [heading] is from magnetic north (the platform's reference), already smoothed. */
data class CompassReading(
    val hasMagnetometer: Boolean,
    val accuracy: SensorAccuracy,
    val field: MicroTesla?,
    val heading: HeadingSample?,
    val pitch: Degrees?,
    val roll: Degrees?,
)

/**
 * Reads the compass sensors while it is started, and only then: [start] from onResume and [stop] from onPause, so no
 * sensor runs while the screen is not visible (battery, INV-P2's spirit). It decides nothing; `bearing/` does.
 *
 * Heading comes from TYPE_ROTATION_VECTOR when the phone has one, otherwise from the accelerometer and magnetometer
 * through SensorManager.getRotationMatrix. The magnetometer is always registered: it gives the field strength for the
 * interference check and the accuracy level the platform assigns to calibration. The axes are remapped for the
 * screen's rotation, so "up" is the top of the screen, not of the phone's body (portrait locks are ignored on large
 * screens from Android 16, so the screen cannot be assumed upright).
 */
class CompassSensors(
    context: Context,
    /** The screen's rotation, Surface.ROTATION_0 to _270, read each time because it can change while the screen is open. */
    private val screenRotation: () -> Int,
    private val onReading: (CompassReading) -> Unit,
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationVector: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var accuracy = SensorAccuracy.Unreliable
    private var field: MicroTesla? = null
    private var heading: HeadingSample? = null
    private var pitch: Degrees? = null
    private var roll: Degrees? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveGravity = false
    private var haveGeomagnetic = false

    val hasMagnetometer: Boolean get() = magnetometer != null

    fun start() {
        // Start from nothing: a reading from before the screen was hidden says nothing about the phone now.
        accuracy = SensorAccuracy.Unreliable
        field = null; heading = null; pitch = null; roll = null
        haveGravity = false; haveGeomagnetic = false
        if (magnetometer == null) {
            onReading(snapshot())
            return
        }
        manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        if (rotationVector != null) {
            manager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
        } else if (accelerometer != null) {
            manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() = manager.unregisterListener(this)

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
        this.accuracy = accuracyOf(accuracy)
        onReading(snapshot())
    }

    override fun onSensorChanged(event: SensorEvent) {
        val matrix = FloatArray(9)
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // The event carries the accuracy too, so it is right even if onAccuracyChanged was never called.
                accuracy = accuracyOf(event.accuracy)
                val (x, y, z) = event.values
                field = MicroTesla.of(sqrt((x * x + y * y + z * z).toDouble()))
                if (rotationVector == null) {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    haveGeomagnetic = true
                    if (haveGravity && SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) update(matrix, event)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                haveGravity = true
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                update(matrix, event)
            }
        }
        onReading(snapshot())
    }

    private fun update(matrix: FloatArray, event: SensorEvent) {
        val angles = FloatArray(3)
        val screen = FloatArray(9)
        val (axisX, axisY) = when (screenRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axisX, axisY, screen)
        SensorManager.getOrientation(screen, angles)
        val raw = Bearing.ofOrNull(Math.toDegrees(angles[0].toDouble())) ?: return
        val sample = HeadingSample(raw, Duration.ofNanos(event.timestamp))
        heading = smoothHeading(heading, sample)
        pitch = Degrees.of(Math.toDegrees(angles[1].toDouble()))
        roll = Degrees.of(Math.toDegrees(angles[2].toDouble()))
    }

    private fun snapshot() = CompassReading(hasMagnetometer, accuracy, field, heading, pitch, roll)

    private fun accuracyOf(platform: Int): SensorAccuracy = when (platform) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorAccuracy.High
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> SensorAccuracy.Medium
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorAccuracy.Low
        // 0 (unreliable), -1 (no contact), and anything a future release adds: not trusted.
        else -> SensorAccuracy.Unreliable
    }
}
