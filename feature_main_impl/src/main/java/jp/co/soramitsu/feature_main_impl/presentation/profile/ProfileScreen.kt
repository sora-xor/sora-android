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

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.ui_core.component.item.CategoryItem
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun ProfileItems(
    state: ProfileScreenState,
    onAccountsClick: () -> Unit,
    onPolkamarktClick: () -> Unit,
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
    if (state.polkamarktVisible) {
        CategoryItem(
            modifier = Modifier
                .testTagAsId("Polkamarkt")
                .padding(top = Dimens.x2),
            title = stringResource(id = R.string.polkamarkt_title),
            subtitle = stringResource(id = R.string.polkamarkt_subtitle),
            icon = R.drawable.ic_settings_buy_crypto,
            onClick = onPolkamarktClick,
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
                isDebugMenuAvailable = true,
                polkamarktVisible = true,
            ),
            onAccountsClick = { },
            onPolkamarktClick = { },
            onNodeClick = { },
            onAppSettingsClick = { },
            onLoginClick = { },
            onReferralClick = { },
            onAboutClick = {},
            onDebugMenuClick = {}
        )
    }
}
