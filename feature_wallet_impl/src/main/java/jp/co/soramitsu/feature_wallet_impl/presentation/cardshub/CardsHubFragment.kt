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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyBanner
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyLearnMoreSheet
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeCompleteChecks
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeCompleteSheet
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeWelcome
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletSessionState
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletUpgradeRetry
import jp.co.soramitsu.common.presentation.compose.neon.safemode.rememberReadOnlyBrowsing
import jp.co.soramitsu.common.presentation.compose.neon.safemode.rememberWalletReadOnly
import jp.co.soramitsu.common_wallet.presentation.compose.states.BackupWalletState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioRoutes
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioViewModel

@AndroidEntryPoint
class CardsHubFragment : SoraBaseFragment<CardsHubViewModel>() {

    override val viewModel: CardsHubViewModel by viewModels()

    /** The Wallet tab's home is the Portfolio view (portfolio-view SPEC). */
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
        // "Where it lives" is a pushed screen: like other detail screens, without the bottom bar.
        val bar = activity as? BottomBarController ?: return
        if (destination == PortfolioRoutes.TOKEN) bar.hideBottomBar() else bar.showBottomBar()
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController,
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val nexusPortfolio =
                    viewModel.nexusPortfolio.collectAsStateWithLifecycle().value
                val sora2Portfolio =
                    viewModel.sora2Portfolio.collectAsStateWithLifecycle().value
                val nexusSend = viewModel.nexusSend.collectAsStateWithLifecycle().value
                // The dashboard has a snapshot once every wallet's balances have been read.
                val balancesLoaded = portfolioViewModel.model.collectAsStateWithLifecycle().value != null
                NexusSendDialog(
                    state = nexusSend,
                    onDismiss = viewModel::closeNexusSend,
                    onRecipient = viewModel::setNexusRecipient,
                    onAmount = viewModel::setNexusAmount,
                    onPrepare = viewModel::prepareNexusSend,
                    onConfirm = viewModel::confirmNexusSend,
                    onEdit = viewModel::editNexusSend,
                    onRecovery = viewModel::onBackupBannerClick,
                    onScan = ::scanNexusRecipient,
                )
                UpgradeSheets(
                    state = state,
                    balancesLoaded = balancesLoaded,
                    onBackUpNow = viewModel::onBackupBannerClick,
                )
                PortfolioHomeHost(
                    scrollState = scrollState,
                    navController = navController,
                    portfolio = portfolioViewModel,
                    hub = viewModel,
                    state = state,
                    sora2Portfolio = sora2Portfolio,
                    nexusPortfolio = nexusPortfolio,
                )
            }
        }
        composable(
            route = PortfolioRoutes.TOKEN,
            arguments = listOf(navArgument(PortfolioRoutes.TOKEN_ARG) { type = NavType.StringType }),
        ) { entry ->
            PortfolioWhereItLivesHost(
                tokenId = entry.arguments?.getString(PortfolioRoutes.TOKEN_ARG).orEmpty(),
                navController = navController,
                portfolio = portfolioViewModel,
                hub = viewModel,
                onScanNexusRecipient = ::scanNexusRecipient,
            )
        }
    }
}

/**
 * U9 (design spec 9.2): in read-only mode the home shows the chip and banner. Balances and
 * history stay visible; Send and Swap are off with their reason on their own screens.
 */
@Composable
internal fun ReadOnlyHomeBanner() {
    if (!rememberReadOnlyBrowsing()) return
    val context = LocalContext.current
    var learnMore by remember { mutableStateOf(false) }
    ReadOnlyBanner(
        modifier = Modifier.padding(bottom = 16.dp),
        onTryAgain = { WalletUpgradeRetry.launch(context) },
        onLearnMore = { learnMore = true },
    )
    if (learnMore) {
        ReadOnlyLearnMoreSheet(
            onTryAgain = {
                learnMore = false
                WalletUpgradeRetry.launch(context)
            },
            onClose = { learnMore = false },
        )
    }
}

/**
 * U11 (design spec 9.2, 9.1-7): once, after the upgrade succeeded, after the PIN and never in
 * read-only mode. Each tick is a real check: the selected account exists on this device, the
 * wallet was unlocked with the PIN, and the dashboard has read the balances. When some accounts
 * cannot sign on this phone, the sheet says how many can and how to repair the others.
 */
@Composable
private fun UpgradeSheets(state: CardsState, balancesLoaded: Boolean, onBackUpNow: () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(UpgradeWelcome.isPending(context)) }
    val unlocked by WalletSessionState.unlocked.collectAsStateWithLifecycle()
    val readOnly = rememberWalletReadOnly()
    // The account counts are read first, so the sheet never claims more than was checked.
    if (!pending || !unlocked || readOnly || state.accountAddress.isBlank() || state.accountCount == 0) return
    UpgradeCompleteSheet(
        checks = UpgradeCompleteChecks(
            accountsOnDevice = true,
            protectedByPin = true,
            balancesLoaded = balancesLoaded,
            needsBackup = state.cards.any { it is BackupWalletState },
            accountCount = state.accountCount,
            signingAccountCount = state.signingAccountCount,
        ),
        onBackUpNow = {
            UpgradeWelcome.markDone(context)
            pending = false
            onBackUpNow()
        },
        onClose = {
            UpgradeWelcome.markDone(context)
            pending = false
        },
    )
}
