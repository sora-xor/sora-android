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

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.androidfoundation.intent.ShareUtil
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyBanner
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyLearnMoreSheet
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletUpgradeRetry
import jp.co.soramitsu.common.presentation.compose.neon.safemode.rememberReadOnlyBrowsing
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioRoutes
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioViewModel

@AndroidEntryPoint
class CardsHubFragment : SoraBaseFragment<CardsHubViewModel>() {

    override val viewModel: CardsHubViewModel by viewModels()

    // The Portfolio view (portfolio-view SPEC) is the Wallet tab's home in every build.
    private val portfolioViewModel: PortfolioViewModel by viewModels()

    @javax.inject.Inject
    lateinit var qrScannerFactory: jp.co.soramitsu.feature_assets_api.presentation.QrScannerContractFactory
    private lateinit var nexusRecipientScanner: androidx.activity.result.ActivityResultLauncher<Unit>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nexusRecipientScanner = registerForActivityResult(
            qrScannerFactory.create(jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose.RECIPIENT),
            { value ->
                val cameraPermissionDenied = value == null && context?.let {
                    androidx.core.content.ContextCompat.checkSelfPermission(it, android.Manifest.permission.CAMERA)
                } == android.content.pm.PackageManager.PERMISSION_DENIED
                viewModel.finishNexusRecipientScan(value, cameraPermissionDenied)
            },
        )
    }

    private fun scanNexusRecipient() {
        if (viewModel.beginNexusRecipientScan()) nexusRecipientScanner.launch(Unit)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()
        // Receive's Share, as on SORA2: the QR code as an image, with the address in the text.
        viewModel.nexusShare.observe { (qrCodeFile, message) ->
            context?.let { context ->
                ShareUtil.shareImageFile(
                    context = context,
                    title = getString(R.string.common_share),
                    file = qrCodeFile,
                    description = message,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNexusPortfolio()
    }

    override fun onStart() {
        super.onStart()
        // The dashboard reads and ticks only while it is on screen.
        portfolioViewModel.setVisible(true)
    }

    override fun onStop() {
        portfolioViewModel.setVisible(false)
        super.onStop()
    }

    override fun onDestinationChanged(destination: String) {
        // A network's XOR pages and "Where it lives" are pushed screens: like the SORA2 detail
        // screens, without the bottom bar.
        val bar = activity as? BottomBarController ?: return
        val pushed = destination in NexusRoutes.pushed || destination == PortfolioRoutes.TOKEN
        if (pushed) bar.hideBottomBar() else bar.showBottomBar()
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController,
    ) {
        composable(
            route = theOnlyRoute,
        ) { entry ->
            // The home keeps its own scroll position under the pages pushed over it; those pages
            // scroll with the toolbar's state, as the SORA2 screens do.
            val homeScrollState = rememberScrollState()
            FollowNexusSend(viewModel, entry, navController)
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val nexusPortfolio =
                    viewModel.nexusPortfolio.collectAsStateWithLifecycle().value
                val sora2Portfolio =
                    viewModel.sora2Portfolio.collectAsStateWithLifecycle().value
                PortfolioHomeHost(
                    scrollState = homeScrollState,
                    navController = navController,
                    portfolio = portfolioViewModel,
                    hub = viewModel,
                    state = state,
                    sora2Portfolio = sora2Portfolio,
                    nexusPortfolio = nexusPortfolio,
                )
            }
        }
        nexusPages(
            hub = viewModel,
            navController = navController,
            scrollState = scrollState,
            onScanRecipient = ::scanNexusRecipient,
        )
        composable(
            route = PortfolioRoutes.TOKEN,
            arguments = PortfolioRoutes.tokenArguments,
        ) { entry ->
            FollowNexusSend(viewModel, entry, navController)
            PortfolioWhereItLivesHost(
                tokenId = entry.arguments?.getString(PortfolioRoutes.TOKEN_ARG).orEmpty(),
                navController = navController,
                portfolio = portfolioViewModel,
                hub = viewModel,
                holdingKey = entry.arguments?.getString(PortfolioRoutes.HOLDING_ARG),
            )
        }
    }
}

/**
 * U9 (design spec 9.2): in read-only mode the home shows the chip and banner. Balances and
 * history stay visible; Send and Swap are off with their reason on their own screens.
 */
@Composable
internal fun ReadOnlyHomeBanner(learnMore: Boolean, onLearnMore: (Boolean) -> Unit) {
    if (!rememberReadOnlyBrowsing()) return
    val context = LocalContext.current
    ReadOnlyBanner(
        modifier = Modifier.padding(bottom = 16.dp),
        onTryAgain = { WalletUpgradeRetry.launch(context) },
        onLearnMore = { onLearnMore(true) },
    )
    if (learnMore) {
        ReadOnlyLearnMoreSheet(
            onTryAgain = {
                onLearnMore(false)
                WalletUpgradeRetry.launch(context)
            },
            onClose = { onLearnMore(false) },
        )
    }
}
