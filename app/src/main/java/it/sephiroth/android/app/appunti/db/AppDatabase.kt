package it.sephiroth.android.app.appunti.db

import android.net.Uri
import com.dbflow5.annotation.Database
import com.dbflow5.annotation.Migration
import com.dbflow5.config.DBFlowDatabase
import com.dbflow5.sql.SQLiteType
import it.sephiroth.android.app.appunti.BuildConfig
import it.sephiroth.android.app.appunti.db.migration.AlterTableMigration
import it.sephiroth.android.app.appunti.db.tables.RemoteUrl
import timber.log.Timber


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
        const val VERSION = 2
        const val BASE_CONTENT_URI = "content://"
    }
}

@Migration(version = 2, database = AppDatabase::class)
class Migration2(table: Class<RemoteUrl>) : AlterTableMigration<RemoteUrl>(table) {

    override fun onPreMigrate() {
        Timber.d("onPreMigrate")
        addColumn(SQLiteType.TEXT, "remoteParsedString")
    }
}