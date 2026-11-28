package com.senseix21.muminsday.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.ScrollView
import com.senseix21.muminsday.R
import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.boldMarkup
import com.senseix21.muminsday.quran.VerseRef
import com.senseix21.muminsday.store.QuranOpen
import com.senseix21.muminsday.store.QuranReader
import com.senseix21.muminsday.store.Stores

/**
 * One surah: each ayah as shipped (INV-QT1), its number in the chosen language's digits, and below it the translation
 * the user chose (ADR-26) with its footnotes, both exactly as the source has them. Opens at the ayah it was given and
 * remembers, when left, the first ayah on screen (saved with the settings, so it is backed up).
 */
class SurahActivity : BaseActivity() {

    private var surah: SurahNumber? = null
    private val ayahViews = mutableListOf<Pair<Int, View>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = SurahNumber.of(intent.getIntExtra(EXTRA_SURAH, 0))
        if (number == null) {
            finish()
            return
        }
        surah = number
        val page = column()
        page.addView(title(getString(R.string.quran_preparing)))
        show(page)
        whenQuranOpens(this) { opened ->
            when (opened) {
                is QuranOpen.Ready -> render(opened.reader, number, intent.getIntExtra(EXTRA_AYAH, 1))
                QuranOpen.Missing -> showMessage(getString(R.string.quran_missing))
                is QuranOpen.Unverified -> showMessage(getString(R.string.quran_unverified))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val number = surah ?: return
        val scroll = ayahViews.firstOrNull()?.second?.parent?.parent as? ScrollView ?: return
        // The first ayah whose bottom is below the top of the screen is the one being read.
        val top = ayahViews.firstOrNull { (_, view) -> view.bottom > scroll.scrollY }?.first ?: return
        val settings = Stores.settings(this)
        settings.writeApp(settings.readApp().copy(quranPosition = VerseRef(number, top)))
    }

    private fun showMessage(text: String) {
        val page = column()
        page.addView(title(getString(R.string.quran_title)))
        page.addView(panel().apply { addView(body(text)) })
        show(page)
    }

    private fun render(reader: QuranReader, number: SurahNumber, startAyah: Int) {
        val texts = Texts(this, language)
        val info = reader.surahs[number.value - 1]
        val page = column()
        page.addView(title(getString(R.string.quran_surah_title, texts.number(number.value))))
        page.addView(arabicText(this, info.name))
        page.addView(body(getString(R.string.quran_credit), muted = true))
        val edition = Stores.settings(this).readApp().quranTranslation?.takeIf { it in reader.editions }
        when {
            edition != null -> page.addView(body(translationCredit(this, edition, texts), muted = true))
            reader.editions.isNotEmpty() -> page.addView(translationChoice(reader.editions) { render(reader, number, startAyah) })
        }
        ayahViews.clear()
        for (ayah in reader.verses(number, edition)) {
            val block = column().apply {
                addView(heading(getString(R.string.quran_ayah_number, texts.number(ayah.ref.ayah))))
                addView(arabicText(this@SurahActivity, ayah.text))
                // INV-QT1: the translation and its footnotes exactly as QuranEnc published them. Not even the digits
                // change (they are the source's own). Their <b> markup is drawn as bold, not shown (G65, founder's
                // yes 2026-11-28). Footnotes stay hidden behind a tap: some run to screens and bury the next ayah.
                ayah.translation?.let { translation ->
                    addView(body(rendered(translation.text)))
                    if (translation.footnotes.isNotEmpty()) {
                        val notes = body(rendered(translation.footnotes), muted = true).apply {
                            textSize = 15f
                            visibility = View.GONE
                        }
                        lateinit var toggle: android.widget.Button
                        toggle = actionButton(getString(R.string.quran_footnotes_show), primary = false) {
                            val opening = notes.visibility != View.VISIBLE
                            notes.visibility = if (opening) View.VISIBLE else View.GONE
                            toggle.text = getString(if (opening) R.string.quran_footnotes_hide else R.string.quran_footnotes_show)
                        }
                        addView(toggle)
                        addView(notes)
                    }
                }
            }
            ayahViews += ayah.ref.ayah to block
            page.addView(block)
        }
        show(page)
        val target = ayahViews.firstOrNull { it.first == startAyah }?.second ?: return
        (page.parent as? ScrollView)?.let { scroll -> scroll.post { scroll.scrollTo(0, target.top) } }
    }

    private fun rendered(source: String): CharSequence {
        val marked = boldMarkup(source)
        if (marked.bold.isEmpty()) return marked.shown
        return SpannableString(marked.shown).apply {
            marked.bold.forEach { setSpan(StyleSpan(Typeface.BOLD), it.first, it.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
    }

    companion object {
        const val EXTRA_SURAH: String = "surah"
        const val EXTRA_AYAH: String = "ayah"
    }
}
