package it.sephiroth.android.library.kotlin_extensions.lang

import android.os.Looper

fun currentThread() = Thread.currentThread()

fun isMainThread() = Thread.currentThread() == Looper.getMainLooper().thread
