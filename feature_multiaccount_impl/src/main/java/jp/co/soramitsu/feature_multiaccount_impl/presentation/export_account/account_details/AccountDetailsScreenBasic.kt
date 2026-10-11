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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyChip
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeAction
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeCard
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountDetailsScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ReadOnlyAccountUi
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AccountDetailsScreenBasic(
    state: AccountDetailsScreenState,
    onValueChanged: (TextFieldValue) -> Unit,
    onShowPassphrase: () -> Unit,
    onShowRawSeed: () -> Unit,
    onExportJson: () -> Unit,
    onBackupClicked: () -> Unit,
    onLogout: () -> Unit,
    onAddressClick: () -> Unit,
    onRestoreKeysWithPhrase: () -> Unit = {},
    onRestoreKeysFromGoogle: () -> Unit = {},
    onShowSavedBackup: () -> Unit = {},
    onRestoreKeysAnotherWay: () -> Unit = {},
) {
    state.readOnly?.let { readOnly ->
        ReadOnlyAccountPanel(
            readOnly = readOnly,
            onRestoreKeysWithPhrase = onRestoreKeysWithPhrase,
            onRestoreKeysAnotherWay = onRestoreKeysAnotherWay,
            onRestoreKeysFromGoogle = onRestoreKeysFromGoogle,
            onShowSavedBackup = onShowSavedBackup,
            onRemove = onLogout,
        )
        Spacer(modifier = Modifier.size(Dimens.x2))
    }
    AccountName(
        inputTextState = state.accountNameState,
        onValueChanged = onValueChanged,
    )
    Spacer(modifier = Modifier.size(Dimens.x2))
    AccountAddress(
        state.address,
        onAddressClick,
    )
    Spacer(modifier = Modifier.size(Dimens.x2))
    BackupOptions(
        isMnemonicAvailable = state.isMnemonicAvailable,
        isBackupAvailable = state.isBackupAvailable,
        isBackupLoading = state.isBackupLoading,
        onShowPassphrase = onShowPassphrase,
        onShowRawSeed = onShowRawSeed,
        onExportJson = onExportJson,
        onBackupGoogle = onBackupClicked,
        isCloudBackupAvailable = state.isCloudBackupAvailable,
    )
    // A read-only account's removal is offered inside its panel, after the ways to repair it.
    if (state.readOnly == null) {
        Spacer(modifier = Modifier.size(Dimens.x2))
        TonalButton(
            modifier = Modifier
                .testTagAsId("ForgetAccount")
                .fillMaxWidth(),
            text = stringResource(R.string.forget_account),
            onClick = onLogout,
            size = Size.Large,
            order = Order.TERTIARY,
        )
    }
}

/** Test tags of the read-only panel in Account options. */
internal object ReadOnlyAccountPanelTags {
    const val PANEL = "ReadOnlyAccountPanel"
    const val REASON = "ReadOnlyAccountReason"
    const val RESTORE_PHRASE = "ReadOnlyRestoreWithPhrase"
    const val RESTORE_OTHER = "ReadOnlyRestoreAnotherWay"
    const val RESTORE_GOOGLE = "ReadOnlyRestoreFromGoogle"
    const val SHOW_BACKUP = "ReadOnlyShowBackup"
    const val REMOVE = "ReadOnlyRemove"
}

@StringRes
internal fun UnavailableWalletReason.textResource(): Int = when (this) {
    UnavailableWalletReason.KEY_MISSING -> R.string.account_read_only_reason_missing
    UnavailableWalletReason.KEY_INCOMPLETE -> R.string.account_read_only_reason_incomplete
    UnavailableWalletReason.KEY_UNREADABLE -> R.string.account_read_only_reason_unreadable
    UnavailableWalletReason.KEY_NOT_USABLE -> R.string.account_read_only_reason_not_usable
    UnavailableWalletReason.WATCH_ONLY -> R.string.account_read_only_reason_watch_only
    UnavailableWalletReason.NOT_CHECKED -> R.string.account_read_only_reason_not_checked
}

/**
 * Why this account cannot send from this phone, and every way forward
 * (`UserRepository.unavailableWalletOptions`): restore its keys in place with its recovery
 * phrase, with its raw seed or backup file, or with its Google backup; look at the backup that
 * is saved here; or remove it. The repair comes first and keeps the account; removal is last
 * and is never needed for the repair.
 */
