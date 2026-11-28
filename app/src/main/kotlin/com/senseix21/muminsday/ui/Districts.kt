package com.senseix21.muminsday.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import com.senseix21.muminsday.districts.District
import com.senseix21.muminsday.districts.NearestDistrict
import com.senseix21.muminsday.districts.WhyAsk
import com.senseix21.muminsday.districts.nearestDistrict
import com.senseix21.muminsday.districts.wholeKilometers
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.store.DistrictsLoad
import com.senseix21.muminsday.store.Stores
import com.senseix21.muminsday.store.loadDistricts

/**
 * The list of districts to pick from, or a plain message when there is no usable list. Never a made-up fallback place:
 * no list means no picker (INV-T7's spirit; the data is Q4). Each choice is a button of at least 48 dp whose label is
 * the district's name.
 */
fun Context.districtPicker(language: Language, texts: Texts, onPicked: (District) -> Unit): View {
    val box = column()
    when (val loaded = loadDistricts(this)) {
        is DistrictsLoad.Loaded -> loaded.check.districts.forEach { district ->
            val name = texts.data(if (language == Language.Bengali) district.nameBn else district.nameEn)
            box.addView(actionButton(name, primary = false) { onPicked(district) })
        }
        is DistrictsLoad.Invalid -> box.addView(body(getString(R.string.districts_invalid)))
        DistrictsLoad.Unavailable -> box.addView(body(getString(R.string.districts_unavailable)))
    }
    return box
}

/** Saves the district and its place (coordinates and fixed elevation, ADR-8) and plans again. */
fun chooseDistrict(context: Context, district: District) {
    val store = Stores.settings(context)
    store.writeApp(store.readApp().copy(districtId = district.id))
    val settings = store.read()
    saveSettings(context, settings.copy(place = district.place(settings.place.zone)))
}

/** What "find my district" has told the user so far. Holds a district and a distance, never the position (INV-P2). */
private sealed interface Finding {
    data object Idle : Finding
    data object Reading : Finding
    data object Denied : Finding
    data object NoPosition : Finding
    data class Answer(val result: NearestDistrict) : Finding
}

/**
 * Changing district after the first run (F1 #7: the user chooses; the app never switches on its own). "Find my district"
 * reads one coarse position while this screen is open, offers the nearest district and waits for a tap. The list of
 * districts below it is always there, so a denied permission or a phone without location loses nothing (ADR-19).
 */
class DistrictActivity : BaseActivity() {

    private lateinit var location: OneShotLocation
    private var finding: Finding = Finding.Idle

    /** The list to search once the permission answer arrives. Held only between the tap and the answer. */
    private var pendingDistricts: List<District>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        location = OneShotLocation(this)
        render()
    }

    override fun onPause() {
        // INV-P2: nothing is read while this screen is not in front.
        location.stop()
        if (finding == Finding.Reading) {
            finding = Finding.Idle
            render()
        }
        super.onPause()
    }

    private fun render() {
        val texts = Texts(this, language)
        val districts = (loadDistricts(this) as? DistrictsLoad.Loaded)?.check?.districts
        val page: LinearLayout = column()
        page.addView(title(getString(R.string.onb_district_title)))
        page.addView(body(getString(R.string.onb_district_help), muted = true))
        if (districts != null) page.addView(findSection(districts, texts))
        page.addView(
            districtPicker(language, texts) { district ->
                chooseDistrict(this, district)
                finish()
            },
        )
        show(page)
    }

    private fun findSection(districts: List<District>, texts: Texts): View {
        val box = column()
        box.addView(body(getString(R.string.find_privacy), muted = true))
        when (finding) {
            Finding.Reading -> box.addView(body(getString(R.string.find_reading)))
            else -> box.addView(actionButton(getString(R.string.find_button)) { startFinding(districts) })
        }
        when (val state = finding) {
            Finding.Idle, Finding.Reading -> Unit
            Finding.Denied -> box.addView(body(getString(R.string.find_denied)))
            Finding.NoPosition -> box.addView(body(getString(R.string.find_unavailable)))
            is Finding.Answer -> answerView(box, state.result, texts)
        }
        return box
    }

    private fun answerView(box: LinearLayout, result: NearestDistrict, texts: Texts) {
        when (result) {
            NearestDistrict.NoDistricts -> box.addView(body(getString(R.string.find_unavailable)))
            is NearestDistrict.Near -> {
                val name = nameOf(result.district, texts)
                box.addView(body(getString(R.string.find_near, name, texts.number(result.distance.wholeKilometers()))))
                box.addView(actionButton(getString(R.string.find_use, name)) { choose(result.district) })
            }
            is NearestDistrict.NeedsConfirmation -> {
                val name = nameOf(result.district, texts)
                val message = when (result.why) {
                    WhyAsk.OutsideBangladesh -> R.string.find_outside
                    WhyAsk.TooFarFromAnyDistrict -> R.string.find_far
                }
                // Never a silent switch: a position that is probably not where the user means gets a warning in a panel.
                box.addView(
                    panel().apply {
                        addView(body(getString(message, name, texts.number(result.distance.wholeKilometers()))))
                        addView(actionButton(getString(R.string.find_use_anyway, name), primary = false) { choose(result.district) })
                    },
                )
            }
        }
    }

    private fun nameOf(district: District, texts: Texts): String =
        texts.data(if (language == Language.Bengali) district.nameBn else district.nameEn)

    private fun choose(district: District) {
        chooseDistrict(this, district)
        finish()
    }

    private fun startFinding(districts: List<District>) {
        pendingDistricts = districts
        if (location.hasPermission()) {
            read(districts)
        } else {
            // Asked here, at the tap, and never at start (INV-P2). Android 12 and later offer "approximate" first.
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_COARSE_LOCATION) return
        val districts = pendingDistricts
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && districts != null) {
            read(districts)
        } else {
            finding = Finding.Denied
            render()
        }
    }

    private fun read(districts: List<District>) {
        finding = Finding.Reading
        render()
        location.start { position ->
            // The position stops here: only the district and the distance are kept (INV-P2).
            val latitude = position?.let { Latitude.of(it.latitude) }
            val longitude = position?.let { Longitude.of(it.longitude) }
            finding = if (latitude == null || longitude == null) {
                Finding.NoPosition
            } else {
                Finding.Answer(nearestDistrict(latitude, longitude, districts))
            }
            render()
        }
    }

    private companion object {
        const val REQUEST_COARSE_LOCATION = 41
    }
}
