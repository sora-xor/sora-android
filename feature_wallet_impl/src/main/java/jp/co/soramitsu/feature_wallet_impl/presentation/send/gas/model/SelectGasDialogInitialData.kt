/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.model

import java.math.BigInteger

data class SelectGasDialogInitialData(
    val defaultGasLimit: BigInteger,
    val defaultGasPrice: BigInteger,
    val gasEstimationItems: List<GasEstimationItem>
)