/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.states

import android.net.Uri
import jp.co.soramitsu.common.util.StringPair

data class PoolSettingsState(
    val id: StringPair,
    val token1Icon: Uri,
    val token2Icon: Uri,
    val tokenName: String,
    val assetAmount: String,
    val favorite: Boolean,
    val fiat: Double,
)
