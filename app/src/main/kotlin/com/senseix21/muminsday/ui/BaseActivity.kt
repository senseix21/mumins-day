package com.senseix21.muminsday.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.store.Stores

/**
 * Every screen is an Activity on this base: no navigation library (ADR-11), and the system's own back button works. It
 * puts the user's chosen language on the screen's resources whatever the phone's language is (F1: the app has its own
 * language setting), and lays the content in a scroll view so large text never clips (INV-L4).
 */
abstract class BaseActivity : Activity() {

    /** The language this screen was created with. Changing it calls [recreate]. */
    protected lateinit var language: Language
        private set

    override fun attachBaseContext(base: Context) {
        val chosen = Stores.settings(base).readApp().language
        language = chosen
        super.attachBaseContext(base.inLanguage(chosen))
    }

    override fun onResume() {
        super.onResume()
        // A screen further back in the stack was built before the language was changed: build it again in the new one.
        if (Stores.settings(this).readApp().language != language) recreate()
    }

    /** Shows [content] in a scroll view with the screen's own margins, clear of the system bars. */
    protected fun show(content: LinearLayout) {
        content.setPadding(dp(16), dp(16), dp(16), dp(32))
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.paper)
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // From Android 15 an app that targets 35 or later draws under the status and navigation bars.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scroll.setOnApplyWindowInsetsListener { view: View, insets: WindowInsets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(scroll)
    }
}
