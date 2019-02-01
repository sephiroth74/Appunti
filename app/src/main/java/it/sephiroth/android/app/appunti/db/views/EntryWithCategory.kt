package it.sephiroth.android.app.appunti.db.views

import com.dbflow5.annotation.ModelView
import com.dbflow5.annotation.ModelViewQuery
import com.dbflow5.query.select
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Category_Table
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table

//@ModelView(database = AppDatabase::class)
class EntryWithCategory {

//    @ModelViewQuery
//    fun query() = select().from(Entry::class).leftOuterJoin(Category::class).on(Entry_Table.category_categoryID.withTable().eq(Category_Table.categoryID.withTable()))
}