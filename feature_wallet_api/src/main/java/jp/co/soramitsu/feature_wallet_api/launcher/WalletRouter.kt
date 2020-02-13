package jp.co.soramitsu.feature_wallet_api.launcher

import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.math.BigDecimal
import java.util.Date

interface WalletRouter {

    fun showTransferTransactionDetails(
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
    )

    fun showWithdrawTransactionDetails(
        recipientId: String,
        recipientFullName: String,
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double,
        totalAmount: Double
    )

    fun showTransactionDetailsFromList(
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
    )

    fun showTransferAmount(recipientId: String, fullName: String, amount: BigDecimal)

    fun returnToWalletFragment()

    fun popBackStackFragment()

    fun showTransactionConfirmation(recipientId: String, fullName: String, amount: Double, description: String, fee: Double)

    fun showTransactionConfirmationViaEth(amount: Double, ethAddress: String, notaryAddress: String, feeAddress: String, fee: Double)

    fun showContacts()

    fun showReceive()

    fun showFaq()

    fun showWithdrawalAmountViaEth(balance: String)
}