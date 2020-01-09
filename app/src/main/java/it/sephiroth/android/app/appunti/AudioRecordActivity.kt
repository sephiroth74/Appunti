package it.sephiroth.android.app.appunti

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.crashlytics.android.answers.CustomEvent
import com.hunter.library.debug.HunterDebug
import it.sephiroth.android.app.appunti.utils.Constants.ActivityRequestCodes.AUDIO_CAPTURE_REQUEST_CODE
import it.sephiroth.android.app.appunti.utils.Constants.ActivityRequestCodes.REQUEST_RECORD_AUDIO_PERMISSION_CODE
import it.sephiroth.android.app.appunti.utils.IntentUtils
import timber.log.Timber

abstract class AudioRecordActivity(wantsFullscreen: Boolean = false) : AppuntiActivity(wantsFullscreen) {

    @HunterDebug
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                answers.logCustom(CustomEvent("recordAudio.permissions.geanted"))
                onAudioPermissionsGranted()
            } else {
                answers.logCustom(CustomEvent("recordAudio.permissions.denied"))
                onAudioPermissionsDenied(false)
            }
        }
    }

    @HunterDebug
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            AUDIO_CAPTURE_REQUEST_CODE -> {
                data?.let { onAudioCaptured(it) }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @HunterDebug
    protected open fun onAudioCaptured(data: Intent) {
        val bundle = data.extras
        var result: String? = null
        var audioUri: Uri? = null
        if (null != bundle) {
            audioUri = data.data
            if (bundle.containsKey(RecognizerIntent.EXTRA_RESULTS)) {
                val results = bundle.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)
                if (null != results && results.isNotEmpty()) {
                    result = results.first()
                }
            }

            if (result.isNullOrEmpty()) {
                if (bundle.containsKey("query")) {
                    result = bundle.getString("query")
                }
            }
        }
        onAudioCaptured(audioUri, result)
    }

    abstract fun onAudioCaptured(audioUri: Uri?, result: String?)

    @HunterDebug
    protected fun dispatchVoiceRecordingIntent() {
        answers.logCustom(CustomEvent("recordAudio.initialize"))
        val intent = IntentUtils.createVoiceRecordingIntent()
        startActivityForResult(intent, AUDIO_CAPTURE_REQUEST_CODE)
    }

    @HunterDebug
    protected fun askForAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                Timber.e("permission denied forever")
                onAudioPermissionsDenied(true)
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION_CODE)
            }
        } else {
            Timber.v("permission granted!")
            onAudioPermissionsGranted()
        }
    }

    abstract fun onAudioPermissionsGranted()

    abstract fun onAudioPermissionsDenied(shouldShowRequestPermissionRationale: Boolean)

}