/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter

data class ContactMenuItem(
    val iconRes: Int,
    val nameRes: Int,
    val type: Type
) {
    enum class Type {
        VAL_TO_MY_ETH
    }
}
