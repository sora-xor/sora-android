/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class TimeSectionInteractorTest {

    @Rule @JvmField var schedulersRule = RxSchedulersRule()

    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var dateTimeFormatter: DateTimeFormatter

    private lateinit var interactor: TimeSectionInteractor

    @Before fun setUp() {
        given(resourceManager.getString(R.string.common_yesterday)).willReturn("Yesterday")
        given(resourceManager.getString(R.string.common_today)).willReturn("Today")
        given(dateTimeFormatter.formatDate(anyNonNull(), anyString())).willReturn("01 January")

        interactor = TimeSectionInteractor(resourceManager, dateTimeFormatter)
    }

    @Test fun `insertDateSections called`() {
        val votesHistoryItems = mutableListOf(
            VotesHistoryItem(
                "message",
                '+',
                Date(100),
                BigDecimal.TEN
            )
        )

        val expectedResult = mutableListOf(
            VotesHistoryItem(header = "01 January")
        )

        expectedResult.addAll(votesHistoryItems)

        assertEquals(expectedResult, interactor.insertDateSections(votesHistoryItems))
    }
}