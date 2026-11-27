package com.senseix21.muminsday.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

/**
 * Plain, calm, high contrast (G12 is not decided: nothing here is a design). Text is black on white, buttons are dark
 * green with white text, a warning is a pale panel with a dark outline. Every size is in sp or dp, no view has a fixed
 * height, so nothing clips at 200 % font scale (INV-L4).
 */
object Palette {
    val ink: Int = Color.parseColor("#111111")
    val muted: Int = Color.parseColor("#404040")
    val paper: Int = Color.WHITE
    val accent: Int = Color.parseColor("#14532D")
    val onAccent: Int = Color.WHITE
    val warnFill: Int = Color.parseColor("#FFF4CC")
    val warnLine: Int = Color.parseColor("#7A5B00")
    val line: Int = Color.parseColor("#BDBDBD")
}

/** The smallest touch target the spec allows (constraints: accessibility). */
const val MIN_TARGET_DP: Int = 48

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun Context.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

private fun Context.text(text: CharSequence, sizeSp: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(8) }
}

fun Context.title(text: CharSequence): TextView = text(text, 26f, Palette.ink, true).apply { setPadding(0, dp(8), 0, dp(8)) }
    .also { it.isFocusable = true }

fun Context.heading(text: CharSequence): TextView = text(text, 20f, Palette.ink, true).apply {
    (layoutParams as LinearLayout.LayoutParams).topMargin = dp(20)
}

fun Context.body(text: CharSequence, muted: Boolean = false): TextView =
    text(text, 18f, if (muted) Palette.muted else Palette.ink, false)

/** A button of at least 48 dp. [primary] is the filled one; the other is outlined. Its label is its accessible name. */
fun Context.actionButton(label: CharSequence, primary: Boolean = true, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    isAllCaps = false
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    setTextColor(if (primary) Palette.onAccent else Palette.accent)
    minHeight = dp(MIN_TARGET_DP)
    minimumHeight = dp(MIN_TARGET_DP)
    gravity = Gravity.CENTER
    setPadding(dp(16), dp(12), dp(16), dp(12))
    val shape = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        if (primary) setColor(Palette.accent) else setStroke(dp(2), Palette.accent)
        if (!primary) setColor(Palette.paper)
    }
    background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33000000")), shape, null)
    stateListAnimator = null
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(12) }
    setOnClickListener { onClick() }
}

/** A pale panel with a dark outline for something the user must act on (INV-S6). */
fun Context.panel(): LinearLayout = column().apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(12) }
    setPadding(dp(16), dp(12), dp(16), dp(16))
    background = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(Palette.warnFill)
        setStroke(dp(2), Palette.warnLine)
    }
}

/** One choice out of a few, as radio buttons of at least 48 dp. Calls [onSelected] only when the user changes it. */
fun <T> Context.choices(options: List<Pair<String, T>>, selected: T, onSelected: (T) -> Unit): RadioGroup {
    val group = RadioGroup(this).apply {
        orientation = RadioGroup.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    var current = selected
    val ids = options.map { View.generateViewId() }
    options.forEachIndexed { index, (label, value) ->
        group.addView(
            RadioButton(this).apply {
                id = ids[index]
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Palette.ink)
                minHeight = dp(MIN_TARGET_DP)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isChecked = value == selected
            },
            RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }
    group.setOnCheckedChangeListener { _, checkedId ->
        val index = ids.indexOf(checkedId)
        if (index >= 0 && options[index].second != current) {
            current = options[index].second
            onSelected(current)
        }
    }
    return group
}
