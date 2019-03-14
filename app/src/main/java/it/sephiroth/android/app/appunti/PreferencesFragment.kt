package it.sephiroth.android.app.appunti


import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import it.sephiroth.android.app.appunti.models.SettingsManager
import timber.log.Timber


class PreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.appunti_preferences, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        Timber.i("onPreferenceTreeClick(${preference?.key})")

        when (preference?.key) {
            SettingsManager.PREFS_KEY_DARK_THEME -> askToRestartApplication()
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
        }

        return super.onPreferenceTreeClick(preference)
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
