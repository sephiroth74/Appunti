package it.sephiroth.android.app.appunti

import android.app.Application
import android.os.Build
import com.dbflow5.config.DatabaseConfig
import com.dbflow5.config.FlowConfig
import com.dbflow5.config.FlowLog
import com.dbflow5.config.FlowManager
import com.dbflow5.query.list
import com.dbflow5.query.select
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.insert
import com.jakewharton.threetenabp.AndroidThreeTen
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.ioThread
import it.sephiroth.android.app.appunti.utils.ShortcutUtils
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

        FlowLog.setMinimumLoggingLevel(FlowLog.Level.V)

        FlowManager.init(FlowConfig.Builder(this)
                .database(DatabaseConfig.Builder(AppDatabase::class.java)
                        .modelNotifier(DirectModelNotifier.get())
                        .build())
                .build())

        ShortcutUtils.getInstance(this).updateShortcuts()


        ioThread {
            val size = select().from(Entry::class).list.size
            var categories = select().from(Category::class).list.toList()

            Timber.d("entries size: $size")

            if (categories.isEmpty()) {
                for (i in 0..10) {
                    val category = Category()
                    category.categoryTitle = "Category $i"
                    category.categoryType = Category.CategoryType.USER
                    category.categoryColorIndex = i
                    val id =
                            category.insert()

                    Timber.v("added category = $id")
                }

                categories = select().from(Category::class).list.toList()
            }


            if (size < 10) {
                for (i in 0..10) {
                    val entry = Entry()
                    entry.entryTitle = "Entry ${size + i}"
                    entry.entryPinned = 0
                    entry.entryPriority = 0
                    entry.entryText = getString(R.string.lorem_ipsum)
                    entry.category = categories[i]
                    val id = entry.insert()

                    Timber.v("added entry = $id")
                }
            }

        }
    }
}