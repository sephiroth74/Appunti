package it.sephiroth.android.app.appunti.workers

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.*
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
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit


class RemoteUrlParserWorker(context: Context, val workerParams: WorkerParameters) : Worker(context, workerParams) {
    @SuppressLint("CheckResult")
    override fun doWork(): Result {
        Timber.i("[${currentThread()}] doWork($workerParams)")

        Answers.getInstance().logCustom(
            CustomEvent("remoteUrlWorker.init")
                .putCustomAttribute("runAttemptCount", workerParams.runAttemptCount)
        )

        DatabaseHelper.getEntries {
            where(Entry_Table.entryDeleted.eq(0))
        }.subscribe { result, error ->
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

        if (entry.hasRemoteUrls()) {
            Timber.v("entry has already a remote url. skipping...")
            Answers.getInstance().logCustom(CustomEvent("remoteUrlWorker.entry.hasRemoteUrls"))
            return
        }

        val remoteUrls = entry.parseRemoteUrls()

        if (remoteUrls.isNotEmpty()) {
            Timber.v("remoteUrls = $remoteUrls")
            val entryRemoteUrls = entry.getAllRemoteUrls()?.toMutableList() ?: mutableListOf()
            Timber.v("entry remote urls = ${entryRemoteUrls.size}")
            for (urlString in remoteUrls) {
                if (!entryRemoteUrls.map { it.remoteUrlOriginalUri }.contains(urlString)) {
                    retrieveUrl(urlString)?.let { remoteUrl ->
                        remoteUrl.remoteUrlEntryID = entry.entryID
                        if (remoteUrl.save()) {
                            Answers.getInstance().logCustom(CustomEvent("remoteUrlWorker.entry.addRemoteUrl"))
                            Timber.v("added $remoteUrl to ${entry.entryID}")
                            entryRemoteUrls.add(remoteUrl)
                            entry.invalidateRemoteUrls()
                            return
                        }
                    }
                }
            }
        }
    }

    private fun retrieveUrl(urlString: String): RemoteUrl? {
        val connection = Jsoup.connect(urlString).followRedirects(true)
        val doc: Document
        val url: URL
        try {
            url = URL(urlString)
            doc = connection.get()
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }

        var imageUrl: String? = null
        var title: String? = null
        var description: String? = null

        doc.select("meta").forEach { e ->
            if (e.attr("property").equals("og:image", true)
                || e.attr("itemprop").equals("image", true)
            ) {
                imageUrl = e.attr("content")
            }

            if (e.attr("property").equals("og:title", true)
                || e.attr("itemprop").equals("name", true)
            ) {
                title = e.attr("content")
            }

            if (e.attr("property").equals("og:description", true)
                || e.attr("itemprop").equals("description", true)
            ) {
                description = e.attr("content")
            }
        }

        if (null == imageUrl) {
            doc.select("link[rel=icon]").forEach { element ->
                imageUrl = element.attr("href")
            }
        }

        if (null == title) title = url.host
        if (null == description) description = urlString

        imageUrl?.let {
            imageUrl = it.replace(
                Regex(
                    "^(//geo[0-9]+\\.ggpht\\.com)",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

                ), "http:$1"
            )
        }

        Timber.v("title = $title")
        Timber.v("description = $description")
        Timber.v("imageUrl = $imageUrl")

        Answers.getInstance().logCustom(
            CustomEvent("remoteUrlWorker.entry.retrieveUrl")
                .putCustomAttribute("hasTitle", if (title.isNullOrEmpty()) 0 else 1)
                .putCustomAttribute("hasImage", if (imageUrl.isNullOrEmpty()) 0 else 1)
                .putCustomAttribute("hasDescription", if (description.isNullOrEmpty()) 0 else 1)
        )

        return RemoteUrl().apply {
            remoteUrlOriginalUri = urlString
            remoteThumbnailUrl = imageUrl
            remoteUrlTitle = title
            remoteUrlDescription = description
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
                .enqueueUniquePeriodicWork("remoteUrlWorker", ExistingPeriodicWorkPolicy.KEEP, saveRequest)
        }
    }
}