@Composable
internal fun ReadOnlyAccountPanel(
    readOnly: ReadOnlyAccountUi,
    onRestoreKeysWithPhrase: () -> Unit,
    onRestoreKeysAnotherWay: () -> Unit = {},
    onRestoreKeysFromGoogle: () -> Unit,
    onShowSavedBackup: () -> Unit,
    onRemove: () -> Unit,
) {
    val n = MaterialTheme.neon
    SafeCard(modifier = Modifier.testTagAsId(ReadOnlyAccountPanelTags.PANEL)) {
        ReadOnlyChip()
        Spacer(modifier = Modifier.size(Dimens.x1))
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.account_read_only_title),
            style = MaterialTheme.customTypography.headline2,
            color = n.textPrimary,
        )
        Spacer(modifier = Modifier.size(Dimens.x1))
        Text(
            modifier = Modifier.testTagAsId(ReadOnlyAccountPanelTags.REASON),
            text = stringResource(readOnly.reason.textResource()),
            style = MaterialTheme.customTypography.paragraphS,
            color = n.textPrimary,
        )
        Spacer(modifier = Modifier.size(Dimens.x1))
        Text(
            text = stringResource(R.string.account_read_only_options_body),
            style = MaterialTheme.customTypography.paragraphS,
            color = n.textSecondary,
        )
        Spacer(modifier = Modifier.size(Dimens.x2))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.x1)) {
            if (readOnly.canRestoreWithPhrase) {
                SafeAction(
                    modifier = Modifier.testTagAsId(ReadOnlyAccountPanelTags.RESTORE_PHRASE),
                    text = stringResource(R.string.account_read_only_restore_phrase),
                    variant = NeonVariant.Safe,
                    onClick = onRestoreKeysWithPhrase,
                )
                // A wallet that was imported by raw seed or from a backup file has no phrase:
                // the same in-place restore, from Import account's list of sources.
                SafeAction(
                    modifier = Modifier.testTagAsId(ReadOnlyAccountPanelTags.RESTORE_OTHER),
                    text = stringResource(R.string.account_read_only_restore_other),
                    variant = NeonVariant.Secondary,
                    onClick = onRestoreKeysAnotherWay,
                )
            }
            if (readOnly.canRestoreFromGoogle) {
                SafeAction(
                    modifier = Modifier.testTagAsId(ReadOnlyAccountPanelTags.RESTORE_GOOGLE),
                    text = stringResource(R.string.account_read_only_restore_google),
                    variant = NeonVariant.Secondary,
                    onClick = onRestoreKeysFromGoogle,
                )
            }
            if (readOnly.canShowBackup) {
                SafeAction(
                    modifier = Modifier.testTagAsId(ReadOnlyAccountPanelTags.SHOW_BACKUP),
                    text = stringResource(R.string.account_read_only_show_backup),
                    variant = NeonVariant.Secondary,
                    onClick = onShowSavedBackup,
                )
            }
            if (readOnly.canRestoreWithPhrase) {
                Text(
                    text = stringResource(R.string.account_read_only_restore_note),
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                )
            }
            if (readOnly.canRemove) {
                SafeAction(
                    modifier = Modifier.testTagAsId(ReadOnlyAccountPanelTags.REMOVE),
                    text = stringResource(R.string.account_read_only_remove),
                    variant = NeonVariant.Tertiary,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewAccountDetailsScreenBasic01() {
    SoraAppTheme {
        Column(
            modifier = Modifier
                .background(Color.White)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            AccountDetailsScreenBasic(
                state = AccountDetailsScreenState(
                    accountNameState = InputTextState(
                        value = TextFieldValue("bla"),
                        label = "Name"
                    ),
                    isMnemonicAvailable = false,
                    isBackupLoading = false,
                    isBackupAvailable = false,
                    "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
                ),
                onValueChanged = {},
                onShowPassphrase = {},
                onShowRawSeed = {},
                onExportJson = {},
                onLogout = {},
                onAddressClick = {},
                onBackupClicked = {}
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAccountDetailsScreenBasic02() {
    SoraAppTheme {
        Column(
            modifier = Modifier
                .background(Color.White)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            AccountDetailsScreenBasic(
                state = AccountDetailsScreenState(
                    accountNameState = InputTextState(
                        value = TextFieldValue("bla"),
                        label = "Name"
                    ),
                    isMnemonicAvailable = false,
                    isBackupLoading = false,
                    isBackupAvailable = false,
                    "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
                ),
                onValueChanged = {},
                onShowPassphrase = {},
                onShowRawSeed = {},
                onExportJson = {},
                onLogout = {},
                onAddressClick = {},
                onBackupClicked = {}
            )
        }
    }
}
