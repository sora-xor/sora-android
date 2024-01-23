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
