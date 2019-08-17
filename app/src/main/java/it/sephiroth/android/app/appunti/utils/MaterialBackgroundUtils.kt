package it.sephiroth.android.app.appunti.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import getFloat
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.library.kotlin_extensions.content.res.getColor
import it.sephiroth.android.library.material.drawable.graphics.MaterialBackgroundDrawable
import it.sephiroth.android.library.material.drawable.graphics.MaterialShape
import it.sephiroth.android.library.material.drawable.graphics.MaterialShapeDrawable

object MaterialBackgroundUtils {

    fun navigationItemDrawable(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder()
            .addChecked(
                MaterialShapeDrawable.Builder(MaterialShape.Type.START).tint(
                    context.theme.getColor(context, R.attr.colorAccent)
                ).alpha(context.theme.getFloat(context, android.R.attr.disabledAlpha) ?: 1f)
            )
            .addSelected(
                MaterialShapeDrawable.Builder(MaterialShape.Type.START).tint(
                    context.theme.getColor(context, R.attr.colorAccent)
                ).alpha(context.theme.getFloat(context, android.R.attr.disabledAlpha) ?: 1f)
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

    fun newEntryListItem(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder()
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
                    .alpha(context.theme.getFloat(context, android.R.attr.disabledAlpha) ?: 1f)
            )
            .addSelected(
                MaterialShapeDrawable
                    .Builder(MaterialShape.Type.END)
                    .tint(context.theme.getColor(context, R.attr.colorAccent))
                    .alpha(context.theme.getFloat(context, android.R.attr.disabledAlpha) ?: 1f)
            )
            .ripple(
                context.theme.getColor(context, R.attr.colorControlHighlight),
                MaterialShapeDrawable.Builder(MaterialShape.Type.END)
            )
            .build()
    }

    fun materialButton(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder()
            .addNormal(
                MaterialShapeDrawable
                    .Builder(MaterialShape.Type.ALL)
                    .color(context.theme.getColor(context, R.attr.colorButtonNormal))
                    .style(Paint.Style.FILL)
            )
            .addPressed(
                MaterialShapeDrawable
                    .Builder(MaterialShape.Type.ALL)
                    .color(context.theme.getColor(context, R.attr.colorButtonNormal))
                    .style(Paint.Style.FILL)
            ).ripple(
                context.theme.getColor(context, R.attr.colorControlHighlight),
                MaterialShapeDrawable.Builder(MaterialShape.Type.ALL)
            )
            .build()

    }
}