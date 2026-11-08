package jp.co.soramitsu.ui_core.component.icons

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.ui_core.R

@Composable
fun TokenIcon(
    modifier: Modifier = Modifier,
    uri: String?,
    size: Dp,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(uri ?: R.drawable.ic_token_default).build(),
        modifier = modifier
            .size(size = size),
        contentDescription = null,
        imageLoader = LocalContext.current.imageLoader,
    )
}

@Composable
fun AccountIcon(
    modifier: Modifier = Modifier,
    drawable: Drawable?,
    size: Dp,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(drawable ?: R.drawable.ic_token_default).build(),
        modifier = modifier
            .size(size = size),
        contentDescription = null,
        imageLoader = LocalContext.current.imageLoader,
    )
}
