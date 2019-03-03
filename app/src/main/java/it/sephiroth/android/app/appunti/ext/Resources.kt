import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.annotation.AttrRes
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import it.sephiroth.android.app.appunti.R
import timber.log.Timber

fun Resources.hasSoftwareNavBar(context: Context): Boolean {
    val id = getIdentifier("config_showNavigationBar", "bool", "android")
    if (id < 0) {
        Timber.v("config_showNavigationBar = ${getBoolean(id)}")
        return getBoolean(id)
    } else {    // Check for keys
        val hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
        val hasHomeKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME)
        Timber.v("hasBackKey = ${hasBackKey}, hasHomeKey = ${hasHomeKey}")
        return ((hasBackKey && hasHomeKey))
    }
}

inline val Resources.isNavBarAtBottom: Boolean
    get() {
        // Navbar is always on the bottom of the screen in portrait mode, but may
        // rotate with device if its category is sw600dp or above.
        return this.isTablet || this.isPortrait
    }

inline val Resources.isTablet: Boolean get() = getBoolean(R.bool.is_tablet)

inline val Resources.isPortrait: Boolean get() = this.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

val Resources.statusBarHeight: Int
    @Px get() {
        val id = getIdentifier("status_bar_height", "dimen", "android")
        return when {
            id > 0 -> getDimensionPixelSize(id)
            else -> 0
        }
    }


fun Resources.Theme.getColorStateList(context: Context, @AttrRes id: Int): ColorStateList? {
    return ContextCompat.getColorStateList(context, context.theme.resolveAttribute(id))
}

fun Resources.Theme.getColor(context: Context, @AttrRes id: Int): Int {
    return ContextCompat.getColor(context, context.theme.resolveAttribute(id))
}

fun Resources.Theme.getDimensionPixelSize(context: Context, @AttrRes id: Int): Int {
    return resources.getDimensionPixelSize(context.theme.resolveAttribute(id))
}

fun Resources.Theme.resolveAttribute(@AttrRes id: Int, resolveRefs: Boolean = false): Int {
    val typedValue = TypedValue()
    resolveAttribute(id, typedValue, resolveRefs)
    return typedValue.data
}