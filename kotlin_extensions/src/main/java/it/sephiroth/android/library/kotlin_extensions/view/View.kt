package it.sephiroth.android.library.kotlin_extensions.view

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun View.showSoftInput() {
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
//    inputMethodManager?.showSoftInput(this, 0)
    inputMethodManager?.toggleSoftInputFromWindow(windowToken, 0, 0)
//    inputMethodManager?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
}

fun View.hideSoftInput() {
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
}