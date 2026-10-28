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
    val txTypeSubTitle: String? = null,
    val explorerUrl: String? = null,
    /**
     * Plain-language explanation of the status of a transaction sent from this device: that it is
     * still waiting and must not be sent again, or why it failed or was not sent.
     */
    val statusNote: String? = null,
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
    "Subtitle",
)
