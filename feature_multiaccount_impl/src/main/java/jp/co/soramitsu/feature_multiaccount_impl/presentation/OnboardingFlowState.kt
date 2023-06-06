/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import jp.co.soramitsu.common.presentation.compose.webview.WebViewState
import jp.co.soramitsu.ui_core.component.input.InputTextState

data class RecoveryState(
    val recoveryType: RecoveryType,
    val title: Int,
    val recoveryInputState: InputTextState = InputTextState(),
    val isButtonEnabled: Boolean = false
)

enum class RecoveryType {
    PASSPHRASE,
    SEED
}

data class RecoveryAccountNameState(
    val accountNameInputState: InputTextState = InputTextState()
)

data class TermsAndPrivacyState(
    val title: Int,
    val webViewState: WebViewState
)

data class CreateAccountState(
    val accountNameInputState: InputTextState = InputTextState(),
    val btnEnabled: Boolean = true,
)

data class CreateBackupPasswordState(
    val password: InputTextState = InputTextState(),
    val passwordConfirmation: InputTextState = InputTextState(),
    val warningIsSelected: Boolean = false,
    val setPasswordButtonIsEnabled: Boolean = false,
)

data class MnemonicConfirmationState(
    val currentWordIndex: Int,
    val buttonsList: List<String>,
    val confirmationStep: Int = 0,
    val confirmedWordIndexes: MutableList<Int> = mutableListOf()
)
