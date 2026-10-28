/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import android.graphics.drawable.Drawable
import jp.co.soramitsu.common.presentation.compose.webview.WebViewState
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta

data class RecoveryState(
    val recoveryType: RecoveryType,
    val title: Int,
    val recoveryInputState: InputTextState = InputTextState(),
    val isButtonEnabled: Boolean = false,
    /** The password of an encrypted backup file; only used by [RecoveryType.JSON]. */
    val passwordInputState: InputTextState = InputTextState(),
    /**
     * The name of the backup file that was chosen ([RecoveryType.JSON]). The file's text is kept
     * by the view model and is not put into the text field.
     */
    val chosenFileName: String? = null,
)

enum class RecoveryType {
    PASSPHRASE,
    SEED,

    /** The encrypted JSON export (Account details, Export JSON), pasted or chosen as a file. */
    JSON,
}

data class RecoveryAccountNameState(
    val accountNameInputState: InputTextState = InputTextState()
)

data class TutorialScreenState(
    val isGoogleSigninLoading: Boolean = false,
    val isGoogleBackupAvailable: Boolean = true,
    /**
     * Wallets whose keys are on this phone without an account and that the stored PIN still
     * protects. Above zero, the screen offers "Wallets found on this phone".
     */
    val foundWalletsCount: Int = 0,
)

data class TermsAndPrivacyState(
    val title: Int,
    val webViewState: WebViewState
)

data class CreateAccountState(
    val accountNameInputState: InputTextState = InputTextState(),
    val btnEnabled: Boolean = true,
    /** The import is running (keys are derived, a backup file is opened). */
    val loading: Boolean = false,
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
    val isImportMoreAvailable: Boolean = false,
    val isContinueButtonEnabled: Boolean = false,
    val isLoading: Boolean = false,
)

data class BackupAccountMetaWithIcon(
    val backupAccountMeta: BackupAccountMeta,
    val icon: Drawable?,
    /**
     * The account of this backup is already listed on this phone but cannot sign here. Restoring
     * the backup completes or repairs its keys in place; the list says so under the address.
     */
    val readOnlyOnThisPhone: Boolean = false,
)

data class MnemonicConfirmationState(
    val currentWordIndex: Int,
    val buttonsList: List<String>,
    val confirmationStep: Int = 0,
    val confirmedWordIndexes: MutableList<Int> = mutableListOf()
)
