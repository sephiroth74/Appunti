package it.sephiroth.android.app.appunti.db

import com.dbflow5.annotation.TypeConverter
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry


@TypeConverter
class EntryTypeConverter : com.dbflow5.converter.TypeConverter<Int, Entry.EntryType>() {

    override fun getDBValue(model: Entry.EntryType?): Int? {
        return model?.ordinal
    }

    override fun getModelValue(data: Int?): Entry.EntryType? {
        data?.let {
            return Entry.EntryType.values()[data]
        } ?: kotlin.run { return null }
    }

}

@TypeConverter
class CategoryTypeConverter : com.dbflow5.converter.TypeConverter<Int, Category.CategoryType>() {
    override fun getDBValue(model: Category.CategoryType?): Int? {
        return model?.ordinal
    }

    override fun getModelValue(data: Int?): Category.CategoryType? {
        data?.let {
            return Category.CategoryType.values()[it]
        } ?: run { return null }
    }

}