/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import androidx.annotation.DrawableRes
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus

data class BasicTxDetailsState(
    val txHash: String,
    val blockHash: String?,
    val sender: String,
    val infos: List<BasicTxDetailsItem>,
    val txStatus: TransactionStatus,
    val time: String,
    val networkFee: String?,
    val networkFeeFiat: String?,
    @DrawableRes val txTypeIcon: Int,
    val txTypeTitle: String,
)

data class BasicTxDetailsItem(
    val title: String,
    val info: String,
)

internal val previewBasicTxDetailsItem = BasicTxDetailsState(
    "0x52bbf4fdd501cdffff315c02899f6391de9e68debc215f2ca8bfe40e0a66442b",
    "0x144eeee55c59918ff0a9dd4a782aefd16430170a0fd65635364a6ec652bb360b",
    "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
    listOf(BasicTxDetailsItem("qwe", "asd")),
    TransactionStatus.COMMITTED,
    "10 Nov. 2022 11:11",
    "123 XOR",
    "$ 123",
    R.drawable.ic_arrow_down_24,
    "Referrer set",
)
