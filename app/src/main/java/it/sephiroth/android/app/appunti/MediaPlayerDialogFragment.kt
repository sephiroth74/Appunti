package it.sephiroth.android.app.appunti

import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.sellmair.disposer.Disposer
import io.sellmair.disposer.disposeBy
import io.sellmair.disposer.disposers
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.models.MediaPlayerViewModel
import it.sephiroth.android.app.appunti.utils.Constants
import kotlinx.android.synthetic.main.mediaplayer_dialog.*
import timber.log.Timber
import java.util.concurrent.TimeUnit


class MediaPlayerDialogFragment : DialogFragment() {

    private var autoPlay: Boolean = false
    private val model: MediaPlayerViewModel by lazy { ViewModelProviders.of(this).get(MediaPlayerViewModel::class.java) }

    private val onStop: Disposer by lazy { lifecycle.disposers.onStop }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        model.loadAttachment(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Appunti_MediaPlayer)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.mediaplayer_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        button_play.isEnabled = false
        button_stop.isEnabled = false

        button_play.setOnClickListener {
            if (model.isReady()) {
                model.playPause()
            }
        }

        button_stop.setOnClickListener {
            if (model.isReady()) {
                model.stop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        model.loadAttachment(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()

        model.ready.observe(this, Observer {
            button_play.isEnabled = it
            if (autoPlay && it) {
                model.playPause()
            }
        })

        model.playing.observe(this, Observer {
            button_play.drawable.level = if (it) 2 else 1
        })

        model.position.observe(this, Observer { position ->
            val total = ((position.first.toFloat() / position.second.toFloat()) * 100).toInt()
            progress.progress = total
            button_stop.isEnabled = position.first > 0
            updateTimers(position.first.toLong(), position.second.toLong())
        })
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.let { window ->
            val w = resources.getDimensionPixelSize(R.dimen.appunti_media_dialog_width)
            window.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
        }

        loadAttachment()
    }

    override fun onStop() {
        super.onStop()
    }

    private fun loadAttachment() {
        autoPlay = arguments?.getBoolean(Constants.KEY_AUTOPLAY, false) ?: false

        arguments?.getLong(Constants.KEY_ID)?.let { id ->
            DatabaseHelper.loadAttachment(id)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onComplete = {
                        Timber.w("attachment with id $id cannot be loaded!")
                        model.loadAttachment(null)
                    },
                    onError = {
                        it.printStackTrace()
                        model.loadAttachment(null)
                    },
                    onSuccess = { attachment ->
                        Timber.v("attachment $attachment loaded!")
                        model.loadAttachment(attachment)
                        media_title.text = attachment.attachmentTitle
                    }
                ).disposeBy(onStop)
        }
    }

    private fun updateTimers(current: Long, total: Long) {
        val elapsed = if (current >= 0) current else 0
        media_time_elapsed.text = String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(elapsed),
            TimeUnit.MILLISECONDS.toSeconds(elapsed) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsed))
        )

        val remaining = if (total > 0 && current >= 0) total - current else 0

        media_time_remaining.text = String.format(
            "-%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(remaining),
            TimeUnit.MILLISECONDS.toSeconds(remaining) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(remaining))
        )
    }
}