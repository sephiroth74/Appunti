package it.sephiroth.android.library.kotlin_extensions.view

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import timber.log.Timber

fun View.showSoftInput() {
    Timber.i("showSoftInput($this)")
    requestFocus()

    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
//    imm?.toggleSoftInputFromWindow(windowToken, 0, 0)
    imm?.showSoftInput(this, 0)
//    imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
}

fun View.hideSoftInput() {
    clearFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    if (imm != null && imm.isActive(this)) {
        Timber.i("hideSoftInput($this)")
        imm.hideSoftInputFromWindow(windowToken, 0)
    }
}