package it.sephiroth.android.app.appunti


import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.CustomEvent
import it.sephiroth.android.app.appunti.models.SettingsManager
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*


@Suppress("NAME_SHADOWING")
class PreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.appunti_preferences, rootKey)

        // updateClickOnLinks version screen
        val prefVersion = preferenceScreen.findPreference("preference.version")
        prefVersion?.let {
            val dateString = SimpleDateFormat.getDateTimeInstance().format(Date(BuildConfig.TIMESTAMP))
            it.title = "Version: ${BuildConfig.VERSION_NAME} (Code: ${BuildConfig.VERSION_CODE})"
            it.summary = "$dateString (hash: ${BuildConfig.COMMIT_HASH})"
            it.isEnabled = false
        }

        updateClickOnLinks()
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        Timber.i("onPreferenceTreeClick(${preference?.key})")

        when (preference?.key) {
            SettingsManager.PREFS_KEY_DARK_THEME -> {
                Answers.getInstance().logCustom(CustomEvent("settings.themeChange"))
                askToRestartApplication()
            }

            "preference.feedback" -> {
                val intent = Intent(ACTION_SENDTO).apply {
                    type = "text/plain"
                    data = Uri.parse("mailto:alessandro.crugnola@gmail.com")
                    putExtra(EXTRA_EMAIL, arrayOf("alessandro.crugnola@gmail.com"))
                    putExtra(EXTRA_SUBJECT, getString(R.string.feedback_from_app_subject))
                }

                if (intent.resolveActivity(activity?.packageManager!!) != null) {
                    startActivity(intent)
                } else {
                    Toast
                        .makeText(context, getString(R.string.unable_to_find_app_to_handle_request), Toast.LENGTH_SHORT)
                        .show()
                }

            }

            "preference.tmp.clickOnLinks" -> {
                AlertDialog
                    .Builder(context!!)
                    .setItems(
                        arrayOf(
                            getString(R.string.open_link),
                            getString(R.string.keep_editing),
                            getString(R.string.always_ask)
                        )
                    ) { _, which ->

                        Answers.getInstance().logCustom(
                            CustomEvent("settings.openLinksOnClick")
                                .putCustomAttribute("value", which)
                        )

                        SettingsManager.getInstance(context!!).openLinksOnClick = when (which) {
                            0 -> true
                            1 -> false
                            else -> null
                        }
                        updateClickOnLinks()
                    }
                    .create()
                    .show()
            }

            "preference.attribution" -> {
                startActivity(Intent(context, AttributionActivity::class.java))
            }
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun updateClickOnLinks() {
        val prefClickOnLinks = preferenceScreen.findPreference("preference.tmp.clickOnLinks")
        prefClickOnLinks?.let { prefClickOnLinks ->
            val value = SettingsManager.getInstance(context!!).openLinksOnClick
            value?.let { value ->
                prefClickOnLinks.summary =
                    if (value) getString(R.string.open_link) else getString(R.string.keep_editing)
            } ?: run {
                prefClickOnLinks.summary = getString(R.string.always_ask)
            }
        }
    }

    private fun askToRestartApplication() {
        activity?.let { activity ->
            AlertDialog.Builder(activity)
                .setTitle(getString(R.string.restart_required))
                .setMessage(getString(R.string.restart_required_body))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.restart)) { _, _ -> triggerRebirth() }
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun triggerRebirth() {
        activity?.let { activity ->
            val intent = Intent(activity, MainActivity::class.java)
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            activity.finish()
        }
        Runtime.getRuntime().exit(0)
    }
}
