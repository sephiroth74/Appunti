package it.sephiroth.android.app.appunti.db

import android.net.Uri
import com.dbflow5.annotation.Database
import com.dbflow5.config.DBFlowDatabase
import it.sephiroth.android.app.appunti.BuildConfig


@Database(version = AppDatabase.VERSION)
abstract class AppDatabase : DBFlowDatabase() {
    override fun backupEnabled(): Boolean = true

    fun buildUri(vararg paths: String): Uri {
        val builder = Uri.parse(AppDatabase.BASE_CONTENT_URI + BuildConfig.APPLICATION_ID).buildUpon()
        for (path in paths) {
            builder.appendPath(path)
        }
        return builder.build()
    }

    companion object {
        const val VERSION = 1
        const val BASE_CONTENT_URI = "content://"
    }
}