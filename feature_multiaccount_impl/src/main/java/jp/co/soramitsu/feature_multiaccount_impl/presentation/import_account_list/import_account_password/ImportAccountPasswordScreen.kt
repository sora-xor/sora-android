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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_password

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.BackupAccountMetaWithIcon
import jp.co.soramitsu.feature_multiaccount_impl.presentation.ImportAccountPasswordState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ImportAccountPasswordScreen(
    importAccountScreenState: ImportAccountPasswordState,
    onPasswordChanged: (value: TextFieldValue) -> Unit,
    onContinueClicked: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x2),
    ) {
        Column {
            Text(
                modifier = Modifier
                    .padding(
                        start = Dimens.x3,
                        end = Dimens.x2,
                        top = Dimens.x3,
                        bottom = Dimens.x3
                    ),
                text = stringResource(id = R.string.enter_password_description),
                style = MaterialTheme.customTypography.paragraphM
            )
            importAccountScreenState.selectedAccount?.let {
                AccountWithIcon(
                    modifier = Modifier
                        .background(MaterialTheme.customColors.bgPage)
                        .padding(vertical = Dimens.x2, horizontal = Dimens.x3),
                    address = it.backupAccountMeta.address,
                    accountName = it.backupAccountMeta.name,
                    accountIcon = it.icon
                )
            }

            InputText(
                modifier = Modifier
                    .padding(Dimens.x3)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = importAccountScreenState.passwordInput,
                visualTransformation = PasswordVisualTransformation('*'),
                onValueChange = onPasswordChanged,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            )

            LoaderWrapper(
                modifier = Modifier
                    .padding(start = Dimens.x3, end = Dimens.x3, bottom = Dimens.x3)
                    .fillMaxWidth(),
                loading = importAccountScreenState.isLoading,
                loaderSize = Size.Large,
            ) { modifier, elevation ->
                FilledButton(
                    modifier = modifier
                        .fillMaxWidth(),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.common_continue),
                    onClick = onContinueClicked
                )
            }
        }
    }
}

@Composable
fun AccountWithIcon(
    modifier: Modifier = Modifier,
    address: String,
    accountName: String,
    accountIcon: Drawable,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(accountIcon).build(),
            modifier = Modifier
                .size(size = 40.dp),
            contentDescription = null,
            imageLoader = LocalContext.current.imageLoader,
        )

        Column(
            modifier = Modifier
                .padding(start = Dimens.x1)
        ) {
            Text(
                text = accountName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.customTypography.textS,
            )

            Text(
                text = address,
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold,
            )
        }
    }
}

@Composable
@Preview
fun PreviewEnterPassphraseScreen() {
    ImportAccountPasswordScreen(
        ImportAccountPasswordState(
            selectedAccount = BackupAccountMetaWithIcon(
                BackupAccountMeta("aa", "add", "fileid"), Drawable.createFromPath("")!!
            )
        ),
        {},
        {}
    )
}
