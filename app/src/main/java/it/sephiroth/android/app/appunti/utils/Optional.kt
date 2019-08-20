package it.sephiroth.android.app.appunti.utils

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

class Optional<T>(var value: T?) {

    constructor() : this(null)

    fun isPresent() = value != null
    fun get() = value!!

    fun doIfPresent(body: (T) -> Unit) {
        value?.let {
            body(it)
        }
    }

    fun orElse(other: T): T {
        return value?.let { it } ?: run { other }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun orElseGet(other: Supplier<out T>): T {
        return value?.let { it } ?: run { other.get() }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun filter(predicate: Predicate<in T>): Optional<T> {

        return if (!isPresent())
            this
        else
            if (predicate.test(value!!)) this else empty()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun <U> map(mapper: Function<in T, out U>): Optional<U> {
        return if (!isPresent())
            empty()
        else {
            Optional.of<U>(mapper.apply(value!!))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is Optional<*>) {
            return false
        }

        val other2 = other as Optional<*>?
        return value == other2!!.value
    }

    override fun hashCode(): Int {
        return Objects.hashCode(value)
    }

    override fun toString(): String {
        return if (value != null)
            String.format("Optional[%s]", value)
        else
            "Optional.empty"
    }

    companion object {
        val EMPTY: Optional<Any> = Optional()

        fun <T> empty(): Optional<T> {
            return EMPTY as Optional<T>
        }

        fun <T> of(value: T?): Optional<T> {
            return Optional(value)
        }
    }
}