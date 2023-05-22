/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoriteAssetsCardState
import jp.co.soramitsu.ui_core.component.asset.Asset
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun AssetsCard(
    cardState: FavoriteAssetsCardState,
    onClick: (String) -> Unit,
) {
    cardState.assets.forEachIndexed { index, assetState ->
        Asset(
            modifier = Modifier.padding(horizontal = Dimens.x3),
            icon = assetState.tokenIcon,
            name = assetState.tokenName,
            balance = assetState.assetAmount,
            symbol = "",
            fiat = assetState.assetFiatAmount,
            change = assetState.fiatChange,
            onClick = { onClick.invoke(assetState.tokenId) },
        )
        if (index < cardState.assets.lastIndex) {
            Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x2))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewAssetsCard() {
    SoraAppTheme {
        Column {
            AssetsCard(
                cardState = FavoriteAssetsCardState(
                    assets = listOf(
                        AssetItemCardState(
                            tokenName = "qwe",
                            tokenId = "id 01",
                            tokenIcon = DEFAULT_ICON_URI,
                            assetAmount = "13.3",
                            tokenSymbol = "XOR",
                            assetFiatAmount = "$45.9",
                            fiatChange = "+34%"
                        ),
                        AssetItemCardState(
                            tokenName = "qwe",
                            tokenId = "id 02",
                            tokenIcon = DEFAULT_ICON_URI,
                            assetAmount = "13.3",
                            tokenSymbol = "XOR",
                            assetFiatAmount = "$45.9",
                            fiatChange = "+34%"
                        ),
                        AssetItemCardState(
                            tokenName = "qwe",
                            tokenId = "id 03",
                            tokenIcon = DEFAULT_ICON_URI,
                            assetAmount = "13.3",
                            tokenSymbol = "XOR",
                            assetFiatAmount = "$45.9",
                            fiatChange = "+34%"
                        ),
                    )
                ),
            ) { }
        }
    }
}
