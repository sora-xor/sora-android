/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import com.google.common.truth.Truth.assertThat
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class TransactionMappersTest {

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var dateTimeFormatter: DateTimeFormatter

    private lateinit var transactionMappers: TransactionMappers

    private val transactions = mutableListOf(
        Transaction.Transfer(
            "",
            "",
            BigDecimal.ZERO,
            TransactionStatus.COMMITTED,
            1000000,
            true,
            BigDecimal.ONE,
            "peerId",
            TransactionTransferType.INCOMING,
            Token("token_id", "token name", "token symbol", 18, true, 0),
        )
    )

    private val transactionsWithHeaders = listOf(
        EventUiModel.EventTxUiModel.EventTransferInUiModel(
            "",
            0,
            "peerId",
            "01 Jan 1970 00:00",
            1000000,
            "10.12" to "VAL",
            false,
            true,
        )
    )

    @Before
    fun setup() {
        transactionMappers =
            TransactionMappers(resourceManager, numbersFormatter, dateTimeFormatter)
    }

    @Test
    fun `map transactions to SoraTransactions with headers`() {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt())).willReturn("10.12")
        given(
            dateTimeFormatter.formatTimeWithoutSeconds(
                Date(transactions.first().timestamp),
            )
        ).willReturn("12:56")
//        given(
//            dateTimeFormatter.formatDateTime(
//                Date(transactions.first().timestamp),
//            )
//        ).willReturn("01 Jan 1970 00:00")

        val result = transactions.map {
            transactionMappers.mapTransaction(it) as EventUiModel.EventTxUiModel.EventTransferInUiModel
        }

        assertThat(transactionsWithHeaders.size).isEqualTo(result.size)
    }
}