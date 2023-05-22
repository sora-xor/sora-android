/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.states

data class FrozenXorDetailsModel(
    val frozen: String,
    val frozenFiat: String,
    val bonded: String,
    val bondedFiat: String,
    val locked: String,
    val lockedFiat: String,
    val reserved: String,
    val reservedFiat: String,
    val redeemable: String,
    val redeemableFiat: String,
    val unbonding: String,
    val unbondingFiat: String,
)
