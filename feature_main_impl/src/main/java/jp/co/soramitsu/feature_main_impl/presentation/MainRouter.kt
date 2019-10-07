/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.net.URL
import java.util.Date

interface MainRouter {

    fun showPin(action: PinCodeAction)

    fun showMain()

    fun showInvite()

    fun showPersonalDataEdition()

    fun hidePinCode()

    fun popBackStackFragment()

    fun showBrowser(link: URL)

    fun showBrowser(link: String)

    fun showTermsFragment()

    fun showProjectDetailed(projectId: String)

    fun showBottomView()

    fun hideBottomView()

    fun showPassphrase()

    fun showPinCheckToPassphrase()

    fun showReputationScreen()

    fun showFaq()

    fun showConversion()

    fun showVotesScreen()

    fun showContacts(balance: String)

    fun showTransferAmount(
        accountId: String,
        fullName: String,
        amount: String,
        description: String,
        balance: String
    )

    fun showWithdrawalAmountViaEth(balance: String)

    fun showTransactionConfirmation(
        accountId: String,
        fullName: String,
        amount: Double,
        description: String,
        fee: Double
    )

    fun showTransactionConfirmationViaEth(
        amount: Double,
        ethAddress: String,
        notaryAddress: String,
        feeAddress: String,
        fee: Double
    )

    fun showTransactionDetails(
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double
    )

    fun showTransactionDetailsFromList(
        recipientId: String,
        balance: String,
        recipient: String,
        transactionId: String,
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double
    )

    fun showTransactionDetails(
        recipient: String,
        transactionId: String,
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double
    )

    fun showReceiveAmount()

    fun closeApp()

    fun restartApp()

    fun returnToWalletFragment()

    fun showUnsupportedScreen(appUrl: String)

    fun showPrivacy()

    fun showAbout()
}