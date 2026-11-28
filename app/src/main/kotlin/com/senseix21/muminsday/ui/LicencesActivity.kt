package com.senseix21.muminsday.ui

import android.os.Bundle
import com.senseix21.muminsday.R
import com.senseix21.muminsday.quran.Edition
import com.senseix21.muminsday.store.QURAN_FONT_LICENCE
import com.senseix21.muminsday.store.quranNotice
import com.senseix21.muminsday.store.translationTranscript

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
        // Tanzil's terms ask that its copyright notice be reproduced with the text: shown as the shipped file has it.
        quranNotice(this)?.let { notice ->
            page.addView(heading("Tanzil Quran Text: copyright notice, as shipped"))
            page.addView(body(notice))
        }
        // ADR-26, QuranEnc conditions 2 to 4: each translation's credit and version, and its transcript as shipped.
        page.addView(heading("Bengali translations: QuranEnc.com"))
        page.addView(body(QURANENC_NOTICE))
        for (edition in Edition.entries) {
            // Our wording follows the language's digits (INV-L2); the quoted terms and the transcript stay as published.
            page.addView(heading(texts.data("${edition.key}, version ${editionVersion(edition)}")))
            page.addView(body(texts.data(editionCredit(edition))))
            page.addView(body(translationTranscript(this, edition) ?: NO_TRANSCRIPT, muted = true))
        }
        // The full texts, copied from the repository at build time (assets/licences/ and assets/quran/, see
        // app/build.gradle.kts). English in both languages and shown as they are: no digit conversion, because this is
        // legal text, not our own copy.
        for ((name, path) in LICENCE_TEXT_FILES) {
            page.addView(heading(name))
            page.addView(body(assets.open(path).bufferedReader().use { it.readText() }))
        }
        show(page)
    }
}

/** Heading and asset path. The LGPL comes first because it is the one the engine needs (LGPL-3.0 section 4b). */
internal val LICENCE_TEXT_FILES: List<Pair<String, String>> = listOf(
    "GNU Lesser General Public License v3.0 (calctimes engine)" to "licences/calctimes-LGPL-3.0.txt",
    "GNU General Public License v3.0 (referenced by the LGPL)" to "licences/GPL-3.0.txt",
    "calctimes notice" to "licences/calctimes-NOTICE.txt",
    "Apache License 2.0 (Mu'mins DAY)" to "licences/Apache-2.0.txt",
    // SIL OFL 1.1 condition 2: each copy of the font carries its copyright notice and this licence.
    "SIL Open Font License 1.1 (Amiri Quran font)" to QURAN_FONT_LICENCE,
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
    "Quran text: Tanzil Project, tanzil.net, CC BY 3.0" to
        "The Arabic text of the Quran is the Tanzil Quran Text (Uthmani, Version 1.1) from the Tanzil Project, " +
        "tanzil.net, licensed under Creative Commons Attribution 3.0. It is included verbatim and has not been changed. " +
        "The Simple Clean text of the same release is included only to build the search index and is never shown. " +
        "Surah names and ayah counts come from the Tanzil Quran metadata (cc-by). Check for updates to the text at " +
        "tanzil.net/updates. The text's own copyright notice follows below.",
    "Amiri Quran font, SIL OFL 1.1" to
        "The Arabic is set in Amiri Quran 1.003, Copyright 2010-2022 The Amiri Project Authors " +
        "(github.com/aliftype/amiri), licensed under the SIL Open Font License, Version 1.1, included unchanged. The " +
        "full licence follows below.",
    "prayer-times-android, Apache-2.0" to
        "Mu'mins DAY is a new application written with github.com/metinkale38/prayer-times-android as a reference. " +
        "The way the calctimes engine is called and the coordinates of the Kaaba come from it. The list of what was " +
        "taken is in the file NOTICE.",
)

/** QuranEnc's terms, quoted exactly as `content/quranenc-terms.txt` holds them (`TranslationCreditTest` compares). */
internal val QURANENC_TERMS: List<String> = listOf(
    "Contents of the translations can be downloaded and re-published, with the following terms and conditions:",
    "1. No modification, addition, or deletion of the content.",
    "2. Clearly referring to the publisher and the source (QuranEnc.com).",
    "3. Mentioning the version number when re-publishing the translation.",
    "4. Keeping the transcript information inside the document.",
    "5. Notifying the source (QuranEnc.com) of any note on the translation.",
    "6. Updating the translation according to the latest version issued from the source (QuranEnc.com).",
    "7. Inappropriate advertisements must not be included when displaying translations of the meanings of the Noble Quran.",
)

private val QURANENC_NOTICE: String =
    "The Bengali translations of the meanings of the Quran are published by QuranEnc.com (Encyclopedia of the Noble " +
        "Quran, quranenc.com) and are included exactly as QuranEnc.com serves them, without modification, addition or " +
        "deletion, footnotes included. They are re-published under QuranEnc.com's terms, quoted here from " +
        "quranenc.com/en/home/api:\n\n" + QURANENC_TERMS.joinToString("\n")

/** QuranEnc's own title and description of [edition] (https://quranenc.com/en/home, read 2026-11-28). */
internal fun editionCredit(edition: Edition): String = when (edition) {
    Edition.BengaliZakaria ->
        "Bengali Translation - Abu Bakr Zakaria. Bengali Translation of Dr. Abu Bakr Muhammad Zakaria. " +
            "Source and publisher: QuranEnc.com. Version 1.1.1 (22/05/2021), as QuranEnc.com lists it. Taken from " +
            "QuranEnc.com's sura API (api/v1/translation/sura/bengali_zakaria/1 to 114), whose responses carry no " +
            "transcript block; the record of the download is content/quran-SOURCES.md in the repository."
    Edition.BengaliRwwad ->
        "Bengali Translation - Rowwad Translation Center. Translated by the team of Rowwad Translation Center, in " +
            "cooperation with the Dawah Association in Rabwah, the Islamic Content Service Association in Languages, " +
            "and the IslamHouse.com website. Source and publisher: QuranEnc.com. Version 1.1.4 (23/11/2026). The file's " +
            "own transcript information follows, as shipped."
}

private const val NO_TRANSCRIPT: String = "(No transcript block in the source.)"
