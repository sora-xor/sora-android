/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.util

import javax.inject.Inject

class EthereumAddressValidator @Inject constructor() {

    private val regex = Regex("0x[a-fA-F0-9]{40}")

    fun isAddressValid(address: String): Boolean {
        return regex.matches(address)
    }
}
