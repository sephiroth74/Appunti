package it.sephiroth.android.app.appunti.models

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.hunter.library.debug.HunterDebugClass
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.ext.getFile
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HunterDebugClass
class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {
    val attachment = MutableLiveData<Attachment>()

    private val mediaPlayer = MutableLiveData<MediaPlayer>()

    val ready = MutableLiveData<Boolean>().apply { value = false }

    val playing = MutableLiveData<Boolean>().apply { value = false }

    val position = MutableLiveData<Pair<Int, Int>>().apply { value = Pair(0, 0) }

    private var positionDisposable: Disposable? = null

    fun loadAttachment(attachment: Attachment?) {
        if (this.attachment.value != attachment) {
            this.attachment.value = attachment
            resetMediaPlayer()

            if (null != attachment) {
                loadMedia(attachment)
            }
        }
    }

    private fun loadMedia(attachment: Attachment) {
        mediaPlayer.value = MediaPlayer().apply {
            setDataSource(attachment.getFile(getApplication()).absolutePath)
            prepareAsync()

            setOnCompletionListener {
                Timber.v("setOnCompletionListener.called")
                it.seekTo(0)
                onMediaStopped()
            }

            setOnBufferingUpdateListener { mediaPlayer, i ->
                Timber.v("setOnBufferingUpdateListener.called $i")
            }

            setOnInfoListener { mediaPlayer, i, i2 ->
                Timber.v("setOnInfoListener.called($i, $i2")
                true
            }

            setOnSeekCompleteListener {
                Timber.v("setOnSeekCompleteListener.called")
            }

            setOnErrorListener { mediaPlayer, i, i2 ->
                Timber.w("setOnErrorListener.called $i $i2")
                //onMediaUnavailable()
                positionDisposable?.dispose()
                true
            }

            setOnPreparedListener { onMediaReady() }
        }
    }

    private fun resetMediaPlayer() {
        mediaPlayer.value?.let {
            if (it.isPlaying) {
                it.pause()
                it.stop()
            }
            it.release()
            mediaPlayer.value = null
            onMediaUnavailable()
        }
    }

    fun playPause() {
        mediaPlayer.value?.let { mediaPlayer ->
            if (isReady() && mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                onMediaStopped()
            } else if (isReady() && !mediaPlayer.isPlaying) {
                mediaPlayer.start()
                onMediaResumed()
            }
        }
    }

    fun stop() {
        mediaPlayer.value?.let { mediaPlayer ->
            if (isReady()) {
                mediaPlayer.pause()
                mediaPlayer.seekTo(0)
                onMediaStopped()
            }
        }
    }

    private fun startUpdatingPosition() {
        positionDisposable?.dispose()
        positionDisposable = Observable.interval(0, 16, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                dispatchCurrentPosition()
            }
    }

    private fun onMediaStopped() {
        playing.value = false
        positionDisposable?.dispose()
        dispatchCurrentPosition()
    }

    private fun onMediaResumed() {
        playing.value = true
        startUpdatingPosition()
    }

    private fun onMediaUnavailable() {
        ready.value = false
        playing.value = false
        positionDisposable?.dispose()
    }

    private fun onMediaReady() {
        ready.value = true
        dispatchCurrentPosition()
    }

    private fun dispatchCurrentPosition() {
        mediaPlayer.value?.let { mediaPlayer ->
            Timber.v("position: ${mediaPlayer.currentPosition} of ${mediaPlayer.duration}")
            position.value = Pair(mediaPlayer.currentPosition, mediaPlayer.duration)
        } ?: run {
            position.value = Pair(0, 0)
        }
    }

    fun isReady(): Boolean = ready.value == true
}
