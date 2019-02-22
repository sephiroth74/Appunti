package it.sephiroth.android.app.appunti.ext

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.Window.ID_ANDROID_CONTENT


fun Activity.getNavigationBarSize(): Point {
    val appUsableSize = getUsableScreenSize()
    val realScreenSize = getRealScreenSize()

    // navigation bar on the side
    return when {
        appUsableSize.x < realScreenSize.x -> Point(realScreenSize.x - appUsableSize.x, appUsableSize.y)
        appUsableSize.y < realScreenSize.y -> // navigation bar at the bottom
            Point(appUsableSize.x, realScreenSize.y - appUsableSize.y)
        else -> // navigation bar is not present
            Point()
    }
}

fun Activity.getUsableScreenSize(): Point {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val display = windowManager.defaultDisplay
    val size = Point()
    display.getSize(size)
    return size
}

fun Activity.getRealScreenSize(): Point {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val display = windowManager.defaultDisplay
    val size = Point()
    display.getRealSize(size)
    return size
}

fun Activity.getStatusbarHeight(): Int {
    var result = 0
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    if (resourceId > 0) {
        result = resources.getDimensionPixelSize(resourceId)
    }
    return result
}