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
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.ioThread
import timber.log.Timber


class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.i("SDK Version = ${Build.VERSION.SDK_INT}")
        }

        FlowLog.setMinimumLoggingLevel(FlowLog.Level.V)

        FlowManager.init(FlowConfig.Builder(this)
                .database(DatabaseConfig.Builder(AppDatabase::class.java)
                        .modelNotifier(DirectModelNotifier.get())
                        .build())
                .build())


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
//                    entry.entryPinned = if (Math.random() > 0.5) 1 else 0
//                    entry.entryPriority = (Math.random() * 10).toInt()
                    entry.entryText = getString(R.string.lorem_ipsum)
                    entry.category = categories[i]
                    val id = entry.insert()

                    Timber.v("added entry = $id")
                }
            }


//
//            val category_size = select().from(Category::class).list.size
//            Timber.v("category_size: $category_size")
//            if (category_size < 1) {
//
//                val database = FlowManager.getDatabase(AppDatabase::class.java)
//
//                var category = Category()
//                category.categoryTitle = getString(R.string.category_default)
//                category.categoryColorIndex = 0
//                category.insert()
//
//                Thread.sleep(200)
//
//                category = Category()
//                category.categoryTitle = "Work"
//                category.categoryColorIndex = 1
//                category.insert()
//
//                Thread.sleep(200)
//
//                category = Category()
//                category.categoryTitle = "Personal"
//                category.categoryColorIndex = 2
//                category.insert()
//
//                Timber.v("category_size: $category_size")
//            }
        }

//
//        FlowManager.init(
//                FlowConfig
//                        .builder(this)
//                        .addDatabaseConfig(
//                                DatabaseConfig
//                                        .builder(AppDatabase::class.java)
//                                        .databaseName(AppDatabase.DATABASE_NAME)
//                                        .build())
//                        .build())

        ioThread {
            //            val model = Category()
//            model.categoryTitle = getString(R.string.category_default)
//            model.categoryColorIndex = 0
//            model.categoryType

//            for (i in 0..10) {
//                var entry = Entry()
////                entry.category = model
//                entry.entryTitle = "Entry Number $i"
//                entry.entryText = getString(R.string.lorem_ipsum)
//                entry.save().subscribe { t1, t2 ->
//                    Timber.d("saved... $t1, $t2")
//                }
//
//                Thread.sleep(500)
//            }
        }
    }
}