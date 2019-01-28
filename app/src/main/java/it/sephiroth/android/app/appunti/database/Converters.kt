package it.sephiroth.android.app.appunti.database

import androidx.room.TypeConverter
import java.util.*


object Converters {

    @TypeConverter
    @JvmStatic
    fun fromTimestamp(value: Long): Date {
        return Date(value)
    }

    @TypeConverter
    @JvmStatic
    fun dateToTimestamp(date: Date): Long {
        return date.getTime().toLong()
    }
}

object EntryTypeConverter {

    @TypeConverter
    @JvmStatic
    fun toEntryType(value: Int): Entry.EntryType = Entry.EntryType.values()[value]


    @TypeConverter
    @JvmStatic
    fun fromEntryType(value: Entry.EntryType) = value.ordinal
}

object CategoryTypeConverter {

    @TypeConverter
    @JvmStatic
    fun toCategoryType(value: Int): Category.CategoryType = Category.CategoryType.values()[value]


    @TypeConverter
    @JvmStatic
    fun fromCategoryType(value: Category.CategoryType) = value.ordinal
}