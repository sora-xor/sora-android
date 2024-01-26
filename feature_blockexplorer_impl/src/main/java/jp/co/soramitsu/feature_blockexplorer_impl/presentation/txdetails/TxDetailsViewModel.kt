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

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txdetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import java.util.Date
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsItem
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsScreenState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.emptyTxDetailsState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TxDetailsViewModel @AssistedInject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val walletInteractor: WalletInteractor,
    private val transactionHistoryHandler: TransactionHistoryHandler,
    private val clipboardManager: BasicClipboardManager,
    private val resourceManager: ResourceManager,
    private val dateTimeFormatter: DateTimeFormatter,
    private val numbersFormatter: NumbersFormatter,
    @Assisted private val txHash: String,
) : BaseViewModel() {

    @AssistedFactory
    interface TxDetailsViewModelFactory {
        fun create(txHash: String): TxDetailsViewModel
    }

    private val _txDetailsScreenState = MutableStateFlow(emptyTxDetailsState)
    val txDetailsScreenState = _txDetailsScreenState.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.Small(),
            basic = BasicToolbarState(
                title = "",
                visibility = false,
                navIcon = null,
            ),
        )
        viewModelScope.launch {
            calcState()
        }
        transactionHistoryHandler.flowLocalTransactions()
            .drop(1)
            .catch { onError(it) }
            .onEach {
                calcState()
            }
            .launchIn(viewModelScope)
    }

    private suspend fun calcState() {
        val currentAddress = assetsInteractor.getCurSoraAccount().substrateAddress
        val feeToken = walletInteractor.getFeeToken()
        val transaction = transactionHistoryHandler.getTransaction(txHash)
        _txDetailsScreenState.value = when (transaction) {
            is Transaction.EthTransfer -> {
                TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        txHash = transaction.base.txHash,
                        blockHash = transaction.base.blockHash,
                        sender = currentAddress,
                        infos = listOf(
                            BasicTxDetailsItem(
                                title = resourceManager.getString(R.string.eth_tx_address),
                                info = transaction.sidechainAddress,
                            ),
                            BasicTxDetailsItem(
                                title = resourceManager.getString(R.string.eth_tx_hash),
                                info = transaction.requestHash,
                            ),
                        ),
                        txStatus = transaction.base.status,
                        time = dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        networkFee = feeToken.printBalance(
                            transaction.base.fee,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        ),
                        networkFeeFiat = feeToken.printFiat(transaction.base.fee, numbersFormatter),
                        txTypeIcon = R.drawable.ic_refresh_24,
                        txTypeTitle = resourceManager.getString(R.string.common_bridged),
                        txTypeSubTitle = "%s -> %s".format(
                            resourceManager.getString(R.string.asset_sora_fullname),
                            resourceManager.getString(R.string.asset_ether_fullname),
                        )
                    ),
                    amount1 = transaction.token.printBalance(
                        transaction.amount,
                        numbersFormatter,
                        AssetHolder.ROUNDING,
                    ),
                    amount2 = null,
                    amountFiat = "",
                    icon1 = transaction.token.iconUri(),
                    icon2 = null,
                    isAmountGreen = false,
                    txType = TxType.REFERRAL_TRANSFER,
                )
            }

            is Transaction.DemeterFarming -> {
                if (transaction.type == DemeterType.REWARD)
                    TxDetailsScreenState(
                        basicTxDetailsState = BasicTxDetailsState(
                            txHash = transaction.base.txHash,
                            blockHash = transaction.base.blockHash,
                            sender = currentAddress,
                            infos = emptyList(),
                            txStatus = transaction.base.status,
                            time = dateTimeFormatter.formatDate(
                                Date(transaction.base.timestamp),
                                DateTimeFormatter.DD_MMM_YYYY_HH_MM
                            ),
                            networkFee = feeToken.printBalance(
                                transaction.base.fee,
                                numbersFormatter,
                                AssetHolder.ROUNDING
                            ),
                            networkFeeFiat = feeToken.printFiat(transaction.base.fee, numbersFormatter),
                            txTypeIcon = R.drawable.ic_star,
                            txTypeTitle = resourceManager.getString(R.string.demeter_claimed_reward),
                        ),
                        amount1 = transaction.rewardToken.printBalance(
                            transaction.amount,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        ),
                        amount2 = null,
                        amountFiat = "",
                        icon1 = transaction.rewardToken.iconUri(),
                        icon2 = null,
                        isAmountGreen = true,
                        txType = TxType.REFERRAL_TRANSFER,
                    )
                else TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        txHash = transaction.base.txHash,
                        blockHash = transaction.base.blockHash,
                        sender = currentAddress,
                        infos = emptyList(),
                        txStatus = transaction.base.status,
                        time = dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        networkFee = feeToken.printBalance(
                            transaction.base.fee,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        ),
                        networkFeeFiat = feeToken.printFiat(transaction.base.fee, numbersFormatter),
                        txTypeIcon = if (transaction.type == DemeterType.STAKE) R.drawable.ic_new_arrow_up_24 else R.drawable.ic_new_arrow_down_24,
                        txTypeTitle = resourceManager.getString(if (transaction.type == DemeterType.STAKE) R.string.demeter_staked_liquidity else R.string.demeter_unstaked_liquidity),
                    ),
                    amount1 = numbersFormatter.formatBigDecimal(transaction.amount, AssetHolder.ROUNDING),
                    amount2 = "%s-%s".format(transaction.baseToken.symbol, transaction.targetToken.symbol),
                    amountFiat = "",
                    icon1 = transaction.baseToken.iconUri(),
                    icon2 = transaction.targetToken.iconUri(),
                    icon3 = transaction.rewardToken.iconUri(),
                    isAmountGreen = transaction.type == DemeterType.UNSTAKE,
                    txType = TxType.DEMETER,
                )
            }

            is Transaction.Transfer -> {
                var sender = transaction.peer
                var recipient = currentAddress
                var typeIcon = R.drawable.ic_new_arrow_down_24
                var typeTitle = R.string.common_received
                var amountPrefix = "+"

                if (transaction.transferType == TransactionTransferType.OUTGOING) {
                    sender = currentAddress
                    recipient = transaction.peer
                    typeIcon = R.drawable.ic_new_arrow_up_24
                    typeTitle = R.string.common_sent
                    amountPrefix = ""
                }
                TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        transaction.base.txHash,
                        transaction.base.blockHash,
                        sender,
                        listOf(
                            BasicTxDetailsItem(
                                resourceManager.getString(R.string.common_recipient),
                                recipient,
                            )
                        ),
                        transaction.base.status,
                        dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        if (transaction.transferType == TransactionTransferType.OUTGOING) feeToken.printBalance(
                            transaction.base.fee,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        ) else null,
                        if (transaction.transferType == TransactionTransferType.OUTGOING) feeToken.printFiat(
                            transaction.base.fee,
                            numbersFormatter
                        ) else null,
                        typeIcon,
                        resourceManager.getString(typeTitle),
                    ),
                    amount1 = "$amountPrefix${
                        transaction.token.printBalance(
                            transaction.amount,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        )
                    }",
                    amountFiat = "$amountPrefix${
                        transaction.token.printFiat(
                            transaction.amount,
                            numbersFormatter
                        )
                    }",
                    icon1 = transaction.token.iconUri(),
                    icon2 = null,
                    isAmountGreen = transaction.transferType == TransactionTransferType.INCOMING,
                    txType = TxType.REFERRAL_TRANSFER
                )
            }

            is Transaction.Swap -> {
                TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        transaction.base.txHash,
                        transaction.base.blockHash,
                        currentAddress,
                        emptyList(),
                        transaction.base.status,
                        dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        feeToken.printBalance(transaction.base.fee, numbersFormatter, AssetHolder.ROUNDING),
                        feeToken.printFiat(transaction.base.fee, numbersFormatter),
                        R.drawable.ic_refresh_24,
                        resourceManager.getString(R.string.polkaswap_swapped),
                    ),
                    amount1 = transaction.tokenFrom.printBalance(
                        transaction.amountFrom,
                        numbersFormatter,
                        AssetHolder.ROUNDING
                    ),
                    amount2 = transaction.tokenTo.printBalance(
                        transaction.amountTo,
                        numbersFormatter,
                        AssetHolder.ROUNDING
                    ),
                    amountFiat = "=${
                        transaction.tokenFrom.printFiat(
                            transaction.amountFrom,
                            numbersFormatter
                        )
                    }",
                    icon1 = transaction.tokenFrom.iconUri(),
                    icon2 = transaction.tokenTo.iconUri(),
                    isAmountGreen = false,
                    txType = TxType.SWAP
                )
            }

            is Transaction.Liquidity -> {
                var amountSign = ""
                var typeIcon = R.drawable.ic_new_arrow_up_24
                var typeTitle = resourceManager.getString(R.string.details_sent_to_pool)

                if (transaction.type == TransactionLiquidityType.WITHDRAW) {
                    typeIcon = R.drawable.ic_new_arrow_down_24
                    typeTitle = resourceManager.getString(R.string.details_receive_from_pool)
                    amountSign = "+"
                }

                TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        transaction.base.txHash,
                        transaction.base.blockHash,
                        currentAddress,
                        emptyList(),
                        transaction.base.status,
                        dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        feeToken.printBalance(transaction.base.fee, numbersFormatter, AssetHolder.ROUNDING),
                        feeToken.printFiat(transaction.base.fee, numbersFormatter),
                        typeIcon,
                        typeTitle,
                    ),
                    amount1 = "$amountSign${
                        transaction.token1.printBalance(
                            transaction.amount1,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        )
                    }",
                    amount2 = "$amountSign${
                        transaction.token2.printBalance(
                            transaction.amount2,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        )
                    }",
                    amountFiat = "$amountSign${
                        transaction.token1.printFiat(
                            transaction.amount1,
                            numbersFormatter
                        )
                    }",
                    icon1 = transaction.token1.iconUri(),
                    icon2 = transaction.token2.iconUri(),
                    isAmountGreen = transaction.type == TransactionLiquidityType.WITHDRAW,
                    txType = TxType.LIQUIDITY
                )
            }

            is Transaction.ReferralBond -> {
                TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        transaction.base.txHash,
                        transaction.base.blockHash,
                        currentAddress,
                        emptyList(),
                        transaction.base.status,
                        dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        feeToken.printBalance(transaction.base.fee, numbersFormatter, AssetHolder.ROUNDING),
                        feeToken.printFiat(transaction.base.fee, numbersFormatter),
                        R.drawable.ic_new_arrow_up_24,
                        resourceManager.getString(R.string.wallet_bonded),
                    ),
                    amount1 = transaction.token.printBalance(
                        transaction.amount,
                        numbersFormatter,
                        AssetHolder.ROUNDING
                    ),
                    amountFiat = transaction.token.printFiat(
                        transaction.amount,
                        numbersFormatter
                    ),
                    icon1 = transaction.token.iconUri(),
                    icon2 = null,
                    txType = TxType.REFERRAL_TRANSFER
                )
            }

            is Transaction.ReferralUnbond -> {
                TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        transaction.base.txHash,
                        transaction.base.blockHash,
                        currentAddress,
                        emptyList(),
                        transaction.base.status,
                        dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        feeToken.printBalance(transaction.base.fee, numbersFormatter, AssetHolder.ROUNDING),
                        feeToken.printFiat(transaction.base.fee, numbersFormatter),
                        R.drawable.ic_new_arrow_down_24,
                        resourceManager.getString(R.string.wallet_unbonded),
                    ),
                    amount1 = transaction.token.printBalance(
                        transaction.amount,
                        numbersFormatter,
                        AssetHolder.ROUNDING
                    ),
                    amountFiat = "+${
                        transaction.token.printFiat(
                            transaction.amount,
                            numbersFormatter
                        )
                    }",
                    isAmountGreen = true,
                    icon1 = transaction.token.iconUri(),
                    icon2 = null,
                    txType = TxType.REFERRAL_TRANSFER
                )
            }

            is Transaction.ReferralSetReferrer -> {
                var title = R.string.referrer_set
                var detailsItemTitle = R.string.history_referrer
                var networkFee: String? = null
                var networkFeeFiat: String? = null

                if (!transaction.myReferrer) {
                    title = R.string.activity_referral_title
                    detailsItemTitle = R.string.history_referral
                    networkFee = feeToken.printBalance(transaction.base.fee, numbersFormatter, AssetHolder.ROUNDING)
                    networkFeeFiat = feeToken.printFiat(transaction.base.fee, numbersFormatter)
                }

                TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        transaction.base.txHash,
                        transaction.base.blockHash,
                        currentAddress,
                        listOf(
                            BasicTxDetailsItem(
                                resourceManager.getString(detailsItemTitle),
                                transaction.who,
                            )
                        ),
                        transaction.base.status,
                        dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        networkFee,
                        networkFeeFiat,
                        R.drawable.ic_new_arrow_up_24,
                        resourceManager.getString(title),
                    ),
                    amount1 = if (transaction.myReferrer) "--" else "-1 ${
                        resourceManager.getQuantityString(
                            R.plurals.referral_invitations,
                            1
                        )
                    }",
                    amountFiat = transaction.token.printFiat(
                        BigDecimal.ZERO,
                        numbersFormatter
                    ),
                    icon1 = transaction.token.iconUri(),
                    icon2 = null,
                    txType = TxType.REFERRAL_TRANSFER
                )
            }

            is Transaction.AdarIncome -> {
                TxDetailsScreenState(
                    basicTxDetailsState = BasicTxDetailsState(
                        txHash = transaction.base.txHash,
                        blockHash = transaction.base.blockHash,
                        sender = transaction.peer,
                        infos = listOf(
                            BasicTxDetailsItem(
                                title = resourceManager.getString(R.string.common_recipient),
                                info = currentAddress,
                            ),
                        ),
                        txStatus = transaction.base.status,
                        time = dateTimeFormatter.formatDate(
                            Date(transaction.base.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY_HH_MM
                        ),
                        networkFee = feeToken.printBalance(
                            transaction.base.fee,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        ),
                        networkFeeFiat = feeToken.printFiat(transaction.base.fee, numbersFormatter),
                        txTypeIcon = R.drawable.ic_new_arrow_down_24,
                        txTypeTitle = resourceManager.getString(R.string.received_from_adar),
                        txTypeSubTitle = null,
                    ),
                    amount1 = "+%s".format(
                        transaction.token.printBalance(
                            transaction.amount,
                            numbersFormatter,
                            AssetHolder.ROUNDING,
                        )
                    ),
                    amount2 = null,
                    amountFiat = "",
                    icon1 = transaction.token.iconUri(),
                    icon2 = null,
                    isAmountGreen = true,
                    txType = TxType.REFERRAL_TRANSFER,
                )
            }

            null -> emptyTxDetailsState
        }
    }

    fun onCopyClicked(text: String) {
        clipboardManager.addToClipboard(text)
        copiedToast.trigger()
    }
}
