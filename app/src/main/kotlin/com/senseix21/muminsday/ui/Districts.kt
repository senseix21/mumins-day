package com.senseix21.muminsday.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.districts.District
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

/** Changing district after the first run (F1 #7: the user chooses; the app never switches on its own). */
class DistrictActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texts = Texts(this, language)
        val page: LinearLayout = column()
        page.addView(title(getString(R.string.onb_district_title)))
        page.addView(body(getString(R.string.onb_district_help), muted = true))
        page.addView(
            districtPicker(language, texts) { district ->
                chooseDistrict(this, district)
                finish()
            },
        )
        show(page)
    }
}
