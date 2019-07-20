package it.sephiroth.android.app.appunti.ext

import io.reactivex.disposables.Disposable
import it.sephiroth.android.app.appunti.utils.AutoDisposable

fun Disposable.addTo(autoDisposable: AutoDisposable) {
    autoDisposable.add(this)
}