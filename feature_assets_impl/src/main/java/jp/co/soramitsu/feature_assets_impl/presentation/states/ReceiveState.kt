/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.states

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

internal data class ReceiveState(
    val qr: Bitmap?,
    val name: String,
    val address: String,
    val avatar: Drawable,
)
