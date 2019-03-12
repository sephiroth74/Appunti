package it.sephiroth.android.library.kotlin_extensions.kotlin

infix fun Int.hasBits(value: Int): Boolean {
    return this and value == value
}