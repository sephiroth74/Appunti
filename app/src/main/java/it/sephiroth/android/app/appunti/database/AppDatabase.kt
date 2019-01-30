package it.sephiroth.android.app.appunti.database

import android.content.Context
import android.graphics.Color
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.R
import timber.log.Timber
import java.util.*

@Database(entities = arrayOf(Entry::class, Category::class, Attachment::class), version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    abstract fun categoryDao(): CategoryDao

    abstract fun attachmentsDao(): AttachmentsDao

    companion object {

        private const val DATABASE_NAME = "appunti.db"
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: populate(context).also { INSTANCE = it }
                }


        private fun populate(context: Context): AppDatabase {
            return Room
                    .databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            Timber.i("onCreate")
                            super.onCreate(db)
                            Schedulers.io().scheduleDirect {
                                Timber.v("Adding initial category")

                                with(getInstance(context)) {
                                    with(categoryDao()) {
                                        add(Category(
                                                category_title = context.resources.getString(R.string.category_default),
                                                category_color_index = 0))
                                        add(Category(
                                                category_title = "Personal",
                                                category_color_index = 1))
                                        add(Category(
                                                category_title = "Work",
                                                category_color_index = 2))
                                        add(Category(
                                                category_title = "Todo",
                                                category_color_index = 5))
                                    }

                                    with(entryDao()) {
                                        Timber.v("Adding initial entries...")
                                        add(Entry("First Item", 1, 1, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Another Item", 1, 1, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Another Item 2", 1, 1, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Another Item 3", 2, 1, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Another Item 4", 2, 2, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Another Item 5", 2, 2, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Another Item 6", 3, 2, Date(), context.getString(R.string.lorem_ipsum)))
                                        Thread.sleep(500)
                                        add(Entry("Second Item", 5, 1, Date(), context.getString(R.string.lorem_ipsum)))
                                        Thread.sleep(300)
                                        add(Entry("Third Item", 8, 1, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Lorem Ipsum anche nel Titolo", 4, 2, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Fourth Item", 8, 4, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Third Item", 8, 3, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Third Item 2", 8, 3, Date(), context.getString(R.string.lorem_ipsum)))
                                        add(Entry("Third Item 3", 8, 3, Date(), context.getString(R.string.lorem_ipsum)))
                                    }
                                }
                            }
                        }
                    }).build()
        }
    }
}

