/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.states

import android.graphics.drawable.Drawable
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common_wallet.presentation.compose.components.SelectSearchAssetState

internal data class SendState(
    val address: String,
    val icon: Drawable,
    val inProgress: Boolean = false,
    val reviewEnabled: Boolean = false,
    val input: AssetAmountInputState? = null,
    val fee: String = "",
    val feeFiat: String = "",
    val feeLoading: Boolean = true,
    val selectSearchAssetState: SelectSearchAssetState = SelectSearchAssetState("", emptyList()),
)
