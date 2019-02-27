package it.sephiroth.android.app.appunti.ext

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.text.style.StrikethroughSpan
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LiveData
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.models.EntryListJsonModel
import java.util.ArrayList
import kotlin.Comparator
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@UseExperimental(ExperimentalContracts::class)
inline fun Entry?.isNull(): Boolean {
    contract {
        returns(false) implies (this@isNull != null)
    }

    return this == null
}

@UseExperimental(ExperimentalContracts::class)
inline fun <T, R> LiveData<T>.whenNotNull(block: (T) -> R): R? {
    return this.value?.let(block)
}

fun Entry.getAttachmentColor(context: Context): Int {
    val outHSL = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(getColor(context), outHSL)
    outHSL[2] = outHSL[2] / 1.1f
    return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(outHSL), 255)
}

fun Entry.asList(): Triple<MutableList<EntryListJsonModel.EntryJson>, MutableList<EntryListJsonModel.EntryJson>, MutableList<EntryListJsonModel.EntryJson>>? {
    if (entryType == Entry.EntryType.LIST) {

        val listComparator = Comparator<EntryListJsonModel.EntryJson> { o1, o2 ->
            if (o1.checked == o2.checked) {
                if (o1.position < o2.position) -1 else 1
            } else {
                if (o1.checked) 1 else -1
            }
        }

        try {
            val data = Gson().fromJson<MutableList<EntryListJsonModel.EntryJson>>(entryText)
            val uncheckedList = (data.filter { entry -> !entry.checked }.sortedWith(listComparator)).toMutableList()
            val checkedList = (data.filter { entry -> entry.checked }.sortedWith(listComparator)).toMutableList()
            return Triple(data, uncheckedList, checkedList)
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            return Triple(mutableListOf(), mutableListOf(), mutableListOf())
        }
    }
    return null
}

fun Entry.convertToList(): Boolean {
    if (entryType == Entry.EntryType.TEXT) {
        val list = entryText.split(Regex("(\\r?\\n)|(\\.(\\s*\\r?\\n?))", RegexOption.IGNORE_CASE))
        val array = arrayListOf<EntryListJsonModel.EntryJson>()
        var id = 0L
        for (item in list) {
            if (item.isEmpty() || item.isBlank()) continue

            EntryListJsonModel.EntryJson(id, id.toInt(), item, false)
                .apply {
                    array.add(this)
                }

            id++
        }

        val string = Gson().toJson(array, object : TypeToken<ArrayList<EntryListJsonModel.EntryJson>>() {}.type)
        with(this) {
            entryType = Entry.EntryType.LIST
            entryText = string
            return true
        }
    } else {
        return false
    }
}

fun Entry.getSummary(context: Context, textSize: Float, maxChars: Int = 100, maxLines: Int = 10): Spannable {
    return when (entryType) {
        Entry.EntryType.TEXT -> getTextSummary(maxChars)
        Entry.EntryType.LIST -> asList(context, textSize, maxLines)
    }
}

fun Entry.getTextSummary(maxLength: Int = 100, postfix: String? = null): Spannable {
    return if (entryText.length <= maxLength) SpannableString(entryText)
    else SpannableString(entryText.substring(0, maxLength) + postfix)
}

fun Entry.asList(context: Context, textSize: Float, maxLines: Int = 10): Spannable {
    val result = this.asList() ?: return SpannableString("")
    val spannable = SpannableStringBuilder("")

    val uncheckedDrawable =
        context.resources.getDrawable(R.drawable.outline_check_box_outline_blank_24, context.theme).apply {
            setBounds(0, 0, textSize.toInt(), textSize.toInt())
        }

    val checkedDrawable = context.resources.getDrawable(R.drawable.outline_check_box_24, context.theme).apply {
        setBounds(0, 0, textSize.toInt(), textSize.toInt())
    }

    var totalLines = 0

    for (item in result.second) {
        val startIndex = spannable.length
        spannable.append("* ${item.text}\n")
        spannable.setSpan(
            ImageSpan(uncheckedDrawable, ImageSpan.ALIGN_BASELINE),
            startIndex,
            startIndex + 1,
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )
        if (++totalLines >= maxLines) return spannable
    }

    for (item in result.third) {
        val startIndex = spannable.length
        spannable.append("* ${item.text}\n")
        spannable.setSpan(
            ImageSpan(checkedDrawable, ImageSpan.ALIGN_BASELINE),
            startIndex,
            startIndex + 1,
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(StrikethroughSpan(), startIndex + 2, spannable.length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        if (++totalLines >= maxLines) return spannable
    }

    return spannable
}