/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txhistory

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionMappers
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.genericTitle

@Singleton
class TransactionMappersImpl @Inject constructor(
    private val resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    private val dateTimeFormatter: DateTimeFormatter,
) : TransactionMappers {

    override fun mapTransaction(tx: Transaction, curAddress: String): EventUiModel.EventTxUiModel =
        when (tx) {
            is Transaction.Sora2Submission -> {
                EventUiModel.EventTxUiModel.EventReferralProgramUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    tokenIcon = DEFAULT_ICON_URI,
                    title = tx.genericTitle(),
                    description = tx.networkId.uppercase(),
                    plusAmount = false,
                    dateTime = dateTimeFormatter.formatTimeWithoutSeconds(
                        Date(tx.base.timestamp)
                    ),
                    amountFormatted = "",
                )
            }

            is Transaction.EthTransfer -> {
                EventUiModel.EventTxUiModel.EventEthTransfer(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    tokenUri = tx.token.iconUri(),
                    ethTokenUri = tx.ethToken.iconUri(),
                    dateTime = dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    amountFormatted = tx.token.printBalance(
                        tx.amount,
                        numbersFormatter,
                        AssetHolder.ACTIVITY_LIST_ROUNDING
                    ),
                    fiatFormatted = "~%s".format(tx.token.printFiat(tx.amount, numbersFormatter)),
                    requestHash = tx.requestHash,
                    sidechainAddress = tx.sidechainAddress,
                )
            }

            is Transaction.AdarIncome -> {
                EventUiModel.EventTxUiModel.EventAdarIncomeUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    tokenUri = tx.token.iconUri(),
                    amountFormatted = tx.token.printBalance(tx.amount, numbersFormatter, AssetHolder.ACTIVITY_LIST_ROUNDING),
                    peerAddress = tx.peer,
                )
            }

            is Transaction.DemeterFarming -> {
                if (tx.type == DemeterType.REWARD) EventUiModel.EventTxUiModel.EventDemeterRewardUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    token = tx.baseToken.iconUri(),
                    amountFormatted = tx.baseToken.printBalance(tx.amount, numbersFormatter, AssetHolder.ACTIVITY_LIST_ROUNDING),
                ) else EventUiModel.EventTxUiModel.EventDemeterStakeUiModel(
                    hash = tx.base.txHash,
                    timestamp = tx.base.timestamp,
                    status = tx.base.status,
                    stake = tx.type == DemeterType.STAKE,
                    tokenBase = tx.baseToken.iconUri(),
                    tokenTarget = tx.targetToken.iconUri(),
                    tokenReward = tx.rewardToken.iconUri(),
                    amountFormatted = numbersFormatter.formatBigDecimal(tx.amount, AssetHolder.ACTIVITY_LIST_ROUNDING),
                    symbols = "%s-%s".format(tx.baseToken.symbol, tx.targetToken.symbol),
                )
            }

            is Transaction.Transfer -> {
                if (tx.transferType == TransactionTransferType.INCOMING)
                    EventUiModel.EventTxUiModel.EventTransferInUiModel(
                        tx.base.txHash,
                        tx.base.timestamp,
                        tx.base.status,
                        tx.token.iconUri(),
                        tx.peer,
                        dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                        "+%s".format(
                            tx.token.printBalance(
                                tx.amount,
                                numbersFormatter,
                                AssetHolder.ACTIVITY_LIST_ROUNDING
                            )
                        ),
                        "~%s".format(tx.token.printFiat(tx.amount, numbersFormatter)),
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
                    tx.tokenFrom.symbol,
                    tx.tokenTo.symbol,
                    "",
                    dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    tx.base.timestamp,
                    tx.base.status,
                )
            }

            is Transaction.Liquidity -> {
                val add = tx.type == TransactionLiquidityType.ADD
                val sign = if (add) "" else "+"
                val amount1 =
                    "$sign${
                        tx.token1.printBalance(
                            tx.amount1,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        )
                    }"
                val amount2 =
                    "$sign${tx.token2.printBalance(tx.amount2, numbersFormatter, AssetHolder.ACTIVITY_LIST_ROUNDING)}"
                EventUiModel.EventTxUiModel.EventLiquidityAddUiModel(
                    tx.base.txHash,
                    tx.base.timestamp,
                    tx.base.status,
                    dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.base.timestamp)),
                    tx.token1.iconUri(),
                    tx.token2.iconUri(),
                    amount1,
                    amount2,
                    resourceManager.getString(R.string.activity_pool_title),
                    tx.token1.symbol,
                    tx.token2.symbol,
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
                    amountFormatted = if (tx.myReferrer) "--" else "-1 ${
                        resourceManager.getQuantityString(
                            R.plurals.referral_invitations,
                            1
                        )
                    }",
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
