package com.senseix21.muminsday

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/**
 * A placeholder so the app installs and opens. It shows the product name and nothing else: the real screens are
 * phase 4, and no Bengali copy is written before a native speaker supplies it (INV-L1).
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                setText(R.string.app_name)
                gravity = Gravity.CENTER
                textSize = 24f
            },
        )
    }
}
