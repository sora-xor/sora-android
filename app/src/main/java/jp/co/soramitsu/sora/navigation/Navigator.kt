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
import jp.co.soramitsu.feature_main_impl.presentation.detail.DetailFragment
import jp.co.soramitsu.feature_main_impl.presentation.version.UnsupportedVersionFragment
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.TransactionConfirmationFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.details.TransactionDetailsFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferAmountFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.withdraw.WithdrawalAmountFragment
import jp.co.soramitsu.sora.R
import java.math.BigDecimal
import java.util.Date

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

    override fun showProjectDetails(projectId: String) {
        navController?.navigate(R.id.projectDetailFragment, DetailFragment.createBundle(projectId))
    }

    override fun showReputation() {
        navController?.navigate(R.id.reputationFragment)
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

    override fun showVotesHistory() {
        navController?.navigate(R.id.votesFragment)
    }

    override fun showContacts() {
        navController?.navigate(R.id.contactsFragment)
    }

    override fun showReceive() {
        navController?.navigate(R.id.receiveFragment)
    }

    override fun showTransferAmount(recipientId: String, fullName: String, amount: BigDecimal) {
        navController?.navigate(R.id.transferAmountFragment, TransferAmountFragment.createBundle(recipientId, fullName, amount))
    }

    override fun showTransactionConfirmation(recipientId: String, fullName: String, amount: Double, description: String, fee: Double) {
        navController?.navigate(R.id.transactionConfirmation, TransactionConfirmationFragment.createBundle(recipientId, fullName, amount, description, fee))
    }

    override fun showTransactionConfirmationViaEth(amount: Double, ethAddress: String, notaryAddress: String, feeAddress: String, fee: Double) {
        navController?.navigate(R.id.transactionConfirmation, TransactionConfirmationFragment.createBundleEth(amount, ethAddress, notaryAddress, feeAddress, fee))
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

    override fun showWithdrawalAmountViaEth(balance: String) {
        navController?.navigate(R.id.withdrawalAmountFragment, WithdrawalAmountFragment.createBundle(balance))
    }

    override fun showVerification() {
        navController?.navigate(R.id.userVerificationFragment, null, NavOptions.Builder().setPopUpTo(R.id.mainFragment, false).build())
    }

    override fun currentDestinationIsPincode(): Boolean {
        return navController?.currentDestination != null && navController?.currentDestination!!.id == R.id.pincodeFragment
    }

    override fun currentDestinationIsUserVerification(): Boolean {
        return navController?.currentDestination != null && navController?.currentDestination!!.id == R.id.userVerificationFragment
    }

    override fun showTransactionDetailsFromList(
        recipientId: String,
        recipientFullName: String,
        transactionId: String,
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double,
        totalAmount: Double
    ) {
        val bundle = TransactionDetailsFragment.createBundleFromList(
            recipientId, recipientFullName, transactionId, amount, status, dateTime, type, description, fee, totalAmount
        )
        navController?.navigate(R.id.transactionDetails, bundle)
    }

    override fun showTransferTransactionDetails(
        recipientId: String,
        recipientFullName: String,
        transactionId: String,
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double,
        totalAmount: Double
    ) {
        val bundle = TransactionDetailsFragment.createBundleForTransfer(
            recipientId, recipientFullName, transactionId, amount, status, dateTime, type, description, fee, totalAmount
        )
        navController?.navigate(R.id.transactionDetails, bundle)
    }

    override fun showWithdrawTransactionDetails(
        recipientId: String,
        recipientFullName: String,
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double,
        totalAmount: Double
    ) {
        val bundle = TransactionDetailsFragment.createBundleForWithdraw(
            recipientId, recipientFullName, amount, status, dateTime, type, description, fee, totalAmount
        )
        navController?.navigate(R.id.transactionDetails, bundle)
    }

    override fun showProfile() {
        navController?.navigate(R.id.profileFragment)
    }

    override fun popBackStackFragment() {
        navController?.popBackStack()
    }
}