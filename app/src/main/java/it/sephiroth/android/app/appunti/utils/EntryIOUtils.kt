package it.sephiroth.android.app.appunti.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.asList
import it.sephiroth.android.app.appunti.models.EntryListJsonModel
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern

object EntryIOUtils {

    /**
     * Given a text, this methods try to convert into a entry list json
     */
    fun convertStringToList(text: String): String? {
        val pattern1: Pattern = Pattern.compile(
            "^\\[(x|\\s)]\\s(.*)$",
            Pattern.MULTILINE or Pattern.UNIX_LINES or Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )

        val pattern2: Pattern = Pattern.compile(
            "\\[(x|\\s)]\\s([^\n]*)\n?",
            Pattern.UNIX_LINES or Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )

        val matcher1 = pattern1.matcher(text)
        Timber.v("matcher = ${matcher1.matches()}")

        if (matcher1.matches()) {
            val array = arrayListOf<EntryListJsonModel.EntryJson>()
            var id = 0L

            val matcher2 = pattern2.matcher(text)

            while (matcher2.find()) {
                if (matcher2.groupCount() == 2) {
                    val checked = matcher2.group(1) == "x"
                    EntryListJsonModel.EntryJson(id, id.toInt(), matcher2.group(2), checked)
                        .apply {
                            array.add(this)
                        }

                    id++
                }
            }
            return Gson().toJson(array, object : TypeToken<ArrayList<EntryListJsonModel.EntryJson>>() {}.type)
        }
        return null
    }

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun convertEntryToString(entry: Entry): String {
        if (entry.entryType == Entry.EntryType.TEXT) return entry.entryText
        val result = entry.asList()

        result?.let { result ->
            val builder = StringBuilder()

            for (item in result.second) {
                builder.append("[ ] ${item.text}\n")
            }

            for (item in result.third) {
                builder.append("[x] ${item.text}\n")
            }

            return builder.toString()
        }

        return entry.entryText
    }
}