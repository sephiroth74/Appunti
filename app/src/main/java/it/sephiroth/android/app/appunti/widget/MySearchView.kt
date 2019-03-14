package com.lapism.searchview.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.*
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lapism.searchview.R
import com.lapism.searchview.Search
import com.lapism.searchview.graphics.SearchAnimator
import com.lapism.searchview.graphics.SearchArrowDrawable


@Suppress("MemberVisibilityCanBePrivate", "unused")
class MySearchView : SearchLayout, Filter.FilterListener {

    // ---------------------------------------------------------------------------------------------
    @Search.Version
    @get:Search.Version
    var version: Int = 0
        set(@Search.Version version) {
            field = version

            if (this.version == Search.Version.MENU_ITEM) {
                visibility = View.GONE
            }
        }

    private var mMenuItemCx = -1
    private var mShadow: Boolean = false
    private var mAnimationDuration: Long = 0

    private lateinit var mImageViewImage: ImageView
    private var mMenuItem: MenuItem? = null
    private var mMenuItemView: View? = null
    private lateinit var mViewShadow: View
    private lateinit var mViewDivider: View
    private lateinit var mRecyclerView: RecyclerView

    private var mOnLogoClickListener: Search.OnLogoClickListener? = null
    private var mOnOpenCloseListener: Search.OnOpenCloseListener? = null

    // Adapter
    var adapter: RecyclerView.Adapter<*>?
        get() = mRecyclerView.adapter
        set(adapter) {
            mRecyclerView.adapter = adapter
        }

    // ---------------------------------------------------------------------------------------------
    constructor(@NonNull context: Context) : super(context) {
        init(context, null, 0, 0)
    }

