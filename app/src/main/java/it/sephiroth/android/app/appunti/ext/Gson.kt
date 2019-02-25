package it.sephiroth.android.app.appunti.ext

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

inline fun <reified T> Gson.fromJson(text: String): T {
    val listType = object : TypeToken<T>() {}.type
    return fromJson<T>(text, listType)
}