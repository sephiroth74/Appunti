package it.sephiroth.android.app.appunti.db.tables

import com.dbflow5.annotation.*
import com.dbflow5.reactivestreams.structure.BaseRXModel
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.CategoryTypeConverter

@Table(database = AppDatabase::class)
class Category : BaseRXModel() {

    @PrimaryKey(autoincrement = true)
    var categoryID: Int = 0

    @Column(defaultValue = "")
    @Unique(unique = true, onUniqueConflict = ConflictAction.ROLLBACK)
    var categoryTitle: String? = null

    var categoryColorIndex: Int = 0

    @Column(typeConverter = CategoryTypeConverter::class)
    var categoryType: CategoryType = CategoryType.SYSTEM

    override fun toString(): String {
        return "Category(uid=$categoryID, title=$categoryTitle, color=$categoryColorIndex)"
    }

    enum class CategoryType {
        SYSTEM, USER
    }
}