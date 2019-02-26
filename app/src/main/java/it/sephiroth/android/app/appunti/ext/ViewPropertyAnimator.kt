package it.sephiroth.android.app.appunti.ext

import android.animation.Animator
import android.view.ViewPropertyAnimator

inline fun ViewPropertyAnimator.addListener(
    crossinline onAnimationStart: (property: ViewPropertyAnimator, animation: Animator) -> Unit = { _, _ -> },
    crossinline onAnimationEnd: (property: ViewPropertyAnimator, animation: Animator) -> Unit = { _, _ -> },
    crossinline onAnimationCancel: (property: ViewPropertyAnimator, animation: Animator) -> Unit = { _, _ -> },
    crossinline onAnimationRepeat: (property: ViewPropertyAnimator, animation: Animator) -> Unit = { _, _ -> }

): ViewPropertyAnimator {
    val listener = object : Animator.AnimatorListener {
        override fun onAnimationRepeat(animation: Animator) {
            onAnimationRepeat(this@addListener, animation)
        }

        override fun onAnimationEnd(animation: Animator) {
            onAnimationEnd(this@addListener, animation)
        }

        override fun onAnimationCancel(animation: Animator) {
            onAnimationCancel(this@addListener, animation)
        }

        override fun onAnimationStart(animation: Animator) {
            onAnimationStart(this@addListener, animation)
        }
    }
    setListener(listener)
    return this

}
