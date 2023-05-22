/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

import jp.co.soramitsu.ui_core.component.input.InputTextState

data class AccountDetailsScreenState(
    val accountNameState: InputTextState,
    val isMnemonicAvailable: Boolean,
    val address: String,
)
