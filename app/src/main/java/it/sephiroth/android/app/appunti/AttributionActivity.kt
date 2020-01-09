package it.sephiroth.android.app.appunti

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import kotlinx.android.synthetic.main.activity_attribution.*
import kotlinx.android.synthetic.main.content_attribution.*
import me.saket.bettermovementmethod.BetterLinkMovementMethod

class AttributionActivity : AppuntiActivity() {

    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.activity_attribution

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        textView.text = HtmlCompat.fromHtml(getString(R.string.attribution_html), HtmlCompat.FROM_HTML_MODE_COMPACT)
        BetterLinkMovementMethod.linkifyHtml(textView)
    }

}
