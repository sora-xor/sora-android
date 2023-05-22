/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txhistory

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionMappers
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType

@Singleton
class TransactionMappersImpl @Inject constructor(
    private val resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    private val dateTimeFormatter: DateTimeFormatter,
) : TransactionMappers {

    override fun mapTransaction(tx: Transaction, curAddress: String): EventUiModel.EventTxUiModel =
        when (tx) {
            is Transaction.Transfer -> {
                if (tx.transferType == TransactionTransferType.INCOMING)
                    EventUiModel.EventTxUiModel.EventTransferInUiModel(
                        tx.base.txHash,
                        tx.token.iconUri(),
                        tx.peer,
                        dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                        tx.base.timestamp,
                        "+%s".format(
                            tx.token.printBalance(
                                tx.amount,
                                numbersFormatter,
                                AssetHolder.ACTIVITY_LIST_ROUNDING
                            )
                        ),
                        "~%s".format(tx.token.printFiat(tx.amount, numbersFormatter)),
                        tx.base.status,
                    ) else
                    EventUiModel.EventTxUiModel.EventTransferOutUiModel(
                        tx.base.txHash,
                        tx.token.iconUri(),
                        tx.peer,
                        dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                        tx.base.timestamp,
                        tx.token.printBalance(
                            tx.amount,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        ),
                        "~%s".format(tx.token.printFiat(tx.amount, numbersFormatter)),
                        tx.base.status,
                    )
            }
            is Transaction.Swap -> {
                EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel(
                    tx.base.txHash,
                    tx.tokenFrom.iconUri(),
                    tx.tokenTo.iconUri(),
                    "%s -> ".format(
                        tx.tokenFrom.printBalance(
                            tx.amountFrom,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        )
                    ),
                    tx.tokenTo.printBalance(
                        tx.amountTo,
                        numbersFormatter,
                        AssetHolder.ACTIVITY_LIST_ROUNDING
                    ),
                    "%s -> %s".format(
                        tx.tokenFrom.symbol,
                        tx.tokenTo.symbol
                    ),
                    "",
                    dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    tx.base.timestamp,
                    tx.base.status,
                )
            }
            is Transaction.Liquidity -> {
                val add = tx.type == TransactionLiquidityType.ADD
                EventUiModel.EventTxUiModel.EventLiquidityAddUiModel(
                    tx.base.txHash,
                    tx.base.timestamp,
                    tx.base.status,
                    dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    tx.token1.iconUri(),
                    tx.token2.iconUri(),
                    "%s%s / %s%s".format(
                        if (add) "" else "+",
                        tx.token1.printBalance(
                            tx.amount1,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        ),
                        if (add) "" else "+",
                        tx.token2.printBalance(
                            tx.amount2,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        )
                    ),
                    resourceManager.getString(R.string.activity_pool_title),
                    "%s / %s".format(
                        tx.token1.symbol,
                        tx.token2.symbol,
                    ),
                    "",
                    add
                )
            }
            is Transaction.ReferralSetReferrer -> {
                EventUiModel.EventTxUiModel.EventReferralProgramUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    title = if (tx.myReferrer) R.string.referrer_set else R.string.activity_referral_title,
                    description = tx.who,
                    plusAmount = false,
                    tokenIcon = tx.token.iconUri(),
                    dateTime = dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    amountFormatted = if (tx.myReferrer) "--" else "-1 ${resourceManager.getQuantityString(R.plurals.referral_invitations, 1)}",
                )
            }
            is Transaction.ReferralUnbond -> {
                EventUiModel.EventTxUiModel.EventReferralProgramUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    title = R.string.wallet_unbonded,
                    description = curAddress,
                    plusAmount = true,
                    tokenIcon = tx.token.iconUri(),
                    dateTime = dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    amountFormatted = "+%s".format(
                        tx.token.printBalance(
                            tx.amount,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        )
                    ),
                )
            }
            is Transaction.ReferralBond -> {
                EventUiModel.EventTxUiModel.EventReferralProgramUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    title = R.string.wallet_bonded,
                    description = curAddress,
                    plusAmount = false,
                    tokenIcon = tx.token.iconUri(),
                    dateTime = dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    amountFormatted = tx.token.printBalance(
                        tx.amount,
                        numbersFormatter,
                        AssetHolder.ACTIVITY_LIST_ROUNDING
                    ),
                )
            }
        }
}
