package it.sephiroth.android.app.appunti.db

import com.dbflow5.annotation.TypeConverter
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import org.threeten.bp.Instant


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

@TypeConverter
class InstantTypeConverter : com.dbflow5.converter.TypeConverter<Long, Instant>() {
    override fun getDBValue(model: Instant?): Long? {
        return model?.toEpochMilli()
    }

    override fun getModelValue(data: Long?): Instant? {
        return data?.let { Instant.ofEpochMilli(it) }
    }

}