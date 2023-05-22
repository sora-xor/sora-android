/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.presentation.compose.util

import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.util.NumbersFormatter

object AmountFormat {
    fun getAssetBalanceText(asset: Asset, nf: NumbersFormatter, precision: Int) = "%s (%s)".format(
        asset.printBalance(
            nf,
            withSymbol = false,
            precision = precision,
        ),
        asset.printFiat(nf)
    )
}
