/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.models

class ExtrinsicSubmitStatus(
    val success: Boolean,
    val txHash: String,
    val blockHash: String? = null,
)
