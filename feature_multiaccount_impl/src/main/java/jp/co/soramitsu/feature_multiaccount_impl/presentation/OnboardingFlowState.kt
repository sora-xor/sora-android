/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import android.graphics.drawable.Drawable
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
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

data class TutorialScreenState(
    val isGoogleSigninLoading: Boolean = false
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
    val isLoading: Boolean = false
)

data class ImportAccountListScreenState(
    val accountList: List<BackupAccountMetaWithIcon> = emptyList()
)

data class ImportAccountPasswordState(
    val selectedAccount: BackupAccountMetaWithIcon? = null,
    val passwordInput: InputTextState = InputTextState(),
    val isContinueButtonEnabled: Boolean = false,
    val isLoading: Boolean = false,
)

data class BackupAccountMetaWithIcon(
    val backupAccountMeta: BackupAccountMeta,
    val icon: Drawable?,
)

data class MnemonicConfirmationState(
    val currentWordIndex: Int,
    val buttonsList: List<String>,
    val confirmationStep: Int = 0,
    val confirmedWordIndexes: MutableList<Int> = mutableListOf()
)
