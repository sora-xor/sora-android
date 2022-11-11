/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.args.BUNDLE_KEY
import jp.co.soramitsu.common.presentation.args.liquidityDetails
import jp.co.soramitsu.common.presentation.args.slippageTolerance
import jp.co.soramitsu.common.presentation.args.tokenFrom
import jp.co.soramitsu.common.presentation.args.tokenFromNullable
import jp.co.soramitsu.common.presentation.args.tokenTo
import jp.co.soramitsu.common.presentation.args.tokenToNullable
import jp.co.soramitsu.common.presentation.args.withArgs
import jp.co.soramitsu.common.presentation.compose.webview.title
import jp.co.soramitsu.common.presentation.compose.webview.url
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.util.action
import jp.co.soramitsu.feature_main_impl.presentation.version.UnsupportedVersionFragment
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import jp.co.soramitsu.feature_multiaccount_impl.util.address
import jp.co.soramitsu.feature_multiaccount_impl.util.addresses
import jp.co.soramitsu.feature_multiaccount_impl.util.type
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_select_node_impl.presentation.nodeAddress
import jp.co.soramitsu.feature_select_node_impl.presentation.nodeName
import jp.co.soramitsu.feature_select_node_impl.presentation.pinCodeChecked
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.AssetDetailsFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.AssetListFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.TransactionConfirmationFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.ContactsFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.details.ExtrinsicDetailsFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove.confirmation.RemoveLiquidityConfirmationFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swap.SwapFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation.SwapConfirmationFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.ReceiveFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferAmountFragment
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.substrate.models.WithDesired
import java.math.BigDecimal

class Navigator : MainRouter, WalletRouter, ReferralRouter, SelectNodeRouter {

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

    override fun showPinLengthInfo() {
        navController?.navigate(R.id.pincodeLengthInfoFragment)
    }

    override fun showFlexibleUpdateScreen() {
        navController?.navigate(R.id.updateFlexibleFragment)
    }

    override fun showPersonalDataEdition() {
        navController?.navigate(R.id.personalDataEditFragment)
    }

    override fun showSwitchAccount() {
        navController?.navigate(R.id.switchAccountFragment)
    }

    override fun popBackStack() {
        navController?.popBackStack()
    }

    override fun popBackStackToAccountList() {
        navController?.popBackStack(R.id.accountListFragment, false)
    }

    override fun popBackStackToAccountDetails() {
        navController?.popBackStack(R.id.accoundDetailsFragment, false)
    }

    override fun showTerms() {
        navController?.navigate(R.id.termsFragment)
    }

    override fun showSelectLanguage() {
        navController?.navigate(R.id.selectLanguageFragment)
    }

    override fun showFaq() {
        navController?.navigate(R.id.faqFragment)
    }

    override fun showAssetSettings() {
        navController?.navigate(R.id.assetSettingsFragment)
    }

    override fun showAssetList(mode: AssetListMode) {
        navController?.navigate(
            R.id.assetListFragment,
            AssetListFragment.createBundle(mode)
        )
    }

    override fun showSelectToken(mode: AssetListMode, hiddenAssetId: String?) {
        navController?.navigate(
            R.id.selectAssetForLiquidityFragment,
            AssetListFragment.createBundle(mode, hiddenAssetId)
        )
    }

    override fun showAssetDetails(assetId: String) {
        navController?.navigate(
            R.id.assetDetailsFragment,
            AssetDetailsFragment.createBundle(assetId)
        )
    }

    override fun showVotesHistory() {
        navController?.navigate(R.id.votesFragment)
    }

    override fun showContacts(assetId: String) {
        navController?.navigate(R.id.contactsFragment, ContactsFragment.createBundle(assetId))
    }

    override fun showReceive(asset: ReceiveAssetModel) {
        navController?.navigate(R.id.receiveFragment, ReceiveFragment.createBundle(asset))
    }

    override fun showValTransferAmount(recipientId: String, assetId: String, amount: BigDecimal) {
        navController?.navigate(
            R.id.transferAmountFragment,
            TransferAmountFragment.createBundleForValTransfer(recipientId, assetId, amount)
        )
    }

    override fun showValERCTransferAmount(address: String, amount: BigDecimal) {
        navController?.navigate(
            R.id.transferAmountFragment,
            TransferAmountFragment.createBundleForValErcTransfer(address, "", amount)
        )
    }

    override fun showValWithdrawToErc(etherAddress: String, amount: BigDecimal) {
        navController?.navigate(
            R.id.transferAmountFragment,
            TransferAmountFragment.createBundleForWithdraw(etherAddress, "", amount)
        )
    }

    override fun showWithdrawRetryFragment(
        soranetTransactionId: String,
        ethTransactionId: String,
        peerId: String,
        amount: BigDecimal,
        isTxFeeNeeded: Boolean
    ) {
        navController?.navigate(
            R.id.transferAmountFragment,
            TransferAmountFragment.createBundleForWithdrawRetry(
                soranetTransactionId,
                ethTransactionId,
                peerId,
                amount,
                isTxFeeNeeded
            )
        )
    }

