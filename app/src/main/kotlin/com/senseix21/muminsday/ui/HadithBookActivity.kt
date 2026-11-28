package com.senseix21.muminsday.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.hadithbooks.HadithBook
import com.senseix21.muminsday.hadithbooks.Section
import com.senseix21.muminsday.hadithbooks.TypedNumber
import com.senseix21.muminsday.hadithbooks.readTypedNumber
import com.senseix21.muminsday.store.HadithBookOpen
import com.senseix21.muminsday.store.HadithBookReader

/**
 * One hadith book (ADR-28): its name, the source's own name for it, the unreviewed banner, a box to jump to a hadith by
 * its number (any of the three digit sets), and its sections with their numbers and hadith counts. Section names are the
 * source's (English), shown as they are.
 */
class HadithBookActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val book = bookFrom(intent.getStringExtra(EXTRA_BOOK))
        if (book == null) {
            finish()
            return
        }
        val page = column()
        page.addView(title(bookTitle(this, book)))
        page.addView(body(getString(R.string.hadith_books_opening), muted = true))
        show(page)
        whenHadithBookOpens(this, book) { opened ->
            when (opened) {
                is HadithBookOpen.Ready -> render(opened.reader)
                HadithBookOpen.NotInThisBuild -> showMessage(book, getString(R.string.hadith_books_not_in_build))
                is HadithBookOpen.Refused -> showMessage(book, getString(R.string.hadith_books_refused))
            }
        }
    }

    private fun showMessage(book: HadithBook, text: String) {
        val page = column()
        page.addView(title(bookTitle(this, book)))
        page.addView(panel().apply { addView(body(text)) })
        show(page)
    }

    private fun render(reader: HadithBookReader) {
        val page = column()
        page.addView(title(bookTitle(this, reader.book)))
        page.addView(body(getString(R.string.hadith_source_name, reader.name), muted = true))
        page.addView(hadithUnreviewedBanner(this))
        page.addView(jumpBox(reader))
        page.addView(heading(getString(R.string.hadith_sections)))
        for (section in reader.sections) {
            page.addView(actionButton(sectionLabel(section), primary = false) { openSection(reader.book, section, null) })
        }
        show(page)
    }

    private fun sectionLabel(section: Section): String {
        val name = section.name.ifEmpty { getString(R.string.hadith_section_unnamed) }
        val count = getString(R.string.hadith_count, sourceNumber(section.hadithCount.toString(), language))
        return "${sourceNumber(section.id.value.toString(), language)}. $name ($count)"
    }

    private fun jumpBox(reader: HadithBookReader): LinearLayout = column().apply {
        val box = EditText(this@HadithBookActivity).apply {
            hint = getString(R.string.hadith_jump_hint)
            // Text, not a number keyboard: the reader accepts Bengali and Arabic-Indic digits as well as ASCII.
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 18f
            minHeight = dp(MIN_TARGET_DP)
            minimumHeight = dp(MIN_TARGET_DP)
            isSingleLine = true
        }
        val message = body("", muted = true)
        val jump = {
            when (val typed = readTypedNumber(box.text.toString())) {
                TypedNumber.NotANumber -> message.text = getString(R.string.hadith_jump_not_number)
                is TypedNumber.Number -> {
                    val id = reader.sectionOf(typed.number)
                    val section = reader.sections.firstOrNull { it.id == id }
                    if (section == null) {
                        message.text = getString(R.string.hadith_jump_not_found, sourceNumber(typed.number.written, language))
                    } else {
                        message.text = ""
                        openSection(reader.book, section, typed.number.written)
                    }
                }
            }
        }
        box.setOnEditorActionListener { _, action, _ -> (action == EditorInfo.IME_ACTION_GO).also { if (it) jump() } }
        addView(box)
        addView(actionButton(getString(R.string.hadith_jump), primary = false) { jump() })
        addView(message)
    }

    private fun openSection(book: HadithBook, section: Section, number: String?) {
        startActivity(
            Intent(this, HadithSectionActivity::class.java)
                .putExtra(EXTRA_BOOK, book.key)
                .putExtra(HadithSectionActivity.EXTRA_SECTION, section.id.value)
                .putExtra(HadithSectionActivity.EXTRA_NUMBER, number),
        )
    }
}
