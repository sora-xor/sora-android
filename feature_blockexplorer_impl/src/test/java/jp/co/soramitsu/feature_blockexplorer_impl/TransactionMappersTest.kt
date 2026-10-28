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

package jp.co.soramitsu.feature_blockexplorer_impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import java.math.BigDecimal
import java.util.Date
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatusDetail
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

    // --- audit A5: an Activity row never calls a refused transaction "submitted" --------------

    private fun genericRow(
        status: TransactionStatus,
        detail: TransactionStatusDetail?,
        ambiguous: Boolean,
    ): EventUiModel.EventTxUiModel.EventReferralProgramUiModel {
        given(dateTimeFormatter.formatTimeWithoutSeconds(Date(1000000))).willReturn("12:56")
        return transactionMappers.mapTransaction(
            Transaction.Sora2Submission(
                base = TransactionBase(
                    txHash = "0x" + "ab".repeat(32),
                    blockHash = null,
                    fee = BigDecimal.ZERO,
                    status = status,
                    timestamp = 1000000,
                    statusDetail = detail,
                ),
                networkId = "sora2",
                submissionIsAmbiguous = ambiguous,
                terminalBlockNumber = null,
            ),
            "address",
        ) as EventUiModel.EventTxUiModel.EventReferralProgramUiModel
    }

    @Test
    fun `a refused transaction known only by its hash is listed as not sent and failed`() {
        // Its journal row is still "submission unknown" until the validity window was scanned.
        val row = genericRow(
            status = TransactionStatus.REJECTED,
            detail = TransactionStatusDetail(TransactionStatusDetail.Kind.REFUSED_CANNOT_PAY_FEE),
            ambiguous = true,
        )

        assertThat(row.title).isEqualTo(R.string.tx_outcome_not_sent_title)
        assertThat(row.status).isEqualTo(TransactionStatus.REJECTED)
    }

    @Test
    fun `a waiting transaction known only by its hash is listed as submitted and pending`() {
        val row = genericRow(
            status = TransactionStatus.PENDING,
            detail = TransactionStatusDetail(TransactionStatusDetail.Kind.WAITING_FOR_CONFIRMATION),
            ambiguous = false,
        )

        assertThat(row.title).isEqualTo(R.string.wallet_transaction_submitted)
        assertThat(row.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `an uncertain submission known only by its hash is listed as status unknown`() {
        val row = genericRow(
            status = TransactionStatus.PENDING,
            detail = TransactionStatusDetail(TransactionStatusDetail.Kind.SUBMISSION_UNCERTAIN),
            ambiguous = true,
        )

        assertThat(row.title).isEqualTo(R.string.wallet_transaction_status_unknown)
    }
}
