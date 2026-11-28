package com.senseix21.muminsday.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.senseix21.muminsday.R
import com.senseix21.muminsday.l10n.rightToLeft
import com.senseix21.muminsday.quran.Lookup
import com.senseix21.muminsday.quran.VerseRef
import com.senseix21.muminsday.quran.lookup
import com.senseix21.muminsday.store.QURAN_FONT
import com.senseix21.muminsday.store.QuranOpen
import com.senseix21.muminsday.store.QuranReader
import com.senseix21.muminsday.store.ShownAyah
import com.senseix21.muminsday.store.Stores
import com.senseix21.muminsday.store.openQuran

/**
 * The Quran (phase 14): where the user left off, a search box, and the 114 surahs. The text is the Tanzil file as
 * shipped, never changed (INV-QT1); `store/` verifies it and `quran/` reads the search box. The first open builds the
 * search database on the phone, so the screen says so while it works.
 */
class QuranActivity : BaseActivity() {

    private var reader: QuranReader? = null
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = column()
        page.addView(title(getString(R.string.quran_title)))
        page.addView(body(getString(R.string.quran_preparing), muted = true))
        show(page)
        whenQuranOpens(this) { opened -> render(opened) }
    }

    override fun onResume() {
        super.onResume()
        // The remembered position changes while a surah is open; draw the "continue" line again on the way back.
        reader?.let { render(QuranOpen.Ready(it)) }
    }

    private fun render(opened: QuranOpen) {
        val page = column()
        page.addView(title(getString(R.string.quran_title)))
        when (opened) {
            QuranOpen.Missing -> page.addView(body(getString(R.string.quran_missing)))
            is QuranOpen.Unverified -> page.addView(panel().apply { addView(body(getString(R.string.quran_unverified))) })
            is QuranOpen.Ready -> showReady(page, opened.reader)
        }
        show(page)
    }

    private fun showReady(page: LinearLayout, reader: QuranReader) {
        this.reader = reader
        val texts = Texts(this, language)
        page.addView(body(getString(R.string.quran_credit), muted = true))

        // A remembered position is offered only if that ayah exists in this text.
        val position = Stores.settings(this).readApp().quranPosition
        if (position != null && position.ayah <= reader.surahs[position.surah.value - 1].ayahCount) {
            page.addView(actionButton(getString(R.string.quran_continue, texts.number(position.surah.value), texts.number(position.ayah))) {
                openSurah(this, position)
            })
        }

        val box = EditText(this).apply {
            hint = getString(R.string.quran_search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            textSize = 18f
            minHeight = dp(MIN_TARGET_DP)
            isSingleLine = true
        }
        results = column()
        val search = { runSearch(box.text.toString(), reader, texts) }
        box.setOnEditorActionListener { _, action, _ -> (action == EditorInfo.IME_ACTION_SEARCH).also { if (it) search() } }
        page.addView(box)
        page.addView(actionButton(getString(R.string.quran_search), primary = false) { search() })
        page.addView(results)

        page.addView(heading(getString(R.string.quran_surahs)))
        for (surah in reader.surahs) {
            page.addView(
                actionButton(texts.number(surah.number.value) + ". " + rightToLeft(surah.name), primary = false) {
                    openSurah(this, VerseRef(surah.number, 1))
                }.apply { typeface = quranTypeface(this@QuranActivity) },
            )
        }
    }

    private fun runSearch(typed: String, reader: QuranReader, texts: Texts) {
        results.removeAllViews()
        when (val found = lookup(typed, reader.surahs)) {
            Lookup.Empty -> Unit
            Lookup.NoSuchVerse -> results.addView(body(getString(R.string.quran_no_such_verse)))
            is Lookup.Verse -> openSurah(this, found.ref)
            is Lookup.Surah -> openSurah(this, VerseRef(found.number, 1))
            is Lookup.Words -> {
                val ayahs = reader.search(found, SEARCH_LIMIT)
                results.addView(heading(getString(if (ayahs.isEmpty()) R.string.quran_no_results else R.string.quran_results)))
                if (!reader.hasTranslation) results.addView(body(getString(R.string.quran_search_arabic_only), muted = true))
                ayahs.forEach { results.addView(resultRow(it, texts)) }
                if (ayahs.size == SEARCH_LIMIT) results.addView(body(getString(R.string.quran_results_capped), muted = true))
            }
        }
    }

    private fun resultRow(ayah: ShownAyah, texts: Texts): View = column().apply {
        setPadding(0, dp(8), 0, dp(8))
        minimumHeight = dp(MIN_TARGET_DP)
        isClickable = true
        isFocusable = true
        setOnClickListener { openSurah(this@QuranActivity, ayah.ref) }
        addView(body(texts.number(ayah.ref.surah.value) + ":" + texts.number(ayah.ref.ayah), muted = true))
        addView(arabicText(this@QuranActivity, ayah.text))
    }
}

/** At most this many search results are listed (target); more words narrow the search. */
private const val SEARCH_LIMIT: Int = 50

/** Opens the Quran off the main thread (the first open builds the database) and hands the result back on it. */
internal fun whenQuranOpens(activity: BaseActivity, then: (QuranOpen) -> Unit) {
    val context = activity.applicationContext
    Thread {
        val opened = openQuran(context)
        activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) then(opened) }
    }.start()
}

internal fun openSurah(context: Context, at: VerseRef) {
    context.startActivity(
        Intent(context, SurahActivity::class.java)
            .putExtra(SurahActivity.EXTRA_SURAH, at.surah.value)
            .putExtra(SurahActivity.EXTRA_AYAH, at.ayah),
    )
}

/** An ayah as shipped (INV-QT1), right to left in its own isolate (INV-L3), in the bundled Uthmani font. */
internal fun arabicText(context: Context, text: String): TextView = context.body(rightToLeft(text)).apply {
    typeface = quranTypeface(context)
    textSize = 28f
    setLineSpacing(0f, 1.4f)
    textDirection = View.TEXT_DIRECTION_RTL
    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    layoutDirection = View.LAYOUT_DIRECTION_RTL
}

/** One copy of the font for the process: 114 buttons and hundreds of ayahs share it. */
private var quranTypeface: Typeface? = null

internal fun quranTypeface(context: Context): Typeface = synchronized(QuranActivity::class) {
    quranTypeface ?: Typeface.createFromAsset(context.assets, QURAN_FONT).also { quranTypeface = it }
}
