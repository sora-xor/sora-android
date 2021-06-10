/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model

/**
 * transaction history list item
 */
data class SoraTransaction(
    val id: String,
    val isIncoming: Boolean,
    val statusIconResource: Int,
    val initials: String,
    val title: String,
    val dateString: String,
    val amountFormatted: String,
    val amountFullFormatted: String,
    val pending: Boolean = false,
    val success: Boolean? = null,
)
