package com.senseix21.muminsday.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.hadithbooks.HadithBook
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.l10n.toBengaliDigits
import com.senseix21.muminsday.store.HadithBookOpen
import com.senseix21.muminsday.store.hadithBooksInBuild
import com.senseix21.muminsday.store.openHadithBook

/**
 * The hadith books (ADR-28): the unreviewed banner and the eight books by their names in the app's language. A release,
 * or a debug build made without `content/local/hadith/`, has no files and says so.
 */
class HadithBooksActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = column()
        page.addView(title(getString(R.string.hadith_books_title)))
        if (!hadithBooksInBuild(this)) {
            page.addView(body(getString(R.string.hadith_books_not_in_build)))
            show(page)
            return
        }
        page.addView(hadithUnreviewedBanner(this))
        page.addView(heading(getString(R.string.hadith_books_list)))
        for (book in HadithBook.entries) {
            page.addView(
                actionButton(bookTitle(this, book), primary = false) {
                    startActivity(Intent(this, HadithBookActivity::class.java).putExtra(EXTRA_BOOK, book.key))
                },
            )
        }
        show(page)
    }
}

/** The intent extra naming a book by its source key (`bukhari`). */
internal const val EXTRA_BOOK: String = "book"

internal fun bookFrom(key: String?): HadithBook? = HadithBook.entries.firstOrNull { it.key == key }

/** The book's name in the app's language: Claude's drafts, for the native speaker (`app/l10n-review.txt`). */
internal fun bookTitle(context: Context, book: HadithBook): String = context.getString(
    when (book) {
        HadithBook.Bukhari -> R.string.hadith_book_bukhari
        HadithBook.Muslim -> R.string.hadith_book_muslim
        HadithBook.AbuDawud -> R.string.hadith_book_abudawud
        HadithBook.Tirmidhi -> R.string.hadith_book_tirmidhi
        HadithBook.Nasai -> R.string.hadith_book_nasai
        HadithBook.IbnMajah -> R.string.hadith_book_ibnmajah
        HadithBook.Malik -> R.string.hadith_book_malik
        HadithBook.Nawawi -> R.string.hadith_book_nawawi
    },
)

/**
 * A number the source gives as a number (a hadith's, a section's, a reference's), in the chosen language's digits
 * (INV-L2). `402.2` keeps its point. Texts the source writes (hadiths, grades, names) are never passed through this.
 */
internal fun sourceNumber(written: String, language: Language): String = when (language) {
    Language.Bengali -> toBengaliDigits(written)
    Language.English -> written
}

/** On every hadith screen, above any text (ADR-28): not reviewed, where it came from, translator unknown. */
internal fun hadithUnreviewedBanner(context: Context): LinearLayout = context.panel().apply {
    addView(context.body(context.getString(R.string.hadith_books_unreviewed_banner)))
}

/** Opens [book] off the main thread (the first open after an install parses and caches it) and hands it back on it. */
internal fun whenHadithBookOpens(activity: BaseActivity, book: HadithBook, then: (HadithBookOpen) -> Unit) {
    val context = activity.applicationContext
    Thread {
        val opened = openHadithBook(context, book)
        activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) then(opened) }
    }.start()
}
