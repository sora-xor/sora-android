/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountDetailsScreenState
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun AccountDetailsScreenBasic(
    state: AccountDetailsScreenState,
    onValueChanged: (TextFieldValue) -> Unit,
    onShowPassphrase: () -> Unit,
    onShowRawSeed: () -> Unit,
    onExportJson: () -> Unit,
    onLogout: () -> Unit,
    onAddressClick: () -> Unit,
) {
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
        onShowPassphrase = onShowPassphrase,
        onShowRawSeed = onShowRawSeed,
        onExportJson = onExportJson,
    )
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

@Preview(showBackground = true)
@Composable
private fun PreviewAccountDetailsScreenBasic() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        AccountDetailsScreenBasic(
            state = AccountDetailsScreenState(
                accountNameState = InputTextState(value = TextFieldValue("bla"), label = "Name"),
                isMnemonicAvailable = false,
                "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
            ),
            onValueChanged = {},
            onShowPassphrase = {},
            onShowRawSeed = {},
            onExportJson = {},
            onLogout = {},
            onAddressClick = {},
        )
    }
}
