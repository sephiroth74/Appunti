package it.sephiroth.android.app.appunti.utils

import android.content.Context
import android.graphics.Paint
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

    fun categoryChip(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder()
            .addNormal(
                MaterialShapeDrawable
                    .Builder(MaterialShape.Type.ALL)
                    .color(context.theme.getColor(context, R.attr.colorControlNormal))
                    .strokeWidth(context.resources.getDimension(R.dimen.appunti_category_chip_strokeWidth))
                    .style(Paint.Style.STROKE)
            )
            .build()
    }

    fun categoryChipClickable(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder()
            .addNormal(
                MaterialShapeDrawable
                    .Builder(MaterialShape.Type.ALL)
                    .color(context.theme.getColor(context, R.attr.colorControlNormal))
                    .strokeWidth(context.resources.getDimension(R.dimen.appunti_category_chip_strokeWidth))
                    .style(Paint.Style.STROKE)
            )
            .ripple(
                context.theme.getColor(context, R.attr.colorControlHighlight),
                MaterialShapeDrawable.Builder(MaterialShape.Type.ALL)
            )
            .build()
    }

    fun categoryItemDrawable(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder()
            .addChecked(
                MaterialShapeDrawable
                    .Builder(MaterialShape.Type.END)
                    .tint(context.theme.getColor(context, R.attr.colorControlHighlight))
            )
            .addSelected(
                MaterialShapeDrawable
                    .Builder(MaterialShape.Type.END)
                    .tint(context.theme.getColor(context, R.attr.colorControlActivated))
            )
            .ripple(
                context.theme.getColor(context, R.attr.colorControlHighlight),
                MaterialShapeDrawable.Builder(MaterialShape.Type.END)
            )
            .build()
    }
}