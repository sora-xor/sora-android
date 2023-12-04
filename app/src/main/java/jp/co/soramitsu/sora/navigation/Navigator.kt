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

package jp.co.soramitsu.sora.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import jp.co.soramitsu.androidfoundation.intent.ShareUtil.openAppSettings
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.args.BUNDLE_KEY
import jp.co.soramitsu.common.presentation.args.address
import jp.co.soramitsu.common.presentation.args.addresses
import jp.co.soramitsu.common.presentation.args.isLaunchedFromSoraCard
import jp.co.soramitsu.common.presentation.args.tokenFromId
import jp.co.soramitsu.common.presentation.args.tokenFromNullable
import jp.co.soramitsu.common.presentation.args.tokenId
import jp.co.soramitsu.common.presentation.args.tokenToId
import jp.co.soramitsu.common.presentation.args.tokenToNullable
import jp.co.soramitsu.common.presentation.args.txHash
import jp.co.soramitsu.common.presentation.args.withArgs
import jp.co.soramitsu.common.presentation.compose.webview.title
import jp.co.soramitsu.common.presentation.compose.webview.url
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails.AssetDetailsFragment
import jp.co.soramitsu.feature_assets_impl.presentation.screens.receiverequest.QRCodeFlowFragment
import jp.co.soramitsu.feature_assets_impl.presentation.screens.send.TransferAmountFragment
import jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails.FarmDetailsFragment
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.util.action
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import jp.co.soramitsu.feature_multiaccount_impl.util.type
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityadd.LiquidityAddFragment
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityremove.LiquidityRemoveFragment
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.pooldetails.PoolDetailsFragment
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_select_node_impl.presentation.nodeAddress
import jp.co.soramitsu.feature_select_node_impl.presentation.nodeName
import jp.co.soramitsu.feature_select_node_impl.presentation.pinCodeChecked
import jp.co.soramitsu.feature_sora_card_impl.presentation.GetSoraCardFragment
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.R

class Navigator :
    MainRouter,
    WalletRouter,
    ReferralRouter,
    SelectNodeRouter,
    PolkaswapRouter,
    AssetsRouter {

    private var navController: NavController? = null

    override fun attachNavController(navController: NavController) {
        navController.setGraph(R.navigation.main_nav_graph)
        this.navController = navController
    }

    override fun detachNavController(navController: NavController) {
        if (this.navController == navController) {
            this.navController = null
        }
    }

    override fun showPin(action: PinCodeAction) {
        navController?.navigate(
            R.id.pincodeFragment,
            withArgs {
                this.action = action
            }
        )
    }

    override fun showPinForLogout(address: String) {
        navController?.navigate(
            R.id.pincodeFragment,
            args = withArgs {
                this.action = PinCodeAction.LOGOUT
                this.addresses = listOf(address)
            }
        )
    }

    override fun showPinForBackup(action: PinCodeAction, addresses: List<String>) {
        navController?.navigate(
            R.id.pincodeFragment,
            args = withArgs {
                this.action = action
                this.addresses = addresses
            }
        )
    }

    override fun showFlexibleUpdateScreen() {
        navController?.navigate(R.id.updateFlexibleFragment)
    }

    override fun popBackStack() {
        navController?.popBackStack()
    }

    override fun popBackStackToAccountList() {
        navController?.popBackStack(R.id.accountListFragment, false)
    }

    override fun popBackStackToAccountDetails() {
        navController?.popBackStack(R.id.accountDetailsFragment, false)
    }

    override fun showSelectLanguage() {
        if (BuildUtils.sdkAtLeast(33)) {
            navController?.context?.openAppSettings()
        } else {
            navController?.navigate(R.id.selectLanguageFragment)
        }
    }

    override fun showPoolDetails(ids: StringPair) {
        navController?.navigate(R.id.poolDetailsFragment, PoolDetailsFragment.createBundle(ids))
    }

    override fun showFarmDetails(ids: StringTriple) {
        navController?.navigate(R.id.farmDetailsFragment, FarmDetailsFragment.createBundle(ids))
    }

    override fun showPoolSettings() {
        navController?.navigate(R.id.fullPoolListFragment)
    }

    override fun showFullPoolsSettings() {
        navController?.navigate(R.id.fullPoolListSettingsFragment)
    }

    override fun showAssetSettings() {
        navController?.navigate(R.id.navAction_to_FullAssetListFragment)
    }

    override fun showFullAssetsSettings() {
        navController?.navigate(R.id.fullAssetsSettingsFragment)
    }

    override fun showAssetDetails(assetId: String) {
        navController?.navigate(
            R.id.assetDetailsFragment,
            AssetDetailsFragment.createBundle(assetId)
        )
    }

    override fun showTxList(assetId: String) {
        navController?.navigate(
            R.id.txListFragment,
            withArgs {
                tokenId = assetId
            }
        )
    }

    override fun showContacts(tokenId: String) {
        navController?.navigate(
            R.id.contactsFragment,
            withArgs {
                this.tokenId = tokenId
            }
        )
    }

    override fun showContactsFilled(tokenId: String, address: String) {
        navController?.navigate(
            R.id.contactsFragment,
            withArgs {
                this.tokenId = tokenId
                this.address = address
            }
        )
    }

    override fun showValTransferAmount(recipientId: String, assetId: String, initSendAmount: String?) {
        navController?.navigate(
            R.id.transferAmountFragment,
            TransferAmountFragment.createBundle(recipientId, assetId, initSendAmount)
        )
    }

    override fun showAddLiquidity(tokenFrom: String, tokenTo: String?) {
        navController?.navigate(
            R.id.addLiquidityFragment,
            LiquidityAddFragment.createBundle(tokenFrom, tokenTo),
        )
    }

    override fun showRemoveLiquidity(ids: StringPair) {
        navController?.navigate(
            R.id.liquidityRemoveFragment,
            LiquidityRemoveFragment.createBundle(ids),
        )
    }

    override fun returnToAddLiquidity(tokenFrom: Token?, tokenTo: Token?) {
        navController?.previousBackStackEntry?.savedStateHandle?.set(
            BUNDLE_KEY,
            withArgs {
                this.tokenFromNullable = tokenFrom
                this.tokenToNullable = tokenTo
            }
        )
        navController?.popBackStack()
    }

    override fun openQrCodeFlow(shouldNavigateToScannerDirectly: Boolean) {
        navController?.navigate(
            R.id.qrCodeFlow,
            QRCodeFlowFragment.createBundle(
                shouldNavigateToScanner = shouldNavigateToScannerDirectly,
            )
        )
    }

    override fun openEditCardsHub() {
        navController?.navigate(R.id.editCardsHub)
    }

    override fun showBuyCrypto(isLaunchedFromSoraCard: Boolean) {
        navController?.navigate(
            R.id.buyCryptoFragment,
            withArgs {
                this.isLaunchedFromSoraCard = isLaunchedFromSoraCard
            }
        )
    }

    override fun showInformation() {
        navController?.navigate(R.id.informationFragment)
    }

    override fun showDebugMenu() {
        navController?.navigate(R.id.debugMenuFragment)
    }

    override fun showLoginSecurity() {
        navController?.navigate(R.id.loginSecurityFragment)
    }

    override fun showSwap(tokenFromId: String?, tokenToId: String?, isLaunchedFromSoraCard: Boolean) {
        navController?.navigate(
            R.id.swapFragment,
            withArgs {
                this.tokenFromId = tokenFromId ?: ""
                this.tokenToId = tokenToId ?: ""
                this.isLaunchedFromSoraCard = isLaunchedFromSoraCard
            },
        )
    }

    override fun showAppSettings() {
        navController?.navigate(R.id.appSettingsFragment)
    }

    override fun returnToHubFragment() {
        navController?.popBackStack(R.id.cardsHubFragment, false)
    }

    override fun currentDestinationIsPincode(): Boolean {
        return navController?.currentDestination != null && navController?.currentDestination!!.id == R.id.pincodeFragment
    }

    override fun currentDestinationIsClaimFragment(): Boolean {
        return navController?.currentDestination != null && navController?.currentDestination!!.id == R.id.claimFragment
    }

    override fun currentDestinationIsPinCheckNeeded(): Boolean {
        return navController?.currentDestination != null && (navController?.currentDestination!!.id == R.id.backupFragment || navController?.currentDestination!!.id == R.id.backupJsonFragment)
    }

    override fun showProfile() {
        navController?.navigate(R.id.profile_nav_graph)
    }

    override fun showReferrals() {
        navController?.navigate(R.id.referral_nav_graph)
    }

    override fun showClaim() {
        navController?.navigate(
            R.id.claimFragment,
            null,
            NavOptions.Builder().setPopUpTo(R.id.cardsHubFragment, false).build()
        )
    }

    override fun showAccountList() {
        navController?.navigate(
            R.id.accountListFragment,
            null,
        )
    }

    override fun showExportPassphraseProtection(address: String) {
        navController?.navigate(
            R.id.exportProtectionFragment,
            withArgs {
                this.type = ExportProtectionViewModel.Type.PASSPHRASE
                this.address = address
            },
        )
    }

    override fun showExportSeedProtection(address: String) {
        navController?.navigate(
            R.id.exportProtectionFragment,
            withArgs {
                this.type = ExportProtectionViewModel.Type.SEED
                this.address = address
            },
        )
    }

    override fun showExportJSONProtection(addresses: List<String>) {
        navController?.navigate(
            R.id.exportProtectionFragment,
            withArgs {
                this.type = ExportProtectionViewModel.Type.JSON
                this.addresses = addresses as ArrayList<String>
                this.address = ""
            }
        )
    }

    override fun showAccountDetails(address: String) {
        navController?.navigate(
            R.id.accountDetailsFragment,
            withArgs {
                this.address = address
            },
        )
    }

    override fun showBackupPassphrase(address: String) {
        navController?.navigate(
            R.id.backupFragment,
            withArgs {
                this.type = ExportProtectionViewModel.Type.PASSPHRASE
                this.address = address
            },
        )
    }

    override fun showBackupSeed(address: String) {
        navController?.navigate(
            R.id.backupFragment,
            withArgs {
                this.type = ExportProtectionViewModel.Type.SEED
                this.address = address
            },
        )
    }

    override fun showBackupJson(addresses: List<String>) {
        navController?.navigate(
            R.id.backupJsonFragment,
            withArgs {
                this.addresses = addresses
            }
        )
    }

    override fun showWebView(title: String, url: String) {
        navController?.navigate(
            R.id.webViewFragment,
            withArgs {
                this.title = title
                this.url = url
            },
        )
    }

    override fun showGetSoraCard(shouldStartSignIn: Boolean, shouldStartSignUp: Boolean) {
        navController?.navigate(
            R.id.sora_card_nav_graph,
            GetSoraCardFragment.createBundle(
                shouldStartSignIn,
                shouldStartSignUp
            )
        )
    }

    override fun showSoraCardDetails() {
        navController?.navigate(R.id.soraCardDetailsFragment)
    }

    override fun showSelectNode() {
        navController?.navigate(
            R.id.select_node_nav_graph
        )
    }

    override fun showAddCustomNode() {
        navController?.navigate(
            R.id.nodeDetailsFragment
        )
    }

    override fun showEditNode(nodeName: String, nodeAddress: String) {
        navController?.navigate(
            R.id.nodeDetailsFragment,
            withArgs {
                this.nodeName = nodeName
                this.nodeAddress = nodeAddress
            }
        )
    }

    override fun returnFromPinCodeCheck() {
        navController?.previousBackStackEntry?.savedStateHandle?.set(
            BUNDLE_KEY,
            withArgs {
                this.pinCodeChecked = true
            }
        )
        navController?.popBackStack()
    }

    override fun popBackStackFragment() {
        navController?.popBackStack()
    }

    override fun showTxDetails(txHash: String, pop: Boolean) {
        navController?.navigate(
            R.id.txDetailsFragment,
            withArgs {
                this.txHash = txHash
            },
            if (pop) NavOptions.Builder().setPopUpTo(R.id.cardsHubFragment, false).build() else null,
        )
    }
}
