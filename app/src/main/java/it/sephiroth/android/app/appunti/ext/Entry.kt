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
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern
import kotlin.contracts.ExperimentalContracts
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
        Timber.v("asList(), entryText = $entryText")

        return try {
            val data = Gson().fromJson<MutableList<EntryListJsonModel.EntryJson>>(entryText)
            val sortedData = data.sortedWith(EntryListJsonModel.listComparator)

            sortedData.forEachIndexed { index, entryJson ->
                if (index > 0) {
                    val prevPosition = sortedData[index - 1].position
                    val currentPosition = entryJson.position

                    if (prevPosition >= currentPosition) {
                        entryJson.position = prevPosition + 1
                    }
                }
            }

            Timber.v("sortedData = $sortedData")

            val uncheckedList =
                (sortedData.filter { entry -> !entry.checked }.sortedWith(EntryListJsonModel.listComparator)).toMutableList()
            val checkedList =
                (sortedData.filter { entry -> entry.checked }.sortedWith(EntryListJsonModel.listComparator)).toMutableList()
            Triple(sortedData.toMutableList(), uncheckedList, checkedList)
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            Triple(mutableListOf(), mutableListOf(), mutableListOf())
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

            EntryListJsonModel.EntryJson(id.toInt(), item, false)
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
        Entry.EntryType.TEXT -> getTextSummary(maxChars, "...")
        Entry.EntryType.LIST -> asList(context, textSize, maxLines)
    }
}

fun Entry.getTextSummary(maxLength: Int = 100, postfix: String? = null): Spannable {
    return if (entryText.length <= maxLength) SpannableString(entryText)
    else SpannableString(entryText.substring(0, maxLength) + (postfix ?: ""))
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

fun Entry.parseRemoteUrls(): List<String> {

    Timber.i("parseRemoteUrls")

    fun addHTTPSIfRequired(url: String): String {
        if (url.startsWith("http://", true)) {
            return url.replace("http://", "https://", true)
        } else if (url.startsWith("www.", true)) {
            return "https://$url"
        }
        return url
    }

    val result = mutableListOf<String>()
    val pattern = Pattern.compile(
//        "(http:\\/\\/www\\.|https:\\/\\/www\\.|http:\\/\\/|https:\\/\\/)[a-z0-9]+([\\-\\.]{1}[a-z0-9]+)*\\.[a-z]{2,5}(:[0-9]{1,5})?(\\/[^\\s\\n\\\"\\']*)?",
        "((https?:\\/\\/(www\\.)?)|www\\.)[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,4}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=,]*)",
        Pattern.CASE_INSENSITIVE
    )

    if (entryType == Entry.EntryType.TEXT) {
        val m = pattern.matcher(entryText)

        while (m.find()) {
            val url = addHTTPSIfRequired(m.group())
            result.add(url)
        }
    } else {
        asList()?.let { triple ->
            for (line in triple.first) {
                val m = pattern.matcher(line.text)

                while (m.find()) {
                    val url = addHTTPSIfRequired(m.group())
                    result.add(url)
                }
            }
        }
    }

    return result.toList()
}