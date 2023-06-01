/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.item.CategoryItem
import jp.co.soramitsu.ui_core.resources.Dimens

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ProfileItems(
    state: ProfileScreenState,
    onAccountsClick: () -> Unit,
    onSoraCardClick: () -> Unit,
    onBuyCrypto: () -> Unit,
    onNodeClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onLoginClick: () -> Unit,
    onReferralClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDebugMenuClick: () -> Unit
) {
    CategoryItem(
        modifier = Modifier
            .testTagAsId("CryptoAccountsButton")
            .padding(
                top = Dimens.x3
            ),
        title = stringResource(id = R.string.settings_crypto_accounts),
        subtitle = stringResource(id = R.string.settings_accounts_subtitle),
        icon = R.drawable.ic_settings_account,
        onClick = onAccountsClick,
    )
    if (state.soraCardEnabled) {
        CategoryItem(
            modifier = Modifier
                .testTagAsId("SoraCard")
                .padding(top = Dimens.x2),
            title = stringResource(id = R.string.more_menu_sora_card_title),
            subtitle = stringResource(id = state.soraCardStatusStringRes),
            subtitleIcon = state.soraCardStatusIconDrawableRes,
            icon = R.drawable.ic_buy_crypto,
            onClick = onSoraCardClick,
        )
        CategoryItem(
            modifier = Modifier
                .testTagAsId("BuyXor")
                .padding(top = Dimens.x2),
            title = stringResource(id = R.string.buy_crypto_buy_xor_with_fiat_title),
            subtitle = stringResource(id = R.string.buy_crypto_buy_xor_with_fiat_subtitle),
            icon = R.drawable.ic_settings_buy_crypto,
            onClick = onBuyCrypto,
        )
    }
    CategoryItem(
        modifier = Modifier
            .testTagAsId("Nodes")
            .padding(
                top = Dimens.x4
            ),
        title = stringResource(id = R.string.settings_nodes),
        subtitle = state.nodeName,
        subtitleIcon = if (state.nodeConnected) R.drawable.ic_connection_indicator_green else R.drawable.ic_connection_indicator_red,
        icon = R.drawable.ic_settings_node,
        onClick = onNodeClick,
    )
    CategoryItem(
        modifier = Modifier
            .testTagAsId("AppSettings")
            .padding(
                top = Dimens.x2
            ),
        title = stringResource(id = R.string.settings_header_app),
        subtitle = stringResource(id = R.string.settings_app_subtitle),
        icon = R.drawable.ic_settings_app,
        onClick = onAppSettingsClick,
    )
    CategoryItem(
        modifier = Modifier
            .testTagAsId("LoginAndSecurity")
            .padding(
                top = Dimens.x2
            ),
        title = stringResource(id = R.string.settings_login_title),
        subtitle = stringResource(id = R.string.settings_login_subtitle),
        icon = R.drawable.ic_settings_bio,
        onClick = onLoginClick,
    )
    CategoryItem(
        modifier = Modifier
            .testTagAsId("Invite")
            .padding(
                top = Dimens.x4
            ),
        title = stringResource(id = R.string.settings_invite_title),
        subtitle = stringResource(id = R.string.settings_invite_subtitle),
        icon = R.drawable.ic_settings_heart,
        onClick = onReferralClick,
    )
    CategoryItem(
        modifier = Modifier
            .testTagAsId("Information")
            .padding(
                top = Dimens.x2
            ),
        title = stringResource(id = R.string.settings_information_title),
        subtitle = stringResource(id = R.string.settings_information_subtitle),
        icon = R.drawable.ic_settings_info,
        onClick = onAboutClick,
    )
    if (state.isDebugMenuAvailable)
        CategoryItem(
            modifier = Modifier
                .testTagAsId("DebugMenu")
                .padding(
                    top = Dimens.x2
                ),
            title = "Debug Menu",
            onClick = onDebugMenuClick,
        )
}

@Preview
@Composable
private fun PreviewProfile() {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        ProfileItems(
            state = ProfileScreenState(
                nodeName = "wss://abcdf.df",
                nodeConnected = true,
                isDebugMenuAvailable = BuildUtils.isPlayMarket(),
                soraCardEnabled = true,
                soraCardStatusStringRes = R.string.more_menu_sora_card_subtitle,
                soraCardStatusIconDrawableRes = R.drawable.ic_connection_indicator_green
            ),
            onAccountsClick = { },
            onSoraCardClick = { },
            onBuyCrypto = { },
            onNodeClick = { },
            onAppSettingsClick = { },
            onLoginClick = { },
            onReferralClick = { },
            onAboutClick = {},
            onDebugMenuClick = {}
        )
    }
}
