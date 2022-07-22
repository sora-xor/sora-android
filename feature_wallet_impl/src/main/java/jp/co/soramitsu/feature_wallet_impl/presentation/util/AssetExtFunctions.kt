/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.util

import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.util.NumbersFormatter

fun Asset.mapAssetToAssetModel(
    numbersFormatter: NumbersFormatter,
    style: AssetBalanceStyle
): AssetListItemModel {
    return AssetListItemModel(
        token.icon,
        token.name,
        AssetBalanceData(
            amount = numbersFormatter.formatBigDecimal(balance.transferable, AssetHolder.ROUNDING),
            style = style,
        ),
        token.symbol,
        position,
        token.id
    )
}
