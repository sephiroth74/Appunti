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
        return MaterialBackgroundDrawable.Builder(
            type = MaterialShape.Type.START,
            body = {
                checked(MaterialShapeDrawable.Style {
                    tint(context.theme.getColor(context, R.attr.colorAccent))
                    alpha(context.theme.getFloat(context, android.R.attr.disabledAlpha) ?: 1f)
                })
                selected(MaterialShapeDrawable.Style {
                    tint(context.theme.getColor(context, R.attr.colorAccent))
                    alpha(context.theme.getFloat(context, android.R.attr.disabledAlpha) ?: 1f)
                })
                ripple(context.theme.getColor(context, R.attr.colorControlHighlight))
            }).build()
    }

    fun categoryChip(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder(MaterialShape.Type.ALL) {
                normal(
                    MaterialShapeDrawable.Style {
                        color(context.theme.getColor(context, R.attr.colorControlNormal))
                        strokeWidth(context.resources.getDimension(R.dimen.appunti_category_chip_strokeWidth))
                        style(Paint.Style.STROKE)
                    })
                ripple(
                    context.theme.getColor(context, R.attr.colorControlHighlight),
                    MaterialShapeDrawable.Style {
                        strokeWidth(context.resources.getDimension(R.dimen.appunti_category_chip_strokeWidth))
                    })
            }.build()
    }

    fun categoryChipClickable(context: Context): Drawable {
        return MaterialBackgroundDrawable
            .Builder(MaterialShape.Type.ALL) {
                normal(MaterialShapeDrawable.Style {
                    color(context.theme.getColor(context, R.attr.colorControlNormal))
                    strokeWidth(context.resources.getDimension(R.dimen.appunti_category_chip_strokeWidth))
                    style(Paint.Style.STROKE)
                })
                ripple(context.theme.getColor(context, R.attr.colorControlHighlight),
                    MaterialShapeDrawable.Style {
                        strokeWidth(context.resources.getDimension(R.dimen.appunti_category_chip_strokeWidth))
                    })
            }.build()
    }

    fun newEntryListItem(context: Context): Drawable {
        return MaterialBackgroundDrawable.Builder(MaterialShape.Type.ALL) {
            ripple(context.theme.getColor(context, R.attr.colorControlHighlight))
        }.build()
    }

    fun categoryItemDrawable(context: Context): Drawable {
        return MaterialBackgroundDrawable.Builder(MaterialShape.Type.END) {
            normal(MaterialShapeDrawable.Style {
                tint(context.theme.getColor(context, R.attr.colorControlHighlight))
                alpha(context.theme.getFloat(context, android.R.attr.disabledAlpha) ?: 1f)
            })
            selected(MaterialShapeDrawable.Style {
                tint(context.theme.getColor(context, R.attr.colorAccent))
                alpha(context.theme.getFloat(context, android.R.attr.disabledAlpha) ?: 1f)
            })
            ripple(context.theme.getColor(context, R.attr.colorControlHighlight))
        }.build()
    }

    fun materialButton(context: Context): Drawable {
        return MaterialBackgroundDrawable.Builder(MaterialShape.Type.ALL) {
            normal(MaterialShapeDrawable.Style {
                color(context.theme.getColor(context, R.attr.colorButtonNormal))
                style(Paint.Style.FILL)
            })
            pressed(MaterialShapeDrawable.Style {
                color(context.theme.getColor(context, R.attr.colorButtonNormal))
                style(Paint.Style.FILL)
            })
            ripple(context.theme.getColor(context, R.attr.colorControlHighlight))
        }.build()
    }
}