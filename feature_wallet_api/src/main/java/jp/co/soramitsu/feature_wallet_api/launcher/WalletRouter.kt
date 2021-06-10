/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.launcher

import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import java.math.BigDecimal

interface WalletRouter {

    fun showTransactionDetailsFromList(
        myAccountId: String,
        peerId: String,
        soranetTransactionId: String,
        soranetBlockId: String,
        amount: BigDecimal,
        status: Transaction.Status,
        success: Boolean? = null,
        assetId: String,
        dateTime: Long,
        type: Transaction.Type,
        transactionFee: BigDecimal,
        totalAmount: BigDecimal
    )

    fun showValTransferAmount(recipientId: String, assetId: String, amount: BigDecimal)

    fun showValERCTransferAmount(address: String, amount: BigDecimal)

    fun returnToWalletFragment()

    fun popBackStackFragment()

    fun showTransactionConfirmation(peerId: String, fullName: String, partialAmount: BigDecimal, amount: BigDecimal, assetId: String, minerFee: BigDecimal, transactionFee: BigDecimal, transferType: TransferType)

    fun showContacts(assetId: String)

    fun showReceive(asset: ReceiveAssetModel)

    fun showFaq()

    fun showAssetSettings()

    fun showAssetList(mode: AssetListMode)

    fun showValWithdrawToErc(etherAddress: String, amount: BigDecimal)

    fun showWithdrawRetryFragment(soranetTransactionId: String, ethTransactionId: String, peerId: String, amount: BigDecimal, isTxFeeNeeded: Boolean)
}
