@file:Suppress("NAME_SHADOWING")

package it.sephiroth.android.app.appunti.workers

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.*
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.CustomEvent
import com.dbflow5.isNullOrEmpty
import com.dbflow5.structure.save
import it.sephiroth.android.app.appunti.BuildConfig
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import it.sephiroth.android.app.appunti.db.tables.RemoteUrl
import it.sephiroth.android.app.appunti.ext.parseRemoteUrls
import it.sephiroth.android.library.kotlin_extensions.lang.currentThread
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import timber.log.Timber
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException


class RemoteUrlParserWorker(context: Context, val workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val answers: Answers by lazy { Answers.getInstance() }

    @SuppressLint("CheckResult")
    override fun doWork(): Result {
        Timber.i("[${currentThread()}] doWork($workerParams)")

        answers.logCustom(
            CustomEvent("remoteUrlWorker.init")
                .putCustomAttribute("runAttemptCount", workerParams.runAttemptCount)
        )

        // run worker on active entries only
        DatabaseHelper.getEntries {
            where(Entry_Table.entryDeleted.eq(0)).and(Entry_Table.entryArchived.eq(0))
        }.subscribe { result, _ ->
            result?.let {
                parseEntries(it)
            }
        }

        return Result.success()
    }

    private fun parseEntries(entries: MutableList<Entry>) {
        Timber.i("parseEntries")
        for (entry in entries) {
            parseEntry(entry)
        }
    }

    private fun parseEntry(entry: Entry) {
        Timber.i("parseEntry($entry)")

        val parsedRemoteUrls = entry.parseRemoteUrls()
        answers.logCustom(
            CustomEvent("remoteUrlWorker.entry.parseRemoteUrls").putCustomAttribute(
                "size",
                parsedRemoteUrls.size
            )
        )

        if (parsedRemoteUrls.isNotEmpty()) {
            Timber.v("parsed remoteUrls (size = ${parsedRemoteUrls.size}) = $parsedRemoteUrls")
            val entryRemoteUrls = entry.getAllRemoteUrls()?.toMutableList() ?: mutableListOf()

            val entryRemoteUrlsList = entryRemoteUrls.map { it.remoteParsedString }.toMutableList().apply {
                addAll(entryRemoteUrls.map { it.remoteUrlOriginalUri })
            }

            Timber.v("entryRemoteUrlsMap: $entryRemoteUrlsList")
            Timber.v("parsedRemoteUrls: $parsedRemoteUrls")

            for (urlString in parsedRemoteUrls) {
                Timber.v("processing $urlString, (contains: ${entryRemoteUrlsList.contains(urlString)})")
                if (!entryRemoteUrlsList.contains(urlString)) {
                    try {
                        tryConnect(urlString)?.also { doc ->
                            retrievePageInfo(doc)?.let { remoteUrl ->
                                remoteUrl.remoteUrlEntryID = entry.entryID
                                remoteUrl.remoteParsedString = urlString

                                if (remoteUrl.save()) {
                                    answers.logCustom(CustomEvent("remoteUrlWorker.entry.addRemoteUrl"))
                                    Timber.v("added $remoteUrl to ${entry.entryID}")
                                    entryRemoteUrlsList.add(urlString)
                                    entry.invalidateRemoteUrls()
                                    //return
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        Crashlytics.logException(t)
                    }
                } else {
                    Timber.w("entry already contains this url")
                    answers.logCustom(CustomEvent("remoteUrlWorker.entry.alreadyContainsUrl"))
                }
            }
        }
    }

    private fun connect(urlString: String): Document {
        Timber.i("connect($urlString)")
        return Jsoup.connect(urlString).followRedirects(true).get()
    }

    private fun tryConnect(urlString: String): Document? {
        try {
            return connect(urlString)
        } catch (e: SSLHandshakeException) {
            e.printStackTrace()

            answers.logCustom(CustomEvent("remoteUrlWorker.entry.sslException"))

            if (urlString.startsWith("https://", true)) {
                Timber.v("trying with http...")
                return try {
                    val newUrl = urlString.replaceFirst(Regex("^https:\\/\\/", RegexOption.MULTILINE), "http://")
                    connect(newUrl)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    Crashlytics.logException(t)
                    null
                }
            }

            return null
        } catch (t: Throwable) {
            t.printStackTrace()
            Crashlytics.logException(t)
            return null
        }
    }

    private fun getAttribute(e: Element, vararg names: String): String? {
//        Timber.d("getAttribute($e, $names)")
        for (name in names) {
            if (e.attr("property").equals(name, true) ||
                e.attr("itemprop").equals(name, true) ||
                e.attr("name").equals(name, true)
            ) {
                return e.attr("content")
            }
        }
        return null
    }

    private fun findAttribute(e: Elements, vararg names: String): String? {
        e.forEach { e ->
            getAttribute(e, *names)?.let {
                return it
            }
        }
        return null
    }

    private fun retrievePageInfo(doc: Document): RemoteUrl? {
        val url = URL(doc.location())
        val fullUrlString = doc.location()

        var imageUrl: String? = null
        var title: String? = null
        var description: String? = null

        val elements = doc.select("meta")

        imageUrl = findAttribute(elements, "image", "og:image")
        title = findAttribute(elements, "og:title", "title", "name")
        description = findAttribute(elements, "og:description", "description")

        Timber.i("meta info")
        Timber.v("title = $title")
        Timber.v("description = $description")
        Timber.v("imageUrl = $imageUrl")

        if (null == imageUrl) {
            doc.select("link[rel=icon]").forEach { element ->
                imageUrl = element.attr("href")
            }
        }

        if (title.isNullOrEmpty()) {
            title = url.host
        } else {
            description = fullUrlString
        }

        if (description.isNullOrEmpty()) description = fullUrlString

        imageUrl = normalizeImageUrl(fullUrlString, imageUrl)

        Timber.i("final values")
        Timber.v("title = $title")
        Timber.v("description = $description")
        Timber.v("imageUrl = $imageUrl")

        answers.logCustom(
            CustomEvent("remoteUrlWorker.entry.retrievePageInfo")
                .putCustomAttribute("hasTitle", if (title.isNullOrEmpty()) 0 else 1)
                .putCustomAttribute("hasImage", if (imageUrl.isNullOrEmpty()) 0 else 1)
                .putCustomAttribute("hasDescription", if (description.isNullOrEmpty()) 0 else 1)
        )

        return RemoteUrl().apply {
            remoteUrlOriginalUri = doc.location()
            remoteThumbnailUrl = imageUrl
            remoteUrlTitle = title
            remoteUrlDescription = description
        }
    }

    private fun normalizeImageUrl(basePath: String, imageUrl: String?): String? {
        imageUrl?.let { imageUrl ->
            Timber.i("normalizeImageUrl($basePath, $imageUrl)")

            var newImageUrl = imageUrl.replace(
                Regex(
                    "^(//geo[0-9]+\\.ggpht\\.com)",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

                ), "http:$1"
            )

            if (newImageUrl.startsWith("/")) {
                val resultURI = URI(basePath).resolve(newImageUrl)
                newImageUrl = resultURI.toString()
            }
            return newImageUrl
        } ?: run {
            return null
        }
    }

    companion object {
        fun createPeriodicWorker() {
            Timber.i("createPeriodicWorker")

            Answers.getInstance().logCustom(CustomEvent("remoteUrlWorker.create"))

            val saveRequest =
                PeriodicWorkRequestBuilder<RemoteUrlParserWorker>(
                    if (BuildConfig.DEBUG) 15L else 5L,
                    if (BuildConfig.DEBUG) TimeUnit.MINUTES else TimeUnit.HOURS
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresCharging(false)
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
            WorkManager.getInstance()
                .enqueueUniquePeriodicWork("remoteUrlWorker", ExistingPeriodicWorkPolicy.REPLACE, saveRequest)
        }
    }
}