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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.ui_core.component.input.InputTextState

data class AccountDetailsScreenState(
    val accountNameState: InputTextState,
    val isMnemonicAvailable: Boolean,
    val isBackupLoading: Boolean,
    val isBackupAvailable: Boolean?,
    val address: String,
    val isCloudBackupAvailable: Boolean = true,
    /** Set while this account cannot sign on this phone: why, and what can be done. */
    val readOnly: ReadOnlyAccountUi? = null,
)

/**
 * The read-only panel of Account options. Every action keeps the account; removal is offered
 * last and is never the repair.
 */
data class ReadOnlyAccountUi(
    val reason: UnavailableWalletReason,
    /** Import its recovery phrase, raw seed or backup file: the keys are restored in place. */
    val canRestoreWithPhrase: Boolean,
    /** Restore its Google Drive backup (the same in-place restore). */
    val canRestoreFromGoogle: Boolean,
    /** A phrase or seed saved for it on this phone can be shown after the PIN. */
    val canShowBackup: Boolean,
    /** The confirmed removal is offered (the matching backup is shown first). */
    val canRemove: Boolean,
)
