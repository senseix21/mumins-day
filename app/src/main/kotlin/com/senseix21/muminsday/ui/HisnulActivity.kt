package com.senseix21.muminsday.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.hisnul.Chapter
import com.senseix21.muminsday.hisnul.HisnulMuslim
import com.senseix21.muminsday.hisnul.chaptersMatching
import com.senseix21.muminsday.store.HisnulLoad
import com.senseix21.muminsday.store.openHisnulMuslim

/**
 * Hisnul Muslim (ADR-27): the unreviewed banner, a search over the chapters' Bengali names, and the 133 chapters with
 * their numbers in the chosen language's digits. The names are the source's, shown as they are. A release has no files
 * and says so.
 */
class HisnulActivity : BaseActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = column()
        page.addView(title(getString(R.string.hisnul_title)))
        page.addView(body(getString(R.string.hisnul_opening), muted = true))
        show(page)
        whenHisnulOpens(this) { render(it) }
    }

    private fun render(opened: HisnulLoad) {
        val page = column()
        page.addView(title(getString(R.string.hisnul_title)))
        when (opened) {
            HisnulLoad.NotInThisBuild -> page.addView(body(getString(R.string.hisnul_not_in_build)))
            is HisnulLoad.Refused -> page.addView(panel().apply { addView(body(getString(R.string.hisnul_refused))) })
            is HisnulLoad.Loaded -> showChapters(page, opened.collection)
        }
        show(page)
    }

    private fun showChapters(page: LinearLayout, collection: HisnulMuslim) {
        page.addView(unreviewedBanner(this))
        val box = EditText(this).apply {
            hint = getString(R.string.hisnul_search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            textSize = 18f
            minHeight = dp(MIN_TARGET_DP)
            minimumHeight = dp(MIN_TARGET_DP)
            isSingleLine = true
        }
        val search = { fill(chaptersMatching(collection, box.text.toString())) }
        box.setOnEditorActionListener { _, action, _ -> (action == EditorInfo.IME_ACTION_SEARCH).also { if (it) search() } }
        page.addView(box)
        page.addView(actionButton(getString(R.string.hisnul_search), primary = false) { search() })
        page.addView(heading(getString(R.string.hisnul_chapters)))
        list = column()
        page.addView(list)
        fill(collection.chapters)
    }

    private fun fill(chapters: List<Chapter>) {
        list.removeAllViews()
        val texts = Texts(this, language)
        if (chapters.isEmpty()) list.addView(body(getString(R.string.hisnul_no_chapters)))
        for (chapter in chapters) {
            list.addView(
                actionButton(texts.number(chapter.id.value) + ". " + chapter.nameBn, primary = false) {
                    startActivity(Intent(this, HisnulChapterActivity::class.java).putExtra(HisnulChapterActivity.EXTRA_CHAPTER, chapter.id.value))
                },
            )
        }
    }
}

/** On both screens, above any text (ADR-27): not reviewed, where it came from, translator unknown. */
internal fun unreviewedBanner(context: Context): LinearLayout = context.panel().apply {
    addView(context.body(context.getString(R.string.hisnul_unreviewed_banner)))
}

/** Opens the collection off the main thread (313 files the first time) and hands the result back on it. */
internal fun whenHisnulOpens(activity: BaseActivity, then: (HisnulLoad) -> Unit) {
    val context = activity.applicationContext
    Thread {
        val opened = openHisnulMuslim(context)
        activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) then(opened) }
    }.start()
}
