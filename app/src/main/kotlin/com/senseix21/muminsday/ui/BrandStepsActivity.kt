package com.senseix21.muminsday.ui

import android.os.Build
import android.os.Bundle
import com.senseix21.muminsday.R
import com.senseix21.muminsday.alarm.brandStepsArray
import com.senseix21.muminsday.alarm.showsTecnoStepsAsNote
import com.senseix21.muminsday.store.Stores

/**
 * The steps that keep the app alive on a phone whose maker stops background apps (F1 failure mode 2, story 3). The text
 * is from G32 (a community site, not checked on a phone) and says so. No deep link is claimed: the one button opens
 * this app's own details page, which every Android phone has. "I have done these steps" is the user's word, stored for
 * this phone only (G27); the app cannot verify it.
 */
class BrandStepsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texts = Texts(this, language)
        val steps = brandStepsArray(Build.MANUFACTURER)

        val page = column()
        page.addView(title(getString(R.string.brand_title)))
        // The maker's name as the phone reports it: a proper noun, shown as it is.
        page.addView(heading(Build.MANUFACTURER))
        page.addView(body(getString(R.string.brand_intro), muted = true))
        if (showsTecnoStepsAsNote(Build.MANUFACTURER)) page.addView(body(getString(R.string.brand_transsion_note)))
        // Not a listed brand: nothing is claimed, and the health check does not ask for these steps.
        steps?.let { array ->
            resources.getStringArray(array).forEachIndexed { index, step ->
                page.addView(body("${texts.number(index + 1)}. $step"))
            }
        }
        page.addView(actionButton(getString(R.string.brand_open_settings), primary = false) { startActivity(appDetails(this)) })
        page.addView(
            actionButton(getString(R.string.brand_confirm)) {
                Stores.device(this).confirmBrandSteps()
                finish()
            },
        )
        page.addView(body(getString(R.string.brand_confirm_note), muted = true))
        show(page)
    }
}
