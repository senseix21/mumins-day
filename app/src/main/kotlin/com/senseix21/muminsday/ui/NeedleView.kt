package com.senseix21.muminsday.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.senseix21.muminsday.bearing.Bearing

/**
 * The Qibla needle: a ring, a fixed mark at the top of the phone, and an arrow turned by [angle] (clockwise from the
 * top). No asset, no animation library. When it is not [trusted] the arrow is grey and only an outline, so the
 * difference does not depend on colour (INV-Q2). With no angle only the ring is drawn.
 */
class NeedleView(context: Context) : View(context) {
    private fun dp(value: Int): Int = context.dp(value)

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat(); color = Palette.line
    }
    private val top = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Palette.ink }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private var angle: Bearing? = null
    private var trusted = false

    /** [angle] null draws no arrow. */
    fun show(angle: Bearing?, trusted: Boolean) {
        if (angle == this.angle && trusted == this.trusted) return
        this.angle = angle
        this.trusted = trusted
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val side = minOf(MeasureSpec.getSize(widthSpec), dp(MAX_SIDE_DP))
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        val centre = width / 2f
        val radius = centre - dp(8)
        canvas.drawCircle(centre, centre, radius, ring)
        canvas.drawCircle(centre, centre - radius - dp(2) + dp(8), dp(5).toFloat(), top)

        val degrees = angle?.degrees ?: return
        canvas.save()
        canvas.rotate(degrees.toFloat(), centre, centre)
        val tip = centre - radius * 0.85f
        val wing = radius * 0.16f
        path.reset()
        path.moveTo(centre, tip)
        path.lineTo(centre + wing, centre)
        path.lineTo(centre + wing / 3, centre)
        path.lineTo(centre + wing / 3, centre + radius * 0.4f)
        path.lineTo(centre - wing / 3, centre + radius * 0.4f)
        path.lineTo(centre - wing / 3, centre)
        path.lineTo(centre - wing, centre)
        path.close()
        if (trusted) {
            arrow.style = Paint.Style.FILL
            arrow.color = Palette.accent
        } else {
            arrow.style = Paint.Style.STROKE
            arrow.strokeWidth = dp(3).toFloat()
            arrow.color = GREY
        }
        canvas.drawPath(path, arrow)
        canvas.restore()
    }

    private companion object {
        const val MAX_SIDE_DP = 300
        const val GREY = 0xFF8A8A8A.toInt()
    }
}
