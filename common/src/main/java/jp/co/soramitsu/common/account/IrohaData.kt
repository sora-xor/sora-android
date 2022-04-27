/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.account

data class IrohaData(
    val address: String,
    val claimSignature: String,
    val publicKey: String,
)
