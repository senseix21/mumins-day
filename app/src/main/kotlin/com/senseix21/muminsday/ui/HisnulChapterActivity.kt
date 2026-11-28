package com.senseix21.muminsday.ui

import android.os.Bundle
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.hisnul.ChapterId
import com.senseix21.muminsday.hisnul.Dua
import com.senseix21.muminsday.hisnul.HisnulMuslim
import com.senseix21.muminsday.store.HisnulLoad

/**
 * One chapter of Hisnul Muslim (ADR-27): each dua's parts exactly as the source has them. Arabic right to left in its
 * own isolate (INV-L3) in the bundled Amiri font; the transliteration, the Bengali translation and the reference as
 * written; the notes before and after muted. A field the source does not have is not shown, so a dua without Bengali
 * shows its Arabic alone. No digit, word or line ending in the source text is changed.
 */
class HisnulChapterActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = ChapterId(intent.getIntExtra(EXTRA_CHAPTER, -1))
        val page = column()
        page.addView(title(getString(R.string.hisnul_title)))
        page.addView(body(getString(R.string.hisnul_opening), muted = true))
        show(page)
        whenHisnulOpens(this) { opened ->
            when (opened) {
                is HisnulLoad.Loaded -> render(opened.collection, id)
                HisnulLoad.NotInThisBuild -> showMessage(getString(R.string.hisnul_not_in_build))
                is HisnulLoad.Refused -> showMessage(getString(R.string.hisnul_refused))
            }
        }
    }

    private fun showMessage(text: String) {
        val page = column()
        page.addView(title(getString(R.string.hisnul_title)))
        page.addView(panel().apply { addView(body(text)) })
        show(page)
    }

    private fun render(collection: HisnulMuslim, id: ChapterId) {
        val chapter = collection.chapter(id)
        if (chapter == null) {
            finish()
            return
        }
        val texts = Texts(this, language)
        val page = column()
        page.addView(title(getString(R.string.hisnul_chapter_title, texts.number(chapter.id.value))))
        page.addView(heading(chapter.nameBn))
        page.addView(unreviewedBanner(this))
        for (dua in collection.duasOf(id)) page.addView(duaBlock(dua, texts))
        show(page)
    }

    private fun duaBlock(dua: Dua, texts: Texts): LinearLayout = column().apply {
        dua.number?.let { addView(heading(getString(R.string.hisnul_dua_number, texts.number(it)))) }
        for (segment in dua.segments) {
            segment.before.shown()?.let { addView(body(it, muted = true)) }
            segment.arabic.shown()?.let { addView(arabicText(this@HisnulChapterActivity, it)) }
            segment.transliteration.shown()?.let { addView(body(it, muted = true)) }
            segment.translation.shown()?.let { addView(body(it).apply { textSize = 20f }) }
            segment.after.shown()?.let { addView(body(it, muted = true)) }
            segment.reference.shown()?.let { addView(body(getString(R.string.hisnul_reference, it), muted = true)) }
        }
    }

    /** The source's text when it has one to show; an empty or blank field shows nothing. The text itself is untouched. */
    private fun String?.shown(): String? = this?.takeIf { it.isNotBlank() }

    companion object {
        const val EXTRA_CHAPTER: String = "chapter"
    }
}
