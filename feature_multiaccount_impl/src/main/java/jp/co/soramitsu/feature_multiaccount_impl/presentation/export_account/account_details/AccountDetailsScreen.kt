/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPressed
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPunched
import jp.co.soramitsu.common.presentation.compose.neumorphism.TonalNeumorphButton
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountDetailsScreenState
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AccountDetailsScreen(
    state: AccountDetailsScreenState,
    viewModel: AccountDetailsViewModel,
    scrollState: ScrollState
) {
    AccountDetails(
        scrollState = scrollState,
        accountNameState = state.accountNameState,
        isMnemonicAvailable = state.isMnemonicAvailable,
        onAccountNameChange = viewModel::onNameChange,
        onShowPassphrase = viewModel::onShowPassphrase,
        onShowRawSeed = viewModel::onShowRawSeed,
        onExportJson = viewModel::onExportJson,
        onLogout = viewModel::onLogout
    )
}

@Composable
private fun AccountDetails(
    scrollState: ScrollState,
    accountNameState: InputTextState,
    isMnemonicAvailable: Boolean,
    onAccountNameChange: (TextFieldValue) -> Unit,
    onShowPassphrase: () -> Unit,
    onShowRawSeed: () -> Unit,
    onExportJson: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.customColors.bgPage)
            .verticalScroll(scrollState)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AccountName(accountNameState, onAccountNameChange)

        BackupOptions(isMnemonicAvailable, onShowPassphrase, onShowRawSeed, onExportJson)

        LogoutButton(onLogout)
    }
}

@Composable
private fun AccountName(
    inputTextState: InputTextState,
    onValueChanged: (TextFieldValue) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    NeuCardPressed(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(Dimens.x2)
    ) {
        InputText(
            modifier = Modifier
                .background(MaterialTheme.customColors.bgSurface)
                .fillMaxWidth()
                .wrapContentHeight(),
            maxLines = 1,
            singleLine = true,
            state = inputTextState,
            onValueChange = onValueChanged,
            focusRequester = focusRequester,
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                }
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun BackupOptions(
    isMnemonicAvailable: Boolean,
    onShowPassphrase: () -> Unit,
    onShowRawSeed: () -> Unit,
    onExportJson: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(vertical = Dimens.x1)
            .fillMaxWidth()
    ) {
        Text(
            modifier = Modifier.padding(start = Dimens.x4, end = Dimens.x4),
            text = stringResource(R.string.export_account_details_backup_options),
            style = MaterialTheme.customTypography.headline4,
            color = MaterialTheme.customColors.fgSecondary
        )

        NeuCardPunched(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
        ) {
            Column {
                if (isMnemonicAvailable) {
                    Option(
                        icon = painterResource(R.drawable.ic_passphrase),
                        label = stringResource(R.string.export_account_details_show_passphrase),
                        onClick = onShowPassphrase
                    )
                }
                Option(
                    icon = painterResource(R.drawable.ic_key),
                    label = stringResource(R.string.export_account_details_show_raw_seed),
                    onClick = onShowRawSeed
                )
                Option(
                    icon = painterResource(R.drawable.ic_arrow_up_rectangle_24),
                    label = stringResource(R.string.export_protection_json_title),
                    onClick = onExportJson
                )
            }
        }

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.x4, end = Dimens.x4),
            text = stringResource(R.string.export_account_details_backup_description),
            style = MaterialTheme.customTypography.paragraphXS,
            color = MaterialTheme.customColors.fgSecondary
        )
    }
}

@Composable
private fun Option(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(Dimens.x2)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.padding(end = Dimens.x1),
                painter = icon,
                tint = MaterialTheme.customColors.fgSecondary,
                contentDescription = null
            )

            Text(
                text = label,
                style = MaterialTheme.customTypography.textM
            )
        }

        Icon(
            modifier = Modifier.padding(Dimens.x05),
            painter = painterResource(R.drawable.ic_neu_chevron_right),
            tint = MaterialTheme.customColors.fgSecondary,
            contentDescription = null
        )
    }
}

@Composable
private fun LogoutButton(
    onClick: () -> Unit
) {
    TonalNeumorphButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.x2),
        text = stringResource(R.string.profile_logout_title),
        onClick = onClick,
        leftIcon = painterResource(R.drawable.ic_logout),
    )
}

@Preview
@Composable
private fun PreviewAccountDetailsScreen() {
    SoraAppTheme {
        AccountDetails(
            scrollState = rememberScrollState(),
            accountNameState = InputTextState(TextFieldValue("Private account")),
            isMnemonicAvailable = false,
            onAccountNameChange = {},
            onShowPassphrase = {},
            onShowRawSeed = {},
            onExportJson = {},
            onLogout = {}
        )
    }
}
