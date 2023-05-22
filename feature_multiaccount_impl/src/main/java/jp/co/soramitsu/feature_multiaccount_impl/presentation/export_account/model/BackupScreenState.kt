/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

data class BackupScreenState(
    val mnemonicWords: List<String> = emptyList(),
    val seedString: String = "",
    val isCreatingFlow: Boolean = false,
    val isSkipButtonEnabled: Boolean = false,
)
