/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class Asset(
    val balance: BigDecimal?,
    val id: String?
)