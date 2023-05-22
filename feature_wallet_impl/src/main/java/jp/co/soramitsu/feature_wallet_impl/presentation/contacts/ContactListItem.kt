/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.graphics.drawable.Drawable
import jp.co.soramitsu.ui_core.component.input.InputTextState

internal data class ContactsState(
    val accounts: List<ContactsListItem>,
    val input: InputTextState,
    val hint: Int,
    val myAddress: Boolean,
    val isSearchEntered: Boolean = false
)

internal data class ContactsListItem(
    val account: String,
    val icon: Drawable,
)
