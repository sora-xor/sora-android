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

package jp.co.soramitsu.feature_blockexplorer_impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import java.math.BigDecimal
import java.util.Date
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.feature_blockexplorer_impl.presentation.txhistory.TransactionMappersImpl
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class TransactionMappersTest {

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var dateTimeFormatter: DateTimeFormatter

    private val mockedUri = Mockito.mock(Uri::class.java)

    private lateinit var transactionMappers: TransactionMappersImpl

    private val token = Token(
        "token_id",
        "token name",
        "token symbol",
        18,
        true,
        DEFAULT_ICON_URI,
        null,
        null,
        null
    )

    private val transactions = mutableListOf(
        Transaction.Transfer(
            TransactionBase(
                "",
                "",
                BigDecimal.ZERO,
                TransactionStatus.COMMITTED,
                1000000,
            ),
            BigDecimal.ONE,
            "peerId",
            TransactionTransferType.INCOMING,
            token,
        )
    )

    private val transactionsWithHeaders = listOf(
        EventUiModel.EventTxUiModel.EventTransferInUiModel(
            "",
            1000000,
            TransactionStatus.COMMITTED,
            DEFAULT_ICON_URI,
            "peerId",
            "01 Jan 1970 00:00",
            "10.12 VAL",
            "$ 34.3",
        )
    )

    @Before
    fun setup() {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        transactionMappers =
            TransactionMappersImpl(
                resourceManager,
                NumbersFormatter(),
                dateTimeFormatter
            )
    }

    @Test
    fun `map transactions to SoraTransactions with headers`() {
        given(
            dateTimeFormatter.formatTimeWithoutSeconds(
                Date(transactions.first().base.timestamp),
            )
        ).willReturn("12:56")

        val result = transactions.map {
            transactionMappers.mapTransaction(it, "address") as EventUiModel.EventTxUiModel.EventTransferInUiModel
        }

        assertThat(transactionsWithHeaders.size).isEqualTo(result.size)
    }
}