    constructor(@NonNull context: Context, @Nullable attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs, 0, 0)
    }

    constructor(@NonNull context: Context, @Nullable attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs, defStyleAttr, 0)
    }

    constructor(@NonNull context: Context, @Nullable attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        init(context, attrs, defStyleAttr, defStyleRes)
    }

    // ---------------------------------------------------------------------------------------------
    private fun getCenterX(@NonNull view: View): Int {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[0] + view.width / 2
    }

    // ---------------------------------------------------------------------------------------------
    override fun onTextChanged(s: CharSequence) {
        mQueryText = s

        setMicOrClearIcon(true)
        filter(s)

        if (mOnQueryTextListener != null) {
            mOnQueryTextListener.onQueryTextChange(mQueryText)
        }
    }

    override fun addFocus() {
        filter(mQueryText)

        if (mShadow) {
            SearchAnimator.fadeOpen(mViewShadow!!, mAnimationDuration)
        }

        setMicOrClearIcon(true)

        if (version == Search.Version.TOOLBAR) {
            setLogoHamburgerToLogoArrowWithAnimation(true)
            // todo SavedState, marginy kulate a barva divideru
            if (mOnOpenCloseListener != null) {
                mOnOpenCloseListener!!.onOpen()
            }
        }

        postDelayed({ showKeyboard() }, mAnimationDuration)
    }

    override fun removeFocus() {
        if (mShadow) {
            SearchAnimator.fadeClose(mViewShadow, mAnimationDuration)
        }

        //setTextImageVisibility(false); todo error + shadow error pri otoceni, pak mizi animace
        hideSuggestions()
        hideKeyboard()
        setMicOrClearIcon(false)

        if (version == Search.Version.TOOLBAR) {
            setLogoHamburgerToLogoArrowWithAnimation(false)

            postDelayed({
                if (mOnOpenCloseListener != null) {
                    mOnOpenCloseListener!!.onClose()
                }
            }, mAnimationDuration)
        }
    }

    override fun isView(): Boolean {
        return false
    }

    @SuppressLint("PrivateResource")
    override fun getLayout(): Int {
        return R.layout.search_view
    }

    override fun open() {
        open(null)
    }

    override fun close() {
        when (version) {
            Search.Version.TOOLBAR -> mSearchEditText.clearFocus()
            Search.Version.MENU_ITEM -> {
                if (mMenuItem != null) {
                    getMenuItemPosition(mMenuItem!!.itemId)
                }
//                SearchAnimator.revealClose(
//                    mContext,
//                    mCardView,
//                    mMenuItemCx,
//                    mAnimationDuration,
//                    mSearchEditText,
//                    this,
//                    mOnOpenCloseListener
//                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    @SuppressLint("PrivateResource")
    internal override fun init(
        @NonNull context: Context, @Nullable attrs: AttributeSet?, defStyleAttr: Int,
        defStyleRes: Int
    ) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SearchView, defStyleAttr, defStyleRes)
        val layoutResId = layout

        val inflater = LayoutInflater.from(context)
        inflater.inflate(layoutResId, this, true)

        super.init(context, attrs, defStyleAttr, defStyleRes)

        mImageViewImage = findViewById(R.id.search_imageView_image)
        mImageViewImage.setOnClickListener(this)

        mImageViewClear = findViewById(R.id.search_imageView_clear)
        mImageViewClear.setOnClickListener(this)
        mImageViewClear.visibility = View.GONE

        mViewShadow = findViewById(R.id.search_view_shadow)
        mViewShadow.visibility = View.GONE
        mViewShadow.setOnClickListener(this)

        mViewDivider = findViewById(R.id.search_view_divider)
        mViewDivider.visibility = View.GONE

        mRecyclerView = findViewById(R.id.search_recyclerView)
        mRecyclerView.let {recyclerView ->
            recyclerView.visibility = View.GONE
            recyclerView.layoutManager = LinearLayoutManager(mContext)
            recyclerView.isNestedScrollingEnabled = false
            recyclerView.itemAnimator = DefaultItemAnimator()
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        hideKeyboard()
                    }
                }

            })
        }

        logo = a.getInteger(R.styleable.SearchView_search_logo, Search.Logo.HAMBURGER_ARROW)
        shape = a.getInteger(R.styleable.SearchView_search_shape, Search.Shape.CLASSIC)
        theme = a.getInteger(R.styleable.SearchView_search_theme, Search.Theme.PLAY)
        versionMargins = a.getInteger(R.styleable.SearchView_search_version_margins, Search.VersionMargins.TOOLBAR)
        version = a.getInteger(R.styleable.SearchView_search_version, Search.Version.TOOLBAR)

        if (a.hasValue(R.styleable.SearchView_search_logo_icon)) {
            setLogoIcon(a.getInteger(R.styleable.SearchView_search_logo_icon, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_logo_color)) {
            setLogoColor(ContextCompat.getColor(mContext, a.getResourceId(R.styleable.SearchView_search_logo_color, 0)))
        }

        if (a.hasValue(R.styleable.SearchView_search_mic_icon)) {
            setMicIcon(a.getResourceId(R.styleable.SearchView_search_mic_icon, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_mic_color)) {
            setMicColor(a.getColor(R.styleable.SearchView_search_mic_color, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_clear_icon)) {
            setClearIcon(a.getDrawable(R.styleable.SearchView_search_clear_icon))
        } else {
            setClearIcon(ContextCompat.getDrawable(mContext, R.drawable.search_ic_clear_black_24dp))
        }

        if (a.hasValue(R.styleable.SearchView_search_clear_color)) {
            setClearColor(a.getColor(R.styleable.SearchView_search_clear_color, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_menu_icon)) {
            setMenuIcon(a.getResourceId(R.styleable.SearchView_search_menu_icon, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_menu_color)) {
            setMenuColor(a.getColor(R.styleable.SearchView_search_menu_color, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_background_color)) {
            setBackgroundColor(a.getColor(R.styleable.SearchView_search_background_color, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_text_image)) {
            setTextImage(a.getResourceId(R.styleable.SearchView_search_text_image, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_text_color)) {
            setTextColor(a.getColor(R.styleable.SearchView_search_text_color, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_text_size)) {
            setTextSize(a.getDimension(R.styleable.SearchView_search_text_size, 0f))
        }

        if (a.hasValue(R.styleable.SearchView_search_text_style)) {
            setTextStyle(a.getInt(R.styleable.SearchView_search_text_style, 0))
        }

        if (a.hasValue(R.styleable.SearchView_search_hint)) {
            setHint(a.getString(R.styleable.SearchView_search_hint))
        }

        if (a.hasValue(R.styleable.SearchView_search_hint_color)) {
            setHintColor(a.getColor(R.styleable.SearchView_search_hint_color, 0))
        }

        setAnimationDuration(
            a.getInt(
                R.styleable.SearchView_search_animation_duration,
                mContext.resources.getInteger(R.integer.search_animation_duration)
            ).toLong()
        )
        setShadow(a.getBoolean(R.styleable.SearchView_search_shadow, true))
        setShadowColor(
            a.getColor(
                R.styleable.SearchView_search_shadow_color,
                ContextCompat.getColor(mContext, R.color.search_shadow)
            )
        )

        if (a.hasValue(R.styleable.SearchView_search_elevation)) {
            elevation = a.getDimensionPixelSize(R.styleable.SearchView_search_elevation, 0).toFloat()
        }

        a.recycle()

        isSaveEnabled = true

        mSearchEditText.visibility = View.VISIBLE // todo
    }

    // ---------------------------------------------------------------------------------------------
    // Divider
    override fun setDividerColor(@ColorInt color: Int) {
        mViewDivider.setBackgroundColor(color)
    }

    // Clear
    fun setClearIcon(@DrawableRes resource: Int) {
        mImageViewClear.setImageResource(resource)
    }

    fun setClearIcon(@Nullable drawable: Drawable?) {
        mImageViewClear.setImageDrawable(drawable)
    }

    override fun setClearColor(@ColorInt color: Int) {
        mImageViewClear.setColorFilter(color)
    }

    // Image
    fun setTextImage(@DrawableRes resource: Int) {
        mImageViewImage.setImageResource(resource)
        setTextImageVisibility(false)
    }

    fun setTextImage(@Nullable drawable: Drawable) {
        mImageViewImage.setImageDrawable(drawable)
        setTextImageVisibility(false)
    }

    // Animation duration
    fun setAnimationDuration(animationDuration: Long) {
        mAnimationDuration = animationDuration
    }

    // Shadow
    fun setShadow(shadow: Boolean) {
        mShadow = shadow
    }

    fun setShadowColor(@ColorInt color: Int) {
        mViewShadow.setBackgroundColor(color)
    }

    fun addDivider(itemDecoration: RecyclerView.ItemDecoration) {
        mRecyclerView.addItemDecoration(itemDecoration)
    }

    fun removeDivider(itemDecoration: RecyclerView.ItemDecoration) {
        mRecyclerView.removeItemDecoration(itemDecoration)
    }

    // Others
    fun open(menuItem: MenuItem?) {
        mMenuItem = menuItem

        when (version) {
            Search.Version.TOOLBAR -> mSearchEditText.requestFocus()
            Search.Version.MENU_ITEM -> {
                visibility = View.VISIBLE
                if (mMenuItem != null) {
                    getMenuItemPosition(mMenuItem!!.itemId)
                }
                mCardView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        mCardView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        SearchAnimator.revealOpen(
                            mContext,
                            mCardView,
                            mMenuItemCx,
                            mAnimationDuration,
                            mSearchEditText,
                            mOnOpenCloseListener
                        )
                    }
                })
            }
        }
    }

    fun setLogoHamburgerToLogoArrowWithAnimation(animate: Boolean) {
        if (mSearchArrowDrawable != null) {
            if (animate) {
                mSearchArrowDrawable.setVerticalMirror(false)
                mSearchArrowDrawable.animate(SearchArrowDrawable.STATE_ARROW, mAnimationDuration)
            } else {
                mSearchArrowDrawable.setVerticalMirror(true)
                mSearchArrowDrawable.animate(SearchArrowDrawable.STATE_HAMBURGER, mAnimationDuration)
            }
        }
    }

    fun setLogoHamburgerToLogoArrowWithoutAnimation(animation: Boolean) {
        if (mSearchArrowDrawable != null) {
            if (animation) {
                mSearchArrowDrawable.progress = SearchArrowDrawable.STATE_ARROW
            } else {
                mSearchArrowDrawable.progress = SearchArrowDrawable.STATE_HAMBURGER
            }
        }
    }

    // Listeners
    fun setOnLogoClickListener(listener: Search.OnLogoClickListener) {
        mOnLogoClickListener = listener
    }

    fun setOnOpenCloseListener(listener: Search.OnOpenCloseListener) {
        mOnOpenCloseListener = listener
    }

    // ---------------------------------------------------------------------------------------------
    private fun setMicOrClearIcon(hasFocus: Boolean) {
        if (hasFocus && !TextUtils.isEmpty(mQueryText)) {
            if (mOnMicClickListener != null) {
                mImageViewMic.visibility = View.GONE
            }
            mImageViewClear.visibility = View.VISIBLE
        } else {
            mImageViewClear.visibility = View.GONE
            if (mOnMicClickListener != null) {
                mImageViewMic.visibility = View.VISIBLE
            }
        }
    }

    private fun setTextImageVisibility(hasFocus: Boolean) {
        if (hasFocus) {
            mImageViewImage.visibility = View.GONE
            mSearchEditText.visibility = View.VISIBLE
            mSearchEditText.requestFocus()
        } else {
            mSearchEditText.visibility = View.GONE
            mImageViewImage.visibility = View.VISIBLE
        }
    }

    private fun filter(s: CharSequence?) {
        if (adapter != null && adapter is Filterable) {
            (adapter as Filterable).filter.filter(s, this)
        }
    }

    private fun showSuggestions() {
        if (adapter != null && adapter!!.getItemCount() > 0) {
            mViewDivider.visibility = View.VISIBLE
            mRecyclerView.setVisibility(View.VISIBLE)
        }
    }

    private fun hideSuggestions() {
        if (adapter != null && adapter!!.getItemCount() > 0) {
            mViewDivider!!.visibility = View.GONE
            mRecyclerView.setVisibility(View.GONE)
        }
    }

    private fun getMenuItemPosition(menuItemId: Int) {
        if (mMenuItemView != null) {
            mMenuItemCx = getCenterX(mMenuItemView!!)
        }
        var viewParent: ViewParent? = parent
        while (viewParent != null && viewParent is View) {
            val parent = viewParent as View?
            val view = parent!!.findViewById<View>(menuItemId)
            if (view != null) {
                mMenuItemView = view
                mMenuItemCx = getCenterX(mMenuItemView!!)
                break
            }
            viewParent = viewParent.getParent()
        }
    }

    // ---------------------------------------------------------------------------------------------
//    override fun onSaveInstanceState(): Parcelable? {
//        val superState = super.onSaveInstanceState()
//        val ss = SearchViewSavedState(superState)
//        ss.hasFocus = mSearchEditText.hasFocus()
//        ss.shadow = mShadow
//        ss.query = if (mQueryText != null) mQueryText!!.toString() else null
//        return ss
//    }
//
//    override fun onRestoreInstanceState(state: Parcelable) {
//        if (state !is SearchViewSavedState) {
//            super.onRestoreInstanceState(state)
//            return
//        }
//        super.onRestoreInstanceState(state.superState)
//
//        mShadow = state.shadow
//        if (mShadow) {
////            mViewShadow!!.visibility = View.VISIBLE
//        }
////        if (state.hasFocus) {
////            open()
////        }
////        if (state.query != null) {
////            setText(state.query)
////        }
////        requestLayout()
//    }

    // ---------------------------------------------------------------------------------------------
    override fun onClick(v: View) {
        if (v == mImageViewLogo) {
            if (mSearchEditText.hasFocus()) {
                close()
            } else {
                if (mOnLogoClickListener != null) {
                    mOnLogoClickListener!!.onLogoClick()
                }
            }
        } else if (v == mImageViewImage) {
            setTextImageVisibility(true)
        } else if (v == mImageViewMic) {
            if (mOnMicClickListener != null) {
                mOnMicClickListener.onMicClick()
            }
        } else if (v == mImageViewClear) {
            if (mSearchEditText.length() > 0) {
                mSearchEditText.text!!.clear()
            }
        } else if (v == mImageViewMenu) {
            if (mOnMenuClickListener != null) {
                mOnMenuClickListener.onMenuClick()
            }
        } else if (v == mViewShadow) {
            close()
        }
    }

    override fun onFilterComplete(count: Int) {
        if (count > 0) {
            showSuggestions()
        } else {
            hideSuggestions()
        }
    }

}
