/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionLiquidityType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import java.util.Date
import javax.inject.Inject

class TransactionMappers @Inject constructor(
    val resourceManager: ResourceManager,
    val numbersFormatter: NumbersFormatter,
    val dateTimeFormatter: DateTimeFormatter,
) {

    fun mapTransaction(tx: Transaction): EventUiModel.EventTxUiModel =
        when (tx) {
            is Transaction.Transfer -> {
                if (tx.transferType == TransactionTransferType.INCOMING)
                    EventUiModel.EventTxUiModel.EventTransferInUiModel(
                        tx.base.txHash,
                        tx.token.icon,
                        tx.peer.truncateUserAddress(),
                        dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                        tx.base.timestamp,
                        Pair(
                            "+%s".format(
                                numbersFormatter.formatBigDecimal(tx.amount, AssetHolder.ROUNDING)
                            ),
                            tx.token.symbol,
                        ),
                        tx.base.status,
                    ) else
                    EventUiModel.EventTxUiModel.EventTransferOutUiModel(
                        tx.base.txHash,
                        tx.token.icon,
                        tx.peer.truncateUserAddress(),
                        dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                        tx.base.timestamp,
                        Pair(
                            "-%s".format(
                                numbersFormatter.formatBigDecimal(tx.amount, AssetHolder.ROUNDING)
                            ),
                            tx.token.symbol,
                        ),
                        tx.base.status,
                    )
            }
            is Transaction.Swap -> {
                EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel(
                    tx.base.txHash,
                    tx.tokenFrom.icon,
                    tx.tokenTo.icon,
                    Pair(
                        "-%s".format(
                            numbersFormatter.formatBigDecimal(
                                tx.amountFrom,
                                AssetHolder.ROUNDING
                            )
                        ),
                        tx.tokenFrom.symbol
                    ),
                    Pair(
                        "+%s".format(
                            numbersFormatter.formatBigDecimal(
                                tx.amountTo,
                                AssetHolder.ROUNDING
                            )
                        ),
                        tx.tokenTo.symbol
                    ),
                    dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    tx.base.timestamp,
                    tx.base.status,
                )
            }
            is Transaction.Liquidity -> {
                EventUiModel.EventTxUiModel.EventLiquidityAddUiModel(
                    tx.base.txHash,
                    tx.base.timestamp,
                    tx.base.status,
                    dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    tx.token1.icon,
                    tx.token2.icon,
                    Pair(
                        numbersFormatter.formatBigDecimal(
                            tx.amount1,
                            AssetHolder.ROUNDING
                        ),
                        tx.token1.symbol
                    ),
                    Pair(
                        numbersFormatter.formatBigDecimal(
                            tx.amount2,
                            AssetHolder.ROUNDING
                        ),
                        tx.token2.symbol
                    ),
                    tx.type == TransactionLiquidityType.ADD
                )
            }
            is Transaction.ReferralSetReferrer -> {
                EventUiModel.EventTxUiModel.EventReferralProgramUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    description = if (tx.myReferrer) R.string.history_referral_set_referrer else R.string.history_referral_set_referral,
                    plusAmount = tx.myReferrer,
                    tokenIcon = tx.token.icon,
                    dateTime = dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    amountFormatted = null,
                    referral = (
                        if (tx.myReferrer) resourceManager.getString(R.string.status_success) else "+1 ${
                        resourceManager.getString(
                            R.string.history_referral
                        )
                        }"
                        ) to (tx.who.truncateUserAddress()),
                )
            }
            is Transaction.ReferralUnbond -> {
                EventUiModel.EventTxUiModel.EventReferralProgramUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    description = R.string.history_referral_unbond_tokens,
                    plusAmount = true,
                    tokenIcon = tx.token.icon,
                    dateTime = dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    amountFormatted = Pair(
                        "+%s".format(
                            numbersFormatter.formatBigDecimal(tx.amount, AssetHolder.ROUNDING)
                        ),
                        tx.token.symbol,
                    ),
                    referral = null,
                )
            }
            is Transaction.ReferralBond -> {
                EventUiModel.EventTxUiModel.EventReferralProgramUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    description = R.string.history_referral_bond_tokens,
                    plusAmount = false,
                    tokenIcon = tx.token.icon,
                    dateTime = dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    amountFormatted = Pair(
                        "-%s".format(
                            numbersFormatter.formatBigDecimal(tx.amount, AssetHolder.ROUNDING)
                        ),
                        tx.token.symbol,
                    ),
                    referral = null,
                )
            }
        }
}
