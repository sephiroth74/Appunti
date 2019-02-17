package it.sephiroth.android.app.appunti.ext

import android.content.Context
import androidx.core.graphics.ColorUtils
import it.sephiroth.android.app.appunti.db.tables.Entry


fun Entry.getAttachmentColor(context: Context): Int {
    val outHSL = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(getColor(context), outHSL)
    outHSL[2] = outHSL[2] / 1.35f
    return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(outHSL), 201)
}