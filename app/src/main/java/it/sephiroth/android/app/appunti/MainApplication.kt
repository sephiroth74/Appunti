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
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.app.appunti.utils.ShortcutHelper
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnScheduler
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

        FlowManager.init(
            FlowConfig.Builder(this)
                .database(
                    DatabaseConfig.Builder(AppDatabase::class.java)
                        .modelNotifier(DirectModelNotifier.get())
                        .build()
                )
                .openDatabasesOnInit(true)
                .build()
        )

        ShortcutHelper.getInstance(this).updateShortcuts()

        doOnScheduler(Schedulers.io()) {
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
            this.entryText =
                "This is an simple text note to show you how simple it is.\n\nCheers,\nAlessandro\nhttp://blog.sephiroth.it"
            this.category = category
            this.entryPinned = 1
            this.touch()
            this.save()
        }

        category = Category().apply {
            categoryTitle = getString(R.string.demo_category_work)
            categoryColorIndex = 6
            save()
        }

        Entry.fromString("[ ] Bread\n[ ] Cheese\n[x] Beer. Indeed!\n")
            .apply {
                this.entryTitle = "Shopping List"
                this.category = category
                this.entryPinned = 0
                this.touch()
                this.save()
            }

        Category().apply {
            categoryTitle = getString(R.string.demo_category_other)
            categoryColorIndex = 2
            save()
        }

    }
}