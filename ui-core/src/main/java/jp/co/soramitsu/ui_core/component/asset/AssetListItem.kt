package jp.co.soramitsu.ui_core.component.asset

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun AssetListItem(
    modifier: Modifier = Modifier,
    icon: Uri,
    name: String,
    balance: String,
    symbol: String,
    visible: Boolean,
    favourite: Boolean,
    onFavoriteClicked: () -> Unit,
    onVisibleClicked: () -> Unit,
    onDrag: () -> Unit
) {
    AssetInfo(
        modifier.padding(start = Dimens.x1, end = Dimens.x2, top = Dimens.x2, bottom = Dimens.x2),
        icon,
        name,
        balance,
        symbol,
        leftContent = {
            val favorite = if (favourite) {
                painterResource(R.drawable.ic_favorite_enabled)
            } else {
                painterResource(R.drawable.ic_favorite_disabled)
            }

            IconButton(
                modifier = Modifier
                    .size(Dimens.IconSizeHuge)
                    .padding(end = Dimens.x1),
                onClick = onFavoriteClicked
            ) {
                Icon(
                    painter = favorite,
                    contentDescription = null,
                    tint = Color.Unspecified
                )
            }
        },
        rightContent = {
            Row(
                modifier = Modifier.padding(start = Dimens.x1_2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val visisble = if (visible) {
                    painterResource(R.drawable.ic_eye_enabled)
                } else {
                    painterResource(R.drawable.ic_eye_disabled)
                }

                IconButton(
                    modifier = Modifier.size(Dimens.IconSizeHuge),
                    onClick = onVisibleClicked
                ) {
                    Icon(
                        painter = visisble,
                        contentDescription = null,
                        tint = MaterialTheme.customColors.accentTertiary
                    )
                }

                IconButton(
                    modifier = Modifier.size(Dimens.IconSizeHuge),
                    onClick = onDrag
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_drag_and_drop),
                        contentDescription = null,
                        tint = MaterialTheme.customColors.accentTertiary
                    )
                }
            }
        }
    )
}

@Preview
@Composable
private fun PreviewAssetListItem() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.customColors.bgPage)
    ) {
        AssetListItem(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            icon = Uri.parse("file:///android_asset/ic_0x0033.png"),
            name = "SORA Synthetic USD",
            balance = "1,697.98",
            symbol = "XSTUSD",
            favourite = true,
            visible = true,
            onFavoriteClicked = {},
            onVisibleClicked = {},
            onDrag = {}
        )

        AssetListItem(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            icon = Uri.parse("file:///android_asset/ic_0x0033.png"),
            name = "SORA Synthetic USD",
            balance = "1,697.98",
            symbol = "XSTUSD",
            favourite = false,
            visible = false,
            onFavoriteClicked = {},
            onVisibleClicked = {},
            onDrag = {}
        )
    }
}
