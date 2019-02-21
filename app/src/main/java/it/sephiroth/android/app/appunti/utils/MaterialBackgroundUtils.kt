package it.sephiroth.android.app.appunti.utils

import android.content.Context
import android.graphics.drawable.Drawable
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.ext.getColor
import it.sephiroth.android.app.appunti.graphics.MaterialBackgroundDrawable
import it.sephiroth.android.app.appunti.graphics.MaterialShape
import it.sephiroth.android.app.appunti.graphics.MaterialShapeDrawable

object MaterialBackgroundUtils {

    fun navigationItemDrawable(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder()
            .addChecked(
                MaterialShapeDrawable.Builder(MaterialShape.Type.START).tint(
                    context.theme.getColor(
                        context,
                        R.attr.colorControlActivated
                    )
                )
            )
            .addSelected(
                MaterialShapeDrawable.Builder(MaterialShape.Type.START).tint(
                    context.theme.getColor(
                        context,
                        R.attr.colorControlActivated
                    )
                )
            )
            .ripple(
                context.theme.getColor(context, R.attr.colorControlHighlight),
                MaterialShapeDrawable.Builder(MaterialShape.Type.START)
            )
            .build()
    }
}