package it.sephiroth.android.library.kotlin_extensions.view

import android.animation.Animator
import android.view.ViewPropertyAnimator

@Suppress("unused")
class AnimatorListenerImpl(private val viewPropertyAnimator: ViewPropertyAnimator) :
    Animator.AnimatorListener {

    private var _onAnimationRepeat: ((viewPropertyAnimator: ViewPropertyAnimator, animation: Animator) -> Unit)? = null
    private var _onAnimationEnd: ((viewPropertyAnimator: ViewPropertyAnimator, animation: Animator) -> Unit)? = null
    private var _onAnimationCancel: ((viewPropertyAnimator: ViewPropertyAnimator, animation: Animator) -> Unit)? = null
    private var _onAnimationStart: ((viewPropertyAnimator: ViewPropertyAnimator, animation: Animator) -> Unit)? = null

    fun onAnimationEnd(func: (viewPropertyAnimator: ViewPropertyAnimator, animation: Animator) -> Unit) {
        _onAnimationEnd = func
    }

    fun onAnimationStart(func: (viewPropertyAnimator: ViewPropertyAnimator, animation: Animator) -> Unit) {
        _onAnimationStart = func
    }

    fun onAnimationCancel(func: (viewPropertyAnimator: ViewPropertyAnimator, animation: Animator) -> Unit) {
        _onAnimationCancel = func
    }

    fun onAnimationRepeat(func: (viewPropertyAnimator: ViewPropertyAnimator, animation: Animator) -> Unit) {
        _onAnimationRepeat = func
    }

    override fun onAnimationRepeat(animation: Animator) {
        _onAnimationRepeat?.invoke(viewPropertyAnimator, animation)
    }

    override fun onAnimationEnd(animation: Animator) {
        _onAnimationEnd?.invoke(viewPropertyAnimator, animation)
    }

    override fun onAnimationCancel(animation: Animator) {
        _onAnimationCancel?.invoke(viewPropertyAnimator, animation)
    }

    override fun onAnimationStart(animation: Animator) {
        _onAnimationStart?.invoke(viewPropertyAnimator, animation)
    }
}

inline fun ViewPropertyAnimator.setAnimationListener(func: AnimatorListenerImpl.() -> Unit): ViewPropertyAnimator {
    val listener = AnimatorListenerImpl(this)
    listener.func()
    setListener(listener)
    return this
}

