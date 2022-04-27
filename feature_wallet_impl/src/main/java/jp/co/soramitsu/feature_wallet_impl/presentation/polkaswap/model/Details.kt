/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.model

import android.text.SpannableString
import androidx.annotation.StringRes

data class DetailsItem(
    val title: String,
    val value: SpannableString,
    val messageAlert: MessageAlert? = null
)

data class DetailsSection(
    val title: String,
    val items: List<DetailsItem>
)

data class MessageAlert(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val positiveButton: Int
)
