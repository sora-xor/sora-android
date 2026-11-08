package jp.co.soramitsu.ui_core.component.asset

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AssetInfo(
    modifier: Modifier = Modifier,
    icon: Uri,
    name: String,
    balance: String,
    symbol: String,
    onClick: (() -> Unit)? = null,
    leftContent: @Composable (() -> Unit)? = null,
    rightContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }.then(
                modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leftContent?.invoke()

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(icon)
                .build(),
            imageLoader = LocalContext.current.imageLoader,
            modifier = Modifier.size(Dimens.AssetIconSize),
            contentDescription = null
        )

        Column(
            modifier = Modifier
                .padding(horizontal = Dimens.x1)
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "$balance $symbol",
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        rightContent?.invoke()
    }
}
