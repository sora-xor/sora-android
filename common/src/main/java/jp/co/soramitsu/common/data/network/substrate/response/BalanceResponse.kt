/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.substrate.response

import androidx.annotation.Keep

@Keep
data class BalanceResponse(val balance: String)
