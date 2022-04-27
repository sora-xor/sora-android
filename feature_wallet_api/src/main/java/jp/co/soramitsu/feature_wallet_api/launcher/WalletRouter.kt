/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.launcher

import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import java.math.BigDecimal

interface WalletRouter {

    fun showTransactionDetails(
        txHash: String
    )

    fun showValTransferAmount(recipientId: String, assetId: String, amount: BigDecimal)

    fun showValERCTransferAmount(address: String, amount: BigDecimal)

    fun returnToWalletFragment()

    fun returnToPolkaswap()

    fun popBackStackFragment()

    fun showTransactionConfirmation(
        peerId: String,
        fullName: String,
        partialAmount: BigDecimal,
        amount: BigDecimal,
        assetId: String,
        minerFee: BigDecimal,
        transactionFee: BigDecimal,
        transferType: TransferType
    )

    fun showContacts(assetId: String)

    fun showReceive(asset: ReceiveAssetModel)

    fun showFaq()

    fun showAssetSettings()

    fun showAssetList(mode: AssetListMode)

    fun showSelectToken(mode: AssetListMode, hiddenAssetId: String? = null)

    fun showAssetDetails(assetId: String)

    fun showValWithdrawToErc(etherAddress: String, amount: BigDecimal)

    fun showWithdrawRetryFragment(
        soranetTransactionId: String,
        ethTransactionId: String,
        peerId: String,
        amount: BigDecimal,
        isTxFeeNeeded: Boolean
    )

    fun showPolkaswapInfoFragment()

    fun showSwapConfirmation(
        inputToken: Token,
        inputAmount: BigDecimal,
        outputToken: Token,
        outputAmount: BigDecimal,
        desired: WithDesired,
        details: SwapDetails,
        feeToken: Token,
        slippage: Float,
    )

    fun showSwapTab(tokenFrom: Token, tokenTo: Token, amountFrom: BigDecimal)

    fun showAddLiquidity(tokenFrom: Token, tokenTo: Token? = null)

    fun showRemoveLiquidity(tokenFromId: Token, tokenToId: Token)

    fun showRemoveLiquidityConfirmation(
        firstToken: Token,
        firstAmount: BigDecimal,
        secondToken: Token,
        secondAmount: BigDecimal,
        slippage: Float,
        percent: Double,
    )

    fun returnToAddLiquidity(tokenFrom: Token, tokenTo: Token? = null)

    fun confirmAddLiquidity(
        tokenFrom: Token,
        tokenTo: Token,
        slippageTolerance: Float,
        liquidityDetails: LiquidityDetails
    )
}
