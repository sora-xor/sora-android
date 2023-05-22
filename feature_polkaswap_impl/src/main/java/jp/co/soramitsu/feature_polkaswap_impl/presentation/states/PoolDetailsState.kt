/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.states

import android.net.Uri

internal data class PoolDetailsState(
    val token1Icon: Uri,
    val token2Icon: Uri,
    val symbol1: String,
    val symbol2: String,
    val apy: String,
    val pooled1: String,
    val pooled2: String,
    val addEnabled: Boolean,
    val removeEnabled: Boolean,
)
