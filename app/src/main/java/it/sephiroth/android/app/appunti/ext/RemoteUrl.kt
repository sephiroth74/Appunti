package it.sephiroth.android.app.appunti.ext

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import com.crashlytics.android.Crashlytics
import com.squareup.picasso.Callback
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.RemoteUrl
import it.sephiroth.android.app.appunti.utils.PicassoUtils
import timber.log.Timber

fun RemoteUrl.loadThumbnail(context: Context, view: ImageView) {
    Timber.i("loadThumbnail($remoteThumbnailUrl)")

    if (remoteThumbnailUrl == null) {
        view.setImageResource(R.drawable.remote_url_fallback_small)
        return
    }

    PicassoUtils
        .get(context)
        .load(this.remoteThumbnailUrl)
        .resizeDimen(
            R.dimen.appunti_detail_attachment_thumbnail_size,
            R.dimen.appunti_detail_attachment_thumbnail_size
        )
        .into(view, object : Callback {
            override fun onError(e: Exception?) {
                e?.printStackTrace()
                Crashlytics.logException(e)
                view.setImageResource(R.drawable.remote_url_fallback_small)
            }

            override fun onSuccess() {
                Timber.v("success=${(view.drawable as BitmapDrawable).bounds}")
            }
        })
}