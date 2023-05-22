/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
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
import java.math.BigDecimal
import java.util.Date

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
        mockedUri,
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
            mockedUri,
            "peerId",
            "01 Jan 1970 00:00",
            1000000,
            "10.12 VAL",
            "$ 34.3",
            TransactionStatus.COMMITTED
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