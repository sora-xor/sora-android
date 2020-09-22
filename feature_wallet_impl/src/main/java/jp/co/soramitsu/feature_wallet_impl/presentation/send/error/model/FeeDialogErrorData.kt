/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send.error.model

data class FeeDialogErrorData(
    val minerFee: String,
    val ethBalance: String
)