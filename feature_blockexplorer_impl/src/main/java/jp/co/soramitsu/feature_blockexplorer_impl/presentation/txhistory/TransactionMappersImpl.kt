/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txhistory

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
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
