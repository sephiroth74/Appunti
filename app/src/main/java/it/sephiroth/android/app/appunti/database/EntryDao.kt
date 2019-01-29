package it.sephiroth.android.app.appunti.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.reactivex.Flowable

@Dao
interface EntryDao {
    @Query("SELECT entries.*, categories.* FROM entries " +
            "INNER JOIN categories ON entries.entry_category_uid = categories.category_uid ORDER BY entries.entry_date DESC")
    fun all(): LiveData<List<EntryWithCategory>>

    @Query("SELECT entries.*, categories.* FROM entries " +
            "INNER JOIN categories ON entries.entry_category_uid = categories.category_uid WHERE categories.category_title = " +
            ":categoryName" +
            " ORDER " +
            "BY entries.entry_date DESC")
    fun allByCategory(categoryName: String): LiveData<List<EntryWithCategory>>


    @Insert
    fun add(entry: Entry)
}

@Dao
interface CategoryDao {
    @Query("SELECT * from categories")
    fun getAll(): LiveData<List<Category>>

    @Insert
    fun add(category: Category)
}

@Dao
interface AttachmentsDao {

}