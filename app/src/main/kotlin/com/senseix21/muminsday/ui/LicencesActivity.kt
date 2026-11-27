package com.senseix21.muminsday.ui

import android.os.Bundle
import com.senseix21.muminsday.R

/**
 * The open-source notices (spec 03, "Attribution"). The notice text is the repository's own wording, in English in both
 * languages: a licence notice is quoted, not translated, and a translation would need a lawyer. It names the files that
 * hold the full texts and does not reproduce them. Facts are from `NOTICE`, `engine/calctimes/NOTICE.txt` and
 * `engine/calctimes/MODIFICATIONS.md`, read 2026-11-27.
 */
class LicencesActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = column()
        page.addView(title(getString(R.string.licences_title)))
        page.addView(body(getString(R.string.licences_intro), muted = true))
        // Digits follow the language like everything else (INV-L2); that is why the commit hash is left to the repository.
        val texts = Texts(this, language)
        for ((name, text) in LICENCE_NOTICES) {
            page.addView(heading(texts.data(name)))
            page.addView(body(texts.data(text)))
        }
        // The full texts, copied from the repository at build time (assets/licences/, see app/build.gradle.kts). English
        // in both languages and shown as they are: no digit conversion, because this is legal text, not our own copy.
        for ((name, file) in LICENCE_TEXT_FILES) {
            page.addView(heading(name))
            page.addView(body(assets.open("licences/$file").bufferedReader().use { it.readText() }))
        }
        show(page)
    }
}

/** Heading and asset file. The LGPL comes first because it is the one the engine needs (LGPL-3.0 section 4b). */
internal val LICENCE_TEXT_FILES: List<Pair<String, String>> = listOf(
    "GNU Lesser General Public License v3.0 (calctimes engine)" to "calctimes-LGPL-3.0.txt",
    "GNU General Public License v3.0 (referenced by the LGPL)" to "GPL-3.0.txt",
    "calctimes notice" to "calctimes-NOTICE.txt",
    "Apache License 2.0 (Mu'mins DAY)" to "Apache-2.0.txt",
)

/** Heading and body, English. The repository path is where the licence texts and the engine source are published. */
internal val LICENCE_NOTICES: List<Pair<String, String>> = listOf(
    "Mu'mins DAY" to
        "Licensed under the Apache License, Version 2.0. See the file LICENSE and the file NOTICE in the repository " +
        "github.com/senseix21/mumins-day.",
    "Prayer-time engine (calctimes), LGPL-3.0" to
        "The prayer times are worked out by the calctimes engine, a copy of the module of open-prayer-times " +
        "(github.com/metinkale38/open-prayer-times, commit a136f9b883c3becd5dc9c0b118b4aa2c51e8d2a2), licensed under " +
        "the GNU Lesser General Public License v3.0. It is a Kotlin port of PrayTimes.js 2.3, Copyright (C) 2007-2011 " +
        "PrayTimes.org, developer Hamid Zarrabi-Zadeh, also under the GNU LGPL v3.0 (praytimes.org). " +
        "This app includes the engine with two visibility changes, recorded in engine/calctimes/MODIFICATIONS.md. The " +
        "engine's source, including that change, is in the directory engine/calctimes/ of the repository above. The " +
        "licence texts are engine/calctimes/LICENSE (LGPL-3.0) and engine/calctimes/LICENSE.GPL-3.0, and the upstream " +
        "notice is engine/calctimes/NOTICE.txt.",
    "prayer-times-android, Apache-2.0" to
        "Mu'mins DAY is a new application written with github.com/metinkale38/prayer-times-android as a reference. " +
        "The way the calctimes engine is called and the coordinates of the Kaaba come from it. The list of what was " +
        "taken is in the file NOTICE.",
)
