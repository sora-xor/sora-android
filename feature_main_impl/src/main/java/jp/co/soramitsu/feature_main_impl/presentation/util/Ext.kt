package jp.co.soramitsu.feature_main_impl.presentation.util

import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator

private typealias LoadImageExtras = RequestCreator.() -> Unit

fun ImageView.loadImage(url: String, extraConfig: LoadImageExtras? = null) {
    val requestCreator = Picasso.get()
        .load(url)
        .fit()
        .centerCrop()

    extraConfig?.invoke(requestCreator)

    requestCreator.into(this)
}