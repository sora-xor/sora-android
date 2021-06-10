/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.detail.referendum.DetailReferendumFragment
import jp.co.soramitsu.feature_main_impl.presentation.version.UnsupportedVersionFragment
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.AssetListFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.TransactionConfirmationFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.ContactsFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.details.TransactionDetailsFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.ReceiveFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferAmountFragment
import jp.co.soramitsu.sora.R
import java.math.BigDecimal

class Navigator : MainRouter, WalletRouter {

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
        val bundle = Bundle().apply {
            putSerializable(Const.PIN_CODE_ACTION, action)
        }
        navController?.navigate(R.id.pincodeFragment, bundle)
    }

    override fun showPersonalDataEdition() {
        navController?.navigate(R.id.personalDataEditFragment)
    }

    override fun popBackStack() {
        navController?.popBackStack()
    }

    override fun showTerms() {
        navController?.navigate(R.id.termsFragment)
    }

    override fun showReferendumDetails(referendumId: String) {
        navController?.navigate(R.id.referendumDetailFragment, DetailReferendumFragment.createBundle(referendumId))
    }

    override fun showPassphrase() {
        navController?.navigate(R.id.passphraseFragment)
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
        navController?.navigate(R.id.assetListFragment, AssetListFragment.createBundle(mode))
    }

    override fun showVotesHistory() {
        navController?.navigate(R.id.votesFragment)
    }

    override fun showReferenda() {
        navController?.navigate(R.id.mainFragment)
    }

    override fun showContacts(assetId: String) {
        navController?.navigate(R.id.contactsFragment, ContactsFragment.createBundle(assetId))
    }

    override fun showReceive(asset: ReceiveAssetModel) {
        navController?.navigate(R.id.receiveFragment, ReceiveFragment.createBundle(asset))
    }

    override fun showValTransferAmount(recipientId: String, assetId: String, amount: BigDecimal) {
        navController?.navigate(R.id.transferAmountFragment, TransferAmountFragment.createBundleForValTransfer(recipientId, assetId, amount))
    }

    override fun showValERCTransferAmount(address: String, amount: BigDecimal) {
        navController?.navigate(R.id.transferAmountFragment, TransferAmountFragment.createBundleForValErcTransfer(address, "", amount))
    }

    override fun showValWithdrawToErc(etherAddress: String, amount: BigDecimal) {
        navController?.navigate(R.id.transferAmountFragment, TransferAmountFragment.createBundleForWithdraw(etherAddress, "", amount))
    }

    override fun showWithdrawRetryFragment(soranetTransactionId: String, ethTransactionId: String, peerId: String, amount: BigDecimal, isTxFeeNeeded: Boolean) {
        navController?.navigate(R.id.transferAmountFragment, TransferAmountFragment.createBundleForWithdrawRetry(soranetTransactionId, ethTransactionId, peerId, amount, isTxFeeNeeded))
    }

    override fun showTransactionConfirmation(peerId: String, fullName: String, partialAmount: BigDecimal, amount: BigDecimal, assetId: String, minerFee: BigDecimal, transactionFee: BigDecimal, transferType: TransferType) {
        navController?.navigate(R.id.transactionConfirmation, TransactionConfirmationFragment.createBundle(peerId, fullName, partialAmount, amount, assetId, minerFee, transactionFee, transferType))
    }

    override fun showUnsupportedScreen(appUrl: String) {
        navController?.navigate(R.id.unsupportedVersionFragment, UnsupportedVersionFragment.createBundle(appUrl))
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

    override fun showVerification() {
        navController?.navigate(R.id.userVerificationFragment, null, NavOptions.Builder().setPopUpTo(R.id.walletFragment, false).build())
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

    override fun showTransactionDetailsFromList(
        myAccountId: String,
        peerId: String,
        soranetTransactionId: String,
        soranetBlockId: String,
        amount: BigDecimal,
        status: Transaction.Status,
        success: Boolean?,
        assetId: String,
        dateTime: Long,
        type: Transaction.Type,
        transactionFee: BigDecimal,
        totalAmount: BigDecimal
    ) {
        val bundle = TransactionDetailsFragment.createBundleFromList(
            myAccountId, peerId, soranetTransactionId, soranetBlockId, amount, status, success, assetId, dateTime, type, transactionFee, totalAmount
        )
        navController?.navigate(R.id.transactionDetails, bundle)
    }

    override fun showProfile() {
        navController?.navigate(R.id.profileFragment)
    }

    override fun showFriends() {
        navController?.navigate(R.id.inviteFragment)
    }

    override fun showClaim() {
        navController?.navigate(R.id.claimFragment, null, NavOptions.Builder().setPopUpTo(R.id.walletFragment, false).build())
    }

    override fun popBackStackFragment() {
        navController?.popBackStack()
    }
}
