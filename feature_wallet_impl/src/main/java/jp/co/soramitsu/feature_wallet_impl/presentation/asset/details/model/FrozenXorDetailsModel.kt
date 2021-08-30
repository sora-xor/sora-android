/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.model

data class FrozenXorDetailsModel(
    val bonded: String,
    val frozen: String,
    val locked: String,
    val reserved: String,
    val redeemable: String,
    val unbonding: String
)
