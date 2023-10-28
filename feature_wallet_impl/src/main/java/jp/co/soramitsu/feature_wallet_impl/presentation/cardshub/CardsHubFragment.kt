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

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.openGooglePlay
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsList
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BackupWalletState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BasicBannerCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BuyXorState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoriteAssetsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.ReferralState
import jp.co.soramitsu.common_wallet.presentation.compose.states.SoraCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContract
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@AndroidEntryPoint
class CardsHubFragment : SoraBaseFragment<CardsHubViewModel>() {

    override val viewModel: CardsHubViewModel by viewModels()

    private val soraCardSignIn = registerForActivityResult(
        SoraCardContract()
    ) { viewModel.handleSoraCardResult(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()
        viewModel.launchSoraCardSignIn.observe { contractData ->
            soraCardSignIn.launch(contractData)
        }
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController,
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            var qrSelection by remember { mutableStateOf(false) }
            val onQrClick: () -> Unit = {
                qrSelection = true
            }
            if (qrSelection) {
                viewModel.openQrCodeFlow()
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                CardsMainScreen(
                    scrollState = scrollState,
                    state = state,
                    onAccountClick = viewModel::onAccountClick,
                    onQrClick = onQrClick,
                    onAssetClick = viewModel::onAssetClick,
                    onPoolClick = viewModel::onPoolClick,
                    onOpenFullCardClick = viewModel::onOpenFullCard,
                    onSoraCardClick = viewModel::onCardStateClicked,
                    onSoraCardNeedUpdateClick = { this@CardsHubFragment.context?.openGooglePlay() },
                    onSoraCardClose = viewModel::onRemoveSoraCard,
                    onBuyXorClick = viewModel::onBuyCrypto,
                    onBuyXorClose = viewModel::onRemoveBuyXorToken,
                    onReferralClick = viewModel::onStartReferral,
                    onReferralClose = viewModel::onRemoveReferralCard,
                    onBackupBannerClick = viewModel::onBackupBannerClick,
                    onEdit = viewModel::onEditViewClick,
                )
            }
        }
    }
}

@Composable
private fun CardsMainScreen(
    scrollState: ScrollState,
    state: CardsState,
    onAccountClick: () -> Unit,
    onQrClick: () -> Unit,
    onAssetClick: (String) -> Unit,
    onPoolClick: (StringPair) -> Unit,
    onOpenFullCardClick: (AssetCardState) -> Unit,
    onSoraCardClick: () -> Unit,
    onSoraCardNeedUpdateClick: () -> Unit,
    onSoraCardClose: () -> Unit,
    onBuyXorClick: () -> Unit,
    onBuyXorClose: () -> Unit,
    onReferralClick: () -> Unit,
    onReferralClose: () -> Unit,
    onBackupBannerClick: () -> Unit,
    onEdit: () -> Unit,
) {
    TopBar(
        account = state.curAccount,
        onAccountClick = onAccountClick,
        onQrClick = onQrClick,
    )
    Spacer(modifier = Modifier.size(size = 16.dp))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimens.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.loading) {
            CircularProgressIndicator(
                color = MaterialTheme.customColors.fgPrimary,
            )
        }
        state.cards.forEach { cardState ->
            when (cardState) {
                is TitledAmountCardState -> {
                    CommonHubCard(
                        title = cardState.title,
                        amount = cardState.amount,
                        onOpenFullCardClick = { onOpenFullCardClick.invoke(cardState.state) },
                        collapseState = cardState.collapsedState,
                        onCollapseClick = cardState.onCollapseClick
                    ) {
                        when (cardState.state) {
                            is FavoriteAssetsCardState -> AssetsCard(
                                cardState.state as FavoriteAssetsCardState,
                                onAssetClick,
                            )
                            is FavoritePoolsCardState -> PoolsList(
                                (cardState.state as FavoritePoolsCardState).state,
                                onPoolClick,
                            )
                        }
                    }
                }

                is BasicBannerCardState -> {
                    when (cardState) {
                        BackupWalletState -> {
                            BackupCard(
                                onStartClicked = onBackupBannerClick,
                            )
                        }
                        BuyXorState -> {
                            BuyXorCard(
                                onBuyXorClicked = onBuyXorClick,
                                onCloseCard = onBuyXorClose,
                            )
                        }
                        ReferralState -> {
                            ReferralCard(
                                onStartClicked = onReferralClick,
                                onCloseCard = onReferralClose,
                            )
                        }
                        is SoraCardState -> {
                            SoraCard(
                                state = cardState,
                                onCardStateClicked = onSoraCardClick,
                                onCloseClicked = onSoraCardClose,
                                onNeedUpdate = onSoraCardNeedUpdateClick,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(size = 16.dp))
        }

        if (state.cards.isNotEmpty())
            BleachedButton(
                modifier = Modifier
                    .padding(bottom = Dimens.x4)
                    .align(Alignment.CenterHorizontally),
                size = Size.Small,
                order = Order.SECONDARY,
                text = stringResource(id = R.string.edit_view),
                onClick = onEdit,
            )
    }
}

@Composable
@Preview
private fun PreviewCardsMainScreen() {
    Column() {
        CardsMainScreen(
            scrollState = rememberScrollState(),
            state = CardsState(
                curAccount = "cnVko",
                accountAddress = "",
                loading = true,
                cards = listOf(
                    BuyXorState, ReferralState, BackupWalletState,
                ),
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}
