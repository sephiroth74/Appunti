package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.snackbar.Snackbar
import it.sephiroth.android.app.appunti.R
import timber.log.Timber


class DetailBottomAppBar @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), CoordinatorLayout.AttachedBehavior {

    var hideOnScroll = false

    init {
        orientation = LinearLayout.HORIZONTAL
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
    }

    private fun findDependent(): View? {
        if (this.parent !is CoordinatorLayout) {
            Timber.w("parent is not a CoordinatorLayout")
            return null
        } else {
            val dependents = (this.parent as CoordinatorLayout).getDependents(this)
//            Timber.v("dependents: $dependents")
            val var2 = dependents.iterator()

            while (var2.hasNext()) {
//                Timber.v("item: $var2")
                val v: View = var2.next()
                if ((v.layoutParams as CoordinatorLayout.LayoutParams).anchorId == id) return v
                if (v is FrameLayout) return v
                if (v is Snackbar.SnackbarLayout) return v
            }

            return null
        }
    }

    override fun getBehavior(): CoordinatorLayout.Behavior<DetailBottomAppBar> {
        return DetailBottomAppBar.Behavior(context)
    }

    class Behavior(context: Context) : HideBottomViewOnScrollBehavior<DetailBottomAppBar>() {

        private var shadowHeight: Int = 0
        private val snackBarBottomMargin: Int

        init {
            shadowHeight = context.resources.getDimensionPixelSize(R.dimen.appunti_bottomappbar_shadow_height)
            snackBarBottomMargin = context.resources.getDimensionPixelSize(R.dimen.appunti_snackbar_appbar_bottom_margin)
        }

        override fun onLayoutChild(parent: CoordinatorLayout, child: DetailBottomAppBar, layoutDirection: Int): Boolean {
//            Timber.i("onLayoutChild(layoutDirection=$layoutDirection)")

            val view = child.findDependent()
            view?.let { view ->
                val params = view.layoutParams as CoordinatorLayout.LayoutParams
//                Timber.d("found dependent:$view,  child.height=${child.height}, bottomMargin=${params.bottomMargin}")
                params.bottomMargin = child.measuredHeight - shadowHeight
                view.layoutParams = params
            }

            parent.onLayoutChild(child, layoutDirection)
            return super.onLayoutChild(parent, child, layoutDirection)
        }

        override fun layoutDependsOn(parent: CoordinatorLayout, child: DetailBottomAppBar, dependency: View): Boolean {
            if (dependency is Snackbar.SnackbarLayout) return true
            return super.layoutDependsOn(parent, child, dependency)
        }

        override fun onDependentViewChanged(parent: CoordinatorLayout, child: DetailBottomAppBar, dependency: View): Boolean {
//            Timber.i("onDependentViewChanged($dependency)")

            if (dependency is Snackbar.SnackbarLayout) {
                val params = dependency.layoutParams as CoordinatorLayout.LayoutParams
                params.bottomMargin = child.measuredHeight - shadowHeight + snackBarBottomMargin
                dependency.layoutParams = params
                return true
            }

            return super.onDependentViewChanged(parent, child, dependency)
        }

        override fun onDependentViewRemoved(parent: CoordinatorLayout, child: DetailBottomAppBar, dependency: View) {
//            Timber.i("onDependentViewRemoved($dependency)")
            super.onDependentViewRemoved(parent, child, dependency)
        }

        override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: DetailBottomAppBar, directTargetChild: View, target: View, axes: Int, type: Int): Boolean {
            return child.hideOnScroll && super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type)
        }

        override fun slideUp(child: DetailBottomAppBar) {
//            Timber.i("slideUp")
            super.slideUp(child)
        }

        override fun slideDown(child: DetailBottomAppBar) {
//            Timber.i("slideDown")
            super.slideDown(child)
        }
    }
}