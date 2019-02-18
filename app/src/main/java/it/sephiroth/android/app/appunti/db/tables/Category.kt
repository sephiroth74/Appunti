package it.sephiroth.android.app.appunti.db.tables

import android.content.Context
import com.dbflow5.annotation.Column
import com.dbflow5.annotation.PrimaryKey
import com.dbflow5.annotation.Table
import com.dbflow5.reactivestreams.structure.BaseRXModel
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.CategoryTypeConverter
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import timber.log.Timber

@Table(database = AppDatabase::class)
class Category() : BaseRXModel() {

    constructor(title: String?, colorIndex: Int = 0, type: CategoryType = CategoryType.USER) : this() {
        categoryTitle = title
        categoryColorIndex = colorIndex
        categoryType = type
    }

    constructor(other: Category) : this() {
        categoryID = other.categoryID
        categoryTitle = other.categoryTitle
        categoryColorIndex = other.categoryColorIndex
        categoryType = other.categoryType
    }

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
            is Category ->
                return categoryID == other.categoryID
                        && categoryTitle == other.categoryTitle
                        && categoryColorIndex == other.categoryColorIndex
                        && categoryType == other.categoryType
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = categoryID
        result = 31 * result + (categoryTitle?.hashCode() ?: 0)
        result = 31 * result + categoryColorIndex
        result = 31 * result + categoryType.hashCode()
        return result
    }

    fun getColor(context: Context): Int {
        return ResourceUtils.getCategoryColors(context)[categoryColorIndex]
    }

    enum class CategoryType {
        SYSTEM, USER
    }
}