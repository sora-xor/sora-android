/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPressed
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPunched
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AccountDetailsScreen(
    viewModel: AccountDetailsViewModel
) {
    AccountDetails(
        accountName = "",
        onAccountNameChange = viewModel::onNameChange,
        onShowPassphrase = viewModel::onShowPassphrase,
        onShowRawSeed = viewModel::onShowRawSeed,
        onLogout = viewModel::onLogout
    )
}

@Composable
private fun AccountDetails(
    accountName: String,
    onAccountNameChange: (String) -> Unit,
    onShowPassphrase: () -> Unit,
    onShowRawSeed: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        AccountName(accountName, onAccountNameChange)

        BackupOptions(onShowPassphrase, onShowRawSeed)

        LogoutButton(onLogout)
    }
}

@Composable
private fun AccountName(
    value: String,
    onValueChanged: (String) -> Unit
) {
    NeuCardPressed(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(Dimens.x2),
        radius = 28
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChanged,
            label = {
                Text(
                    text = stringResource(R.string.common_name),
                    style = MaterialTheme.customTypography.textXS
                )
            },
            textStyle = MaterialTheme.customTypography.textM,
            singleLine = true,
            maxLines = 1,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun BackupOptions(
    onShowPassphrase: () -> Unit,
    onShowRawSeed: () -> Unit
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
                Option(
                    icon = painterResource(R.drawable.ic_passphrase),
                    label = stringResource(R.string.export_account_details_show_passphrase),
                    onClick = onShowPassphrase
                )
                Option(
                    icon = painterResource(R.drawable.ic_key),
                    label = stringResource(R.string.export_account_details_show_raw_seed),
                    onClick = onShowRawSeed
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
    NeuCardPunched(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.x2)
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(Dimens.x2)
        ) {
            Icon(
                modifier = Modifier.padding(end = Dimens.x1),
                painter = painterResource(R.drawable.ic_logout),
                tint = MaterialTheme.customColors.accentPrimary,
                contentDescription = null,
            )

            Text(
                text = stringResource(R.string.profile_logout_title),
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.accentPrimary
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAccountDetailsScreen() {
    SoraAppTheme {
        AccountDetails(
            accountName = "Private account",
            onAccountNameChange = {},
            onShowPassphrase = {},
            onShowRawSeed = {},
            onLogout = {}
        )
    }
}
