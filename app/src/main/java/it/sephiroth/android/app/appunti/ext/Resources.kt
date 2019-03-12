import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.annotation.AttrRes
import androidx.annotation.Px
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.library.kotlin_extensions.content.res.getTypedValue
import it.sephiroth.android.library.kotlin_extensions.content.res.isPortrait
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


val Resources.statusBarHeight: Int
    @Px get() {
        val id = getIdentifier("status_bar_height", "dimen", "android")
        return when {
            id > 0 -> getDimensionPixelSize(id)
            else -> 0
        }
    }


fun Resources.Theme.getFloat(context: Context, @AttrRes id: Int): Float? {
    val typedValue = getTypedValue(id, true)
    if (typedValue.type == TypedValue.TYPE_FLOAT) {
        return typedValue.float
    }
    return null
}

