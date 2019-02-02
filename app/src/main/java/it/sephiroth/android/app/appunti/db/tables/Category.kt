package it.sephiroth.android.app.appunti.db.tables

import com.dbflow5.annotation.Column
import com.dbflow5.annotation.PrimaryKey
import com.dbflow5.annotation.Table
import com.dbflow5.reactivestreams.structure.BaseRXModel
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.CategoryTypeConverter
import timber.log.Timber

@Table(database = AppDatabase::class)
class Category : BaseRXModel() {

    @PrimaryKey(autoincrement = true)
    var categoryID: Int = 0

    @Column(defaultValue = "")
    var categoryTitle: String? = null

    @Column(defaultValue = "0")
    var categoryColorIndex: Int = 0

    @Column(typeConverter = CategoryTypeConverter::class)
    var categoryType: CategoryType = CategoryType.USER

    override fun toString(): String {
        return "Category(id=$categoryID, title=$categoryTitle, color=$categoryColorIndex)"
    }

    override fun equals(other: Any?): Boolean {
        when (other) {
            is Category -> return categoryID == other.categoryID
                    && categoryTitle == other.categoryTitle
                    && categoryColorIndex == other.categoryColorIndex
                    && categoryType == other.categoryType
        }
        return super.equals(other)
    }

    enum class CategoryType {
        SYSTEM, USER
    }
}