/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.request

import androidx.annotation.Keep

@Keep
data class SubqueryRequest(
    val query: String,
    val variables: String? = null
)
