package it.sephiroth.android.app.appunti.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import it.sephiroth.android.app.appunti.R
import timber.log.Timber

class CollapsingLinearLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var animating = false

    private var animator = ValueAnimator.ofInt(0, 0).apply {
        addUpdateListener {
            val layoutParams = contentView?.layoutParams?.apply { height = it.animatedValue as Int }
            contentView?.layoutParams = layoutParams
        }

        doOnStart { animating = true }
        doOnEnd { animating = false }

        duration = 300
    }

    var collapsed = false
        set(value) {
            if (field != value && !animating) {
                if (value) collapse()
                else expand()
                field = value
            }
        }

    private var contentView: View? = null
    private val contentId: Int
    private var contentViewSize: Int = 0

    init {
        Timber.i("init")
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.CollapsingLinearLayout, defStyleAttr, 0)
        contentId = array.getResourceId(R.styleable.CollapsingLinearLayout_android_content, 0)

        Timber.v("content = $contentId")

        if (contentId != 0) {
            contentView = findViewById(contentId)
            Timber.v("contentView = $contentView")
        }

        array.recycle()
    }

    private fun isVertical() = orientation == LinearLayout.VERTICAL


    private fun collapse() {

        contentView?.let { contentView ->
            contentViewSize = if (isVertical()) contentView.measuredHeight else contentView.measuredWidth
            Timber.i("collapse = $contentViewSize")

            animator.setIntValues(contentViewSize, 0)
            animator.start()
        }
    }

    private fun expand() {
        Timber.i("expand")

        contentView?.let { contentView ->
            animator.setIntValues(0, contentViewSize)
            animator.start()
        }
    }

    override fun onFinishInflate() {
        Timber.i("onFinishInflate")
        super.onFinishInflate()
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)

        if (child?.id == contentId && contentView == null) {
            contentView = child
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
//        Timber.i("onSizeChanged($w, $h)")
        super.onSizeChanged(w, h, oldw, oldh)
    }
}