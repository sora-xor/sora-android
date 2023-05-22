/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.buycrypto

data class BuyCryptoState(
    val loading: Boolean = true,
    val showAlert: Boolean = false,
    val script: String = ""
)
