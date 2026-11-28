package com.senseix21.muminsday.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import com.senseix21.muminsday.R
import com.senseix21.muminsday.hadithbooks.Hadith
import com.senseix21.muminsday.hadithbooks.HadithBook
import com.senseix21.muminsday.hadithbooks.Section
import com.senseix21.muminsday.hadithbooks.SectionId
import com.senseix21.muminsday.store.HadithBookOpen
import com.senseix21.muminsday.store.HadithBookReader
import com.senseix21.muminsday.store.openHadithBook

/**
 * One section of a hadith book (ADR-28), as a ListView: only the hadiths on screen are built, so a section of hundreds
 * of long texts opens at once. Each hadith: its number, the Arabic right to left in its own isolate (INV-L3), the
 * Bengali below, each grade as the source gives it, and the source's reference. Texts and grades are the source's,
 * unchanged; a field the source does not have is not shown.
 */
class HadithSectionActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val book = bookFrom(intent.getStringExtra(EXTRA_BOOK))
        if (book == null) {
            finish()
            return
        }
        val section = SectionId(intent.getIntExtra(EXTRA_SECTION, -1))
        val number = intent.getStringExtra(EXTRA_NUMBER)
        val page = column()
        page.addView(title(bookTitle(this, book)))
        page.addView(body(getString(R.string.hadith_books_opening), muted = true))
        show(page)
        val context = applicationContext
        Thread {
            val opened = openHadithBook(context, book)
            val hadiths = (opened as? HadithBookOpen.Ready)?.reader?.hadiths(section).orEmpty()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (opened) {
                    is HadithBookOpen.Ready -> render(book, opened.reader, section, hadiths, number)
                    HadithBookOpen.NotInThisBuild -> showMessage(book, getString(R.string.hadith_books_not_in_build))
                    is HadithBookOpen.Refused -> showMessage(book, getString(R.string.hadith_books_refused))
                }
            }
        }.start()
    }

    private fun showMessage(book: HadithBook, text: String) {
        val page = column()
        page.addView(title(bookTitle(this, book)))
        page.addView(panel().apply { addView(body(text)) })
        show(page)
    }

    private fun render(book: HadithBook, reader: HadithBookReader, id: SectionId, hadiths: List<Hadith>, number: String?) {
        val section = reader.sections.firstOrNull { it.id == id }
        if (section == null) {
            finish()
            return
        }
        val list = ListView(this)
        list.addHeaderView(header(book, section), null, false)
        list.adapter = HadithAdapter(hadiths)
        showList(list)
        val at = hadiths.indexOfFirst { it.number.written == number }
        if (at >= 0) list.setSelection(list.headerViewsCount + at)
    }

    private fun header(book: HadithBook, section: Section): LinearLayout = column().apply {
        layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(title(bookTitle(this@HadithSectionActivity, book)))
        addView(heading(getString(R.string.hadith_section_title, sourceNumber(section.id.value.toString(), language))))
        addView(body(section.name.ifEmpty { getString(R.string.hadith_section_unnamed) }))
        addView(hadithUnreviewedBanner(this@HadithSectionActivity))
    }

    /** Builds a row only when the ListView asks for it, reusing a row that scrolled away. */
    private inner class HadithAdapter(private val hadiths: List<Hadith>) : BaseAdapter() {
        override fun getCount(): Int = hadiths.size

        override fun getItem(position: Int): Hadith = hadiths[position]

        override fun getItemId(position: Int): Long = position.toLong()

        // Rows hold reading, not actions: nothing to select or tap.
        override fun areAllItemsEnabled(): Boolean = false

        override fun isEnabled(position: Int): Boolean = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: column().apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, dp(8), 0, dp(16))
            }
            row.removeAllViews()
            fill(row, hadiths[position])
            return row
        }
    }

    private fun fill(row: LinearLayout, hadith: Hadith) {
        row.addView(heading(getString(R.string.hadith_number, sourceNumber(hadith.number.written, language))))
        hadith.arabicNumber?.takeIf { it != hadith.number.written }?.let {
            row.addView(body(getString(R.string.hadith_arabic_number, sourceNumber(it, language)), muted = true))
        }
        val arabic = hadith.arabic.shown()
        val bengali = hadith.bengali.shown()
        arabic?.let { row.addView(arabicText(this, it)) }
        bengali?.let { row.addView(body(it).apply { textSize = 20f }) }
        if (arabic == null && bengali == null) row.addView(body(getString(R.string.hadith_no_text), muted = true))
        for (grade in hadith.grades) row.addView(body(getString(R.string.hadith_grade, grade.name, grade.grade), muted = true))
        row.addView(
            body(
                getString(
                    R.string.hadith_reference,
                    sourceNumber(hadith.reference.book.toString(), language),
                    sourceNumber(hadith.reference.hadith.toString(), language),
                ),
                muted = true,
            ),
        )
        row.addView(View(this).apply {
            setBackgroundColor(Palette.line)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(16) }
        })
    }

    /** The source's text when it has one to show; an empty or blank field shows nothing. The text itself is untouched. */
    private fun String?.shown(): String? = this?.takeIf { it.isNotBlank() }

    companion object {
        const val EXTRA_SECTION: String = "section"
        const val EXTRA_NUMBER: String = "number"
    }
}
