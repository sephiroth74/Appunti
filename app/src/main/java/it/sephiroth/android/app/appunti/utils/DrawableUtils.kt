package it.sephiroth.android.app.appunti.utils

import android.graphics.PorterDuff

/**
 * Copyright 2017 Adobe Systems Incorporated.  All rights reserved.
 * $Id$
 * $DateTime$
 * $Change$
 * $File$
 * $Revision$
 * $Author$
 */

object DrawableUtils {
    fun parseTintMode(value: Int, defaultMode: PorterDuff.Mode): PorterDuff.Mode {
        when (value) {
            3 -> return PorterDuff.Mode.SRC_OVER
            5 -> return PorterDuff.Mode.SRC_IN
            9 -> return PorterDuff.Mode.SRC_ATOP
            14 -> return PorterDuff.Mode.MULTIPLY
            15 -> return PorterDuff.Mode.SCREEN
            16 -> return PorterDuff.Mode.ADD
            else -> return defaultMode
        }
    }
}