package it.sephiroth.android.library.kotlin_extensions.app

import android.app.Activity
import android.os.Build

inline val Activity.isInMultiWindow: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInMultiWindowMode
        } else {
            false
        }
    }