    override fun showTransactionConfirmation(
        peerId: String,
        fullName: String,
        partialAmount: BigDecimal,
        amount: BigDecimal,
        assetId: String,
        minerFee: BigDecimal,
        transactionFee: BigDecimal,
        transferType: TransferType
    ) {
        navController?.navigate(
            R.id.transactionConfirmation,
            TransactionConfirmationFragment.createBundle(
                peerId,
                fullName,
                partialAmount,
                amount,
                assetId,
                minerFee,
                transactionFee,
                transferType
            )
        )
    }

    override fun showPolkaswapInfoFragment() {
        navController?.navigate(R.id.polkaswapInfoFragment)
    }

    override fun showPolkaswapDisclaimerFromSettings() {
        navController?.navigate(R.id.polkaDiscFragment)
    }

    override fun showSwapConfirmation(
        inputToken: Token,
        inputAmount: BigDecimal,
        outputToken: Token,
        outputAmount: BigDecimal,
        desired: WithDesired,
        details: SwapDetails,
        feeToken: Token,
        slippage: Float,
    ) {
        navController?.navigate(
            R.id.swapConfirmationFragment,
            SwapConfirmationFragment.createSwapData(
                inputToken,
                inputAmount,
                outputToken,
                outputAmount,
                desired,
                details,
                feeToken,
                slippage,
            )
        )
    }

    override fun showSwapTab(tokenFrom: Token, tokenTo: Token, amountFrom: BigDecimal) {
        navController?.navigate(
            R.id.polkaswap_nav_graph,
            SwapFragment.createSwapData(tokenFrom, tokenTo, amountFrom),
            NavOptions.Builder().setPopUpTo(R.id.walletFragment, false).build()
        )
    }

    override fun showAddLiquidity(tokenFrom: Token, tokenTo: Token?) {
        navController?.navigate(
            R.id.addLiquidityFragment,
            args = withArgs {
                this.tokenFrom = tokenFrom
                this.tokenToNullable = tokenTo
            },
            NavOptions.Builder().setPopUpTo(R.id.addLiquidityFragment, true).build()
        )
    }

    override fun showRemoveLiquidity(tokenFrom: Token, tokenTo: Token) {
        navController?.navigate(
            R.id.removeLiquidityFragment,
            args = withArgs {
                this.tokenFrom = tokenFrom
                this.tokenTo = tokenTo
            }
        )
    }

    override fun showRemoveLiquidityConfirmation(
        firstToken: Token,
        firstAmount: BigDecimal,
        secondToken: Token,
        secondAmount: BigDecimal,
        slippage: Float,
        percent: Double,
    ) {
        navController?.navigate(
            R.id.removeLiquidityConfirmationFragment,
            RemoveLiquidityConfirmationFragment.createBundle(
                firstToken,
                firstAmount,
                secondToken,
                secondAmount,
                slippage,
                percent,
            )
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

    override fun confirmAddLiquidity(
        tokenFrom: Token,
        tokenTo: Token,
        slippageTolerance: Float,
        liquidityDetails: LiquidityDetails
    ) {
        navController?.navigate(
            R.id.confirmAddLiquidityFragment,
            withArgs {
                this.tokenFrom = tokenFrom
                this.tokenTo = tokenTo
                this.slippageTolerance = slippageTolerance
                this.liquidityDetails = liquidityDetails
            }
        )
    }

    override fun showUnsupportedScreen(appUrl: String) {
        navController?.navigate(
            R.id.unsupportedVersionFragment,
            UnsupportedVersionFragment.createBundle(appUrl)
        )
    }

    override fun showAbout() {
        navController?.navigate(R.id.aboutFragment)
    }

    override fun showPrivacy() {
        navController?.navigate(R.id.privacyFragment)
    }

    override fun returnToWalletFragment() {
        navController?.popBackStack(R.id.walletFragment, false)
    }

    override fun returnToPolkaswap() {
        navController?.popBackStack(R.id.polkaswapFragment, false)
    }

    override fun showVerification() {
        navController?.navigate(
            R.id.userVerificationFragment,
            null,
            NavOptions.Builder().setPopUpTo(R.id.walletFragment, false).build()
        )
    }

    override fun currentDestinationIsPincode(): Boolean {
        return navController?.currentDestination != null && navController?.currentDestination!!.id == R.id.pincodeFragment
    }

    override fun currentDestinationIsUserVerification(): Boolean {
        return navController?.currentDestination != null && navController?.currentDestination!!.id == R.id.userVerificationFragment
    }

    override fun currentDestinationIsClaimFragment(): Boolean {
        return navController?.currentDestination != null && navController?.currentDestination!!.id == R.id.claimFragment
    }

    override fun currentDestinationIsPinCheckNeeded(): Boolean {
        return navController?.currentDestination != null && (navController?.currentDestination!!.id == R.id.backupFragment || navController?.currentDestination!!.id == R.id.backupJsonFragment)
    }

    override fun showTransactionDetails(
        txHash: String
    ) {
        val bundle = ExtrinsicDetailsFragment.createBundle(txHash)
        navController?.navigate(R.id.extrinsicDetails, bundle)
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
            NavOptions.Builder().setPopUpTo(R.id.walletFragment, false).build()
        )
    }

    override fun showAccountList() {
        navController?.navigate(
            R.id.export_account_nav_graph,
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
            R.id.accoundDetailsFragment,
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
}
