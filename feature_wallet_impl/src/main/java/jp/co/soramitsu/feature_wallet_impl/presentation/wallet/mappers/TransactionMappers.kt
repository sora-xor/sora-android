/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.getInitials
import jp.co.soramitsu.common.util.ext.isErc20Address
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import java.util.Date
import javax.inject.Inject

class TransactionMappers @Inject constructor(
    val resourceManager: ResourceManager,
    val numbersFormatter: NumbersFormatter,
    val dateTimeFormatter: DateTimeFormatter
) {

    fun mapTransactionToSoraTransactionWithHeaders(
        transactions: List<Transaction>,
        myAccountId: String?,
        myEthAddress: String?
    ): List<Any> {
        val transactionsWithHeaders = mutableListOf<Any>()
        var lastDateString = ""

        myAccountId?.let {
            myEthAddress?.let {
                transactions.forEach {
                    val createdAt = Date(it.timestampInMillis)

                    val soraTransaction = with(it) {
                        val statusIcon = getIcon(it)
                        val title = getTitle(it, myAccountId, myEthAddress)
                        val details: String = getDetails(it, myAccountId, myEthAddress)
                        val dateString = dateTimeFormatter.formatDate(createdAt, DateTimeFormatter.DD_MMM_YYYY_HH_MM_SS)
                        val assetName = when (assetId) {
                            AssetHolder.SORA_XOR.id, AssetHolder.SORA_XOR_ERC_20.id -> resourceManager.getString(R.string.xor)
                            AssetHolder.SORA_VAL.id, AssetHolder.SORA_VAL_ERC_20.id -> resourceManager.getString(R.string.val_token)
                            AssetHolder.ETHER_ETH.id -> resourceManager.getString(R.string.transaction_eth_sign)
                            else -> resourceManager.getString(R.string.val_token)
                        }

                        val amountFormatted = "${numbersFormatter.formatBigDecimal(it.amount)} $assetName"

                        SoraTransaction(
                            it.soranetTxHash + it.ethTxHash,
                            Transaction.Type.OUTGOING != it.type && Transaction.Type.WITHDRAW != it.type,
                            statusIcon,
                            peerName.getInitials(),
                            title,
                            details,
                            dateString,
                            amountFormatted
                        )
                    }

                    val dayString = dateTimeFormatter.dateToDayWithoutCurrentYear(createdAt, resourceManager.getString(R.string.common_today), resourceManager.getString(R.string.common_yesterday))

                    if (lastDateString != dayString) {
                        lastDateString = dayString
                        transactionsWithHeaders.add(EventHeader(dayString))
                    }
                    transactionsWithHeaders.add(soraTransaction)
                }
            }
        }

        return transactionsWithHeaders
    }

    private fun getIcon(tx: Transaction): Int {
        return with(tx) {
            when (status) {
                Transaction.Status.REJECTED -> R.drawable.ic_error_30
                Transaction.Status.PENDING -> R.drawable.ic_pending_30
                else -> {
                    if (peerId?.isErc20Address() == true || details.isErc20Address()) {
                        if (assetId == AssetHolder.SORA_XOR_ERC_20.id) {
                            R.drawable.ic_xor_black_30
                        } else if (assetId == AssetHolder.SORA_VAL_ERC_20.id) {
                            R.drawable.ic_val_black_30
                        } else {
                            R.drawable.ic_eth_30
                        }
                    } else {
                        if (assetId == AssetHolder.SORA_XOR.id) {
                            R.drawable.ic_xor_red_30
                        } else {
                            R.drawable.ic_val_gold_30
                        }
                    }
                }
            }
        }
    }

    private fun getTitle(tx: Transaction, myAccountId: String, myEthAddress: String): String {
        return with(tx) {
            if (peerId == myAccountId) {
                resourceManager.getString(R.string.wallet_history_to_my_soranet_title)
            } else if (peerId?.toLowerCase() == myEthAddress.toLowerCase() || details.toLowerCase() == myEthAddress.toLowerCase()) {
                resourceManager.getString(R.string.wallet_history_to_my_ethereum_title)
            } else {
                processTitleByType(type, peerName, peerId, details, myEthAddress)
            }
        }
    }

    private fun processTitleByType(type: Transaction.Type, peerName: String, peerId: String?, details: String?, myEthAddress: String): String {
        return when (type) {
            Transaction.Type.REWARD -> {
                if (peerName.isNotEmpty()) {
                    peerName
                } else {
                    resourceManager.getString(R.string.wallet_history_reward_from_system)
                }
            }
            Transaction.Type.INCOMING -> {
                processPeerInformation(peerId, peerName, resourceManager.getString(R.string.wallet_history_from_ethereum_address_title),
                    peerName, resourceManager.getString(R.string.wallet_history_from_soranet_address_title))
            }
            Transaction.Type.OUTGOING -> {
                processPeerInformation(peerId, peerName, resourceManager.getString(R.string.wallet_history_to_ethereum_address),
                    peerName, resourceManager.getString(R.string.wallet_history_to_soranet_account))
            }
            Transaction.Type.DEPOSIT -> {
                resourceManager.getString(R.string.wallet_history_to_my_soranet_title)
            }
            Transaction.Type.WITHDRAW -> {
                if (peerId?.toLowerCase() == myEthAddress.toLowerCase() || details?.toLowerCase() == myEthAddress.toLowerCase()) {
                    resourceManager.getString(R.string.wallet_history_to_my_ethereum_title)
                } else {
                    resourceManager.getString(R.string.wallet_transfer_to_ethereum)
                }
            }
        }
    }

    private fun getDetails(tx: Transaction, myAccountId: String, myEthAddress: String): String {
        return with(tx) {
            if (peerId == myAccountId) {
                peerId!!
            } else if (peerId?.toLowerCase() == myEthAddress.toLowerCase() || details.toLowerCase() == myEthAddress.toLowerCase()) {
                myEthAddress
            } else {
                processDetailsByType(type, details, peerName, peerId, myAccountId)
            }
        }
    }

    private fun processDetailsByType(type: Transaction.Type, details: String, peerName: String, peerId: String?, myAccountId: String): String {
        return when (type) {
            Transaction.Type.REWARD -> {
                details
            }
            Transaction.Type.INCOMING, Transaction.Type.OUTGOING -> {
                processPeerInformation(peerId, peerName, peerId!!, details, peerId)
            }
            Transaction.Type.DEPOSIT -> {
                myAccountId
            }
            Transaction.Type.WITHDRAW -> {
                if (details.isErc20Address()) {
                    details
                } else {
                    peerId!!
                }
            }
        }
    }

    private fun processPeerInformation(peerId: String?, peerName: String, ethAddressReturnValue: String, peerNameReturnValue: String, elseReturnValue: String): String {
        return when {
            peerId?.isErc20Address() == true -> {
                ethAddressReturnValue
            }
            peerName.isNotEmpty() -> {
                peerNameReturnValue
            }
            else -> {
                elseReturnValue
            }
        }
    }
}