package it.sephiroth.android.app.appunti

import android.app.Application
import android.os.Build
import com.dbflow5.config.DatabaseConfig
import com.dbflow5.config.FlowConfig
import com.dbflow5.config.FlowManager
import com.dbflow5.query.selectCountOf
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.save
import com.jakewharton.threetenabp.AndroidThreeTen
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import it.sephiroth.android.app.appunti.ext.ioThread
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.app.appunti.utils.ShortcutHelper
import timber.log.Timber


@Suppress("unused")
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.i("SDK Version = ${Build.VERSION.SDK_INT}")
        }

        AndroidThreeTen.init(this)

//        FlowLog.setMinimumLoggingLevel(FlowLog.Level.V)

        FlowManager.init(
            FlowConfig.Builder(this)
                .database(
                    DatabaseConfig.Builder(AppDatabase::class.java)
                        .modelNotifier(DirectModelNotifier.get())
                        .build()
                )
                .build()
        )

        ShortcutHelper.getInstance(this).updateShortcuts()

        ioThread {
            if (SettingsManager.getInstance(this).isFirstLaunch) {

                val hasData = selectCountOf(Entry_Table.entryID)
                    .from(Entry::class)
                    .hasData(FlowManager.getDatabase(AppDatabase::class.java))

                if (!hasData) {
                    prepopulateDatabase()
                }
            }
        }
    }

    private fun prepopulateDatabase() {
        Timber.i("prepopulateDatabase")

        var category = Category().apply {
            categoryTitle = getString(R.string.demo_category_personal)
            categoryColorIndex = 9
        }
        category.save()

        Entry().apply {
            this.entryTitle = "Welcome to your first Note"
            this.entryText = "This is an simple text note so show you how simple it is"
            this.category = category
            this.entryPinned = 1
            this.touch()
            this.save()
        }

        Category().apply {
            categoryTitle = getString(R.string.demo_category_work)
            categoryColorIndex = 6
            save()
        }

        Category().apply {
            categoryTitle = getString(R.string.demo_category_other)
            categoryColorIndex = 2
            save()
        }

//        }.build().execute()
    }
}