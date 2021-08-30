package jp.co.soramitsu.feature_wallet_impl.presentation.util

import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.util.NumbersFormatter

fun Asset.mapAssetToAssetModel(numbersFormatter: NumbersFormatter): AssetListItemModel {
    return AssetListItemModel(
        token.icon,
        token.name,
        numbersFormatter.formatBigDecimal(balance.transferable, AssetHolder.ROUNDING),
        token.symbol,
        position,
        token.id
    )
}
