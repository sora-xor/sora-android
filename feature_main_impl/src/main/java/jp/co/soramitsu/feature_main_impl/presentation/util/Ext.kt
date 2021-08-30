package jp.co.soramitsu.feature_main_impl.presentation.util

import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator

private typealias LoadImageExtras = RequestCreator.() -> Unit

/**
 * keep in mind that if [url] is empty you can use [extraConfig] to set placeholder in ImageView
 *
 * @param url url to be loaded
 * @param extraConfig [RequestCreator] that can be used to configure picasso
 */
fun ImageView.loadImage(url: String, extraConfig: LoadImageExtras? = null) {
    val requestCreator = Picasso.get()
        .load(if (url.isEmpty()) null else url)
        .fit()
        .centerCrop()

    extraConfig?.invoke(requestCreator)

    requestCreator.into(this)
}
