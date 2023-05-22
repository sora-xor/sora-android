/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import android.net.Uri
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus

val emptyTxDetailsState: TxDetailsScreenState = TxDetailsScreenState(
    basicTxDetailsState = BasicTxDetailsState(
        "",
        "",
        "",
        emptyList(),
        TransactionStatus.COMMITTED,
        "",
        "",
        "",
        R.drawable.ic_empty_state,
        ""
    ),
    "",
    "",
    "",
    DEFAULT_ICON_URI,
    DEFAULT_ICON_URI,
    true,
    txType = TxType.LIQUIDITY
)

data class TxDetailsScreenState(
    val basicTxDetailsState: BasicTxDetailsState,
    val amount1: String,
    val amount2: String? = null,
    val amountFiat: String,
    val icon1: Uri,
    val icon2: Uri? = null,
    val isAmountGreen: Boolean = false,
    val txType: TxType
)

enum class TxType {
    LIQUIDITY,
    REFERRAL_TRANSFER,
    SWAP
}
