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

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.item.CategoryItem
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

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
            subtitle = if (state.soraCardNeedUpdate)
                stringResource(id = jp.co.soramitsu.oauth.R.string.card_update_title) else
                state.soraCardIbanError ?: stringResource(id = state.soraCardStatusStringRes),
            subtitleIcon = if (state.soraCardNeedUpdate) null else state.soraCardStatusIconDrawableRes,
            subtitleColor = if (state.soraCardNeedUpdate) MaterialTheme.customColors.statusError else MaterialTheme.customColors.fgSecondary,
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
                isDebugMenuAvailable = true,
                soraCardEnabled = true,
                soraCardNeedUpdate = false,
                soraCardStatusStringRes = R.string.more_menu_sora_card_subtitle,
                soraCardStatusIconDrawableRes = R.drawable.ic_connection_indicator_green,
                soraCardIbanError = null,
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
