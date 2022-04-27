/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist

import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal

object AssetListTestData {

    val FIRST_TOKEN = Token("token_id", "token name", "token symbol", 18, true, 0)
    val SECOND_TOKEN = Token("token2_id", "token2 name", "token2 symbol", 18, true, 0)

    private val ASSET_BALANCE = AssetBalance(
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE
    )

    private val FIRST_ASSET = Asset(FIRST_TOKEN, isDisplaying = true, position = 0, ASSET_BALANCE)
    val SECOND_ASSET = Asset(SECOND_TOKEN, isDisplaying = true, position = 1, ASSET_BALANCE)

    val ASSET_LIST = listOf(FIRST_ASSET, SECOND_ASSET)

    val SECOND_ASSET_LIST_ITEM_MODEL = AssetListItemModel(
        0,
        "title",
        AssetBalanceData(
            amount = "1",
            style = AssetBalanceStyle(
                R.style.TextAppearance_Soramitsu_Neu_Bold_15,
                R.style.TextAppearance_Soramitsu_Neu_Bold_11
            )
        ),
        "sora",
        1,
        SECOND_TOKEN.id
    )
}
