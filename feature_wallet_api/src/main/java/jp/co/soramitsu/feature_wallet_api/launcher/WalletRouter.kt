/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.launcher

import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import java.math.BigDecimal
import java.util.Date

interface WalletRouter {

    fun showTransactionDetailsFromList(
        myAccountId: String,
        peerId: String,
        recipientFullName: String,
        ethTransactionId: String,
        soranetTransactionId: String,
        amount: BigDecimal,
        status: String,
        assetId: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        minerFee: BigDecimal,
        transactionFee: BigDecimal,
        totalAmount: BigDecimal
    )

    fun showValTransferAmount(recipientId: String, fullName: String, amount: BigDecimal)

    fun showValERCTransferAmount(address: String, amount: BigDecimal)

    fun returnToWalletFragment()

    fun popBackStackFragment()

    fun showTransactionConfirmation(peerId: String, fullName: String, partialAmount: BigDecimal, amount: BigDecimal, description: String, minerFee: BigDecimal, transactionFee: BigDecimal, transferType: TransferType)

    fun showContacts()

    fun showReceive()

    fun showFaq()

    fun showAssetSettings()

    fun showValWithdrawToErc(etherAddress: String, amount: BigDecimal)
}