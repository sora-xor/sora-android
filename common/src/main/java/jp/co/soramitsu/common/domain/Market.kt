/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import androidx.annotation.StringRes
import jp.co.soramitsu.common.R

enum class Market(
    @StringRes val titleResource: Int,
    @StringRes val descriptionResource: Int,
    val backString: String
) {
    SMART(R.string.polkaswap_smart, R.string.polkaswap_market_smart_description, ""),
    TBC(
        R.string.polkaswap_tbc,
        R.string.polkaswap_market_tbc_description,
        "MulticollateralBondingCurvePool"
    ),
    XYK(R.string.polkaswap_xyk, R.string.polkaswap_market_xyk_description, "XYKPool")
}
