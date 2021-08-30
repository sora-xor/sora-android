/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import android.os.Parcelable
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.R
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

enum class Market(@StringRes val titleResource: Int, val backString: String) {
    SMART(R.string.polkaswap_smart, ""),
    TBC(R.string.polkaswap_tbc, "MulticollateralBondingCurvePool"),
    XYK(R.string.polkaswap_xyk, "XYKPool")
}

enum class WithDesired(val backString: String) {
    INPUT("WithDesiredInput"),
    OUTPUT("WithDesiredOutput")
}

@Parcelize
data class SwapDetails(
    val amount: BigDecimal,
    val per1: BigDecimal,
    val per2: BigDecimal,
    val minmax: BigDecimal,
    val liquidityFee: BigDecimal,
    val networkFee: BigDecimal,
) : Parcelable

data class SwapQuote(
    val amount: BigDecimal,
    val fee: BigDecimal,
)
