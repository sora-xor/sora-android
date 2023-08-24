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

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

data class SoraCardDetailsScreenState(
    val soraCardMainSoraContentCardState: SoraCardMainSoraContentCardState,
    val soraCardReferralBannerCardState: SoraCardReferralBannerCardState? = null,
    val soraCardRecentActivitiesCardState: SoraCardRecentActivitiesCardState? = null,
    val soraCardIBANCardState: SoraCardIBANCardState? = null,
    val soraCardSettingsCard: SoraCardSettingsCardState? = null,
    val logoutDialog: Boolean,
)

@Composable
fun SoraCardDetailsScreen(
    soraCardDetailsScreenState: SoraCardDetailsScreenState,
    onShowSoraCardDetailsClick: () -> Unit,
    onSoraCardMenuActionClick: (position: Int) -> Unit,
    onReferralBannerClick: () -> Unit,
    onCloseReferralBannerClick: () -> Unit,
    onRecentActivityClick: (position: Int) -> Unit,
    onShowMoreRecentActivitiesClick: () -> Unit,
    onIbanCardShareClick: () -> Unit,
    onIbanCardClick: () -> Unit,
    onSettingsOptionClick: (position: Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.x2),
        verticalArrangement = Arrangement
            .spacedBy(Dimens.x2),
        horizontalAlignment = Alignment
            .CenterHorizontally
    ) {
        item {
            SoraCardMainSoraContentCard(
                soraCardMainSoraContentCardState = soraCardDetailsScreenState.soraCardMainSoraContentCardState,
                onShowMoreClick = onShowSoraCardDetailsClick,
                onIconButtonClick = onSoraCardMenuActionClick
            )
        }
        soraCardDetailsScreenState.soraCardReferralBannerCardState?.let { state ->
            item {
                SoraCardReferralBannerCard(
                    soraCardReferralBannerCardState = state,
                    onReferAndEarnClick = onReferralBannerClick,
                    onCloseClick = onCloseReferralBannerClick
                )
            }
        }
        soraCardDetailsScreenState.soraCardRecentActivitiesCardState?.let { state ->
            if (state.data.isNotEmpty())
                item {
                    SoraCardRecentActivitiesCard(
                        soraCardRecentActivitiesCardState = state,
                        onListTileClick = onRecentActivityClick,
                        onShowMoreClick = onShowMoreRecentActivitiesClick
                    )
                }
        }
        soraCardDetailsScreenState.soraCardIBANCardState?.let { state ->
            item {
                SoraCardIBANCard(
                    soraCardIBANCardState = state,
                    onShareClick = onIbanCardShareClick,
                    onCardClick = onIbanCardClick,
                )
            }
        }
        soraCardDetailsScreenState.soraCardSettingsCard?.let { state ->
            if (state.settings.isNotEmpty())
                item {
                    SoraCardSettingsCard(
                        state = state,
                        onItemClick = onSettingsOptionClick
                    )
                }
        }
        item {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.x5)
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSoraCardDetailsScreen() {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.customColors.bgPage
            )
            .fillMaxSize()
    ) {
        SoraCardDetailsScreen(
            soraCardDetailsScreenState = SoraCardDetailsScreenState(
                soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
                    balance = 3665.50f,
                    isCardEnabled = false,
                    soraCardMenuActions = SoraCardMenuAction.values().toList()
                ),
                soraCardReferralBannerCardState = SoraCardReferralBannerCardState(),
                soraCardRecentActivitiesCardState = SoraCardRecentActivitiesCardState(
                    data = listOf()
                ),
                soraCardIBANCardState = SoraCardIBANCardState(
                    iban = "LT61 3250 0467 7252 5583",
                ),
                soraCardSettingsCard = SoraCardSettingsCardState(
                    soraCardSettingsOptions = SoraCardSettingsOption.values().toList()
                ),
                logoutDialog = false,
            ),
            onShowSoraCardDetailsClick = {},
            onSoraCardMenuActionClick = { _ -> },
            onReferralBannerClick = {},
            onCloseReferralBannerClick = {},
            onShowMoreRecentActivitiesClick = {},
            onRecentActivityClick = { _ -> },
            onIbanCardShareClick = {},
            onIbanCardClick = {},
            onSettingsOptionClick = { _ -> }
        )
    }
}
