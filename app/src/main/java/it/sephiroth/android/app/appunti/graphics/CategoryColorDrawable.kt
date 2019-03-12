package it.sephiroth.android.app.appunti.graphics

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.library.kotlin_extensions.content.res.getColor

class CategoryColorDrawable(context: Context, color: Int) : ColorDrawable(color) {
    val strokeColorNormal = context.theme.getColor(context, R.attr.colorControlNormal)
    val strokeColorPressed = context.theme.getColor(context, R.attr.colorControlActivated)
    val strokeWidth = context.resources.getDimension(R.dimen.appunti_category_color_drawable_stroke_width)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val dstBounds: Rect = Rect()

    private val mark = context.getDrawable(R.drawable.sharp_done_24)
    private var mChecked: Boolean = false

    init {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = strokeWidth
        strokePaint.color = strokeColorNormal
        fillPaint.color = color
    }

    override fun setColorFilter(color: Int, mode: PorterDuff.Mode) {
        super.setColorFilter(color, mode)
        fillPaint.color = color
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        super.setColorFilter(colorFilter)
        fillPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun isStateful(): Boolean {
        return true
    }

    override fun onBoundsChange(bounds: Rect?) {
        super.onBoundsChange(bounds)
        dstBounds.set(bounds)
        dstBounds.inset(strokeWidth.toInt(), strokeWidth.toInt())

        val markBounds = Rect(dstBounds)
        markBounds.inset(strokeWidth.toInt() * 2, strokeWidth.toInt() * 2)
        mark?.bounds = markBounds
    }


    override fun onStateChange(stateSet: IntArray?): Boolean {
        val pressed = stateSet?.contains(android.R.attr.state_pressed) ?: kotlin.run { false }
        val enabled = stateSet?.contains(android.R.attr.state_enabled) ?: kotlin.run { false }
        mChecked = stateSet?.contains(android.R.attr.state_checked) ?: kotlin.run { false }
        mChecked = mChecked or (stateSet?.contains(android.R.attr.state_selected) ?: kotlin.run { false })

        strokePaint.color = if (pressed) strokeColorPressed else strokeColorNormal
        strokePaint.alpha = if (enabled) 255 else 51
        fillPaint.alpha = if (enabled) 255 else 51

        return super.onStateChange(stateSet)
    }

    override fun draw(canvas: Canvas) {
        canvas
            .drawCircle(dstBounds.centerX().toFloat(), dstBounds.centerY().toFloat(), (dstBounds.width() / 2).toFloat(), fillPaint)
        canvas.drawCircle(dstBounds.centerX().toFloat(), dstBounds.centerY().toFloat(), (dstBounds.width() / 2).toFloat(),
                strokePaint)

        if (mChecked) {
            mark?.draw(canvas)
        }
    }
}