package it.sephiroth.android.app.appunti.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.ChangeAction
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.DetailActivity
import it.sephiroth.android.app.appunti.MainActivity
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Category
import timber.log.Timber

class ShortcutUtils private constructor(private val context: Context) {

    val categoriesModelListener = object : DirectModelNotifier.ModelChangedListener<Category> {
        override fun onModelChanged(model: Category, action: ChangeAction) {
            Timber.i("onModelChanged($action)")
            updateShortcuts()
        }

        override fun onTableChanged(table: Class<*>?, action: ChangeAction) {
        }
    }

    init {
        DirectModelNotifier.get().registerForModelChanges(Category::class.java, categoriesModelListener)
    }

    fun updateShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            updateShortcutsApi25()
        }
    }

    @SuppressLint("CheckResult")
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun updateShortcutsApi25() {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)

        val shortcut = ShortcutInfo.Builder(context, "id1")
            .setShortLabel("New Note")
            .setLongLabel("Create a new note")
            .setIcon(Icon.createWithResource(context, R.drawable.shortcut_sharp_add_24))
            .setIntent(Intent(context, DetailActivity::class.java).apply {
                action = Intent.ACTION_CREATE_DOCUMENT
            }).build()

        val shortcuts = mutableListOf(shortcut)

        DatabaseHelper.getCategories()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { result, error ->
                result?.let { result ->
                    for (category in result) {
                        val shortcut = ShortcutInfo.Builder(context, "category${category.categoryID}")
                            .setShortLabel(category.categoryTitle ?: "noname")
                            .setIcon(
                                Icon.createWithResource(context, R.drawable.shortcut_outline_label_24)
                            )
                            .setIntent(Intent(context, MainActivity::class.java).apply {
                                action = "category"
                                putExtra("categoryID", category.categoryID)
                            }).build()
                        shortcuts.add(shortcut)
                    }
                }
                shortcutManager?.dynamicShortcuts = shortcuts
            }

    }

    companion object : SingletonHolder<ShortcutUtils, Context>(::ShortcutUtils)
}