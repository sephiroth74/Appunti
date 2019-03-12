package it.sephiroth.android.library.kotlin_extensions.widget

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView

class TextWatcherImpl(val textView: TextView) : TextWatcher {
    private var _afterTextChanged: ((textView: TextView, s: Editable?) -> Unit)? = null
    private var _beforeTextChanged: ((textView: TextView, s: CharSequence?, start: Int, count: Int, after: Int) -> Unit)? =
        null
    private var _onTextChanged: ((textView: TextView, s: CharSequence?, start: Int, before: Int, count: Int) -> Unit)? =
        null

    fun afterTextChanged(func: (textView: TextView, s: Editable?) -> Unit) {
        _afterTextChanged = func
    }

    fun beforeTextChanged(func: (textView: TextView, s: CharSequence?, start: Int, count: Int, after: Int) -> Unit) {
        _beforeTextChanged = func
    }

    fun onTextChanged(func: (textView: TextView, s: CharSequence?, start: Int, before: Int, count: Int) -> Unit) {
        _onTextChanged = func
    }

    override fun afterTextChanged(s: Editable?) {
        _afterTextChanged?.invoke(textView, s)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        _beforeTextChanged?.invoke(textView, s, start, count, after)
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        _onTextChanged?.invoke(textView, s, start, before, count)
    }
}

inline fun TextView.addTextWatcherListener(func: TextWatcherImpl.() -> Unit): TextWatcher {
    val listener = TextWatcherImpl(this)
    listener.func()
    addTextChangedListener(listener)
    return listener
}

inline fun TextView.doOnTextChanged(crossinline action: (s: CharSequence?, start: Int, count: Int, after: Int) -> Unit) =
    addTextChangedListener(onTextChanged = action)

inline fun TextView.doOnAfterTextChanged(crossinline action: (e: Editable) -> Unit) =
    addTextChangedListener(onAfterTextChanged = action)

inline fun TextView.doOnBeforeTextChanged(crossinline action: (s: CharSequence?, start: Int, count: Int, after: Int) -> Unit) =
    addTextChangedListener(onBeforeTextChanged = action)


inline fun TextView.addTextChangedListener(
    crossinline onBeforeTextChanged: (s: CharSequence?, start: Int, count: Int, after: Int) -> Unit = { _, _, _, _ -> },
    crossinline onTextChanged: (s: CharSequence?, start: Int, before: Int, count: Int) -> Unit = { _, _, _, _ -> },
    crossinline onAfterTextChanged: (s: Editable) -> Unit = { }
): TextWatcher {
    val listener = object : TextWatcher {

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
            onTextChanged(s, start, before, count)

        override fun afterTextChanged(s: Editable) = onAfterTextChanged(s)
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) = onBeforeTextChanged(s, start, count, after)


    }

    addTextChangedListener(listener)
    return listener
}