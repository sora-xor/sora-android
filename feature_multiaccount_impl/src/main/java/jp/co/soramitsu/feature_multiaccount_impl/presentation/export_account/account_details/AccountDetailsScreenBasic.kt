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
