/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.voteshistory

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.TimeSectionInteractor
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyList
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
@Ignore("remove ignore when votes is implemented")
class VotesHistoryViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var interactor: MainInteractor

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var timeSectionInteractor: TimeSectionInteractor

    private lateinit var votesHistoryViewModel: VotesHistoryViewModel

    private val votesHistoryItem =
        VotesHistoryItem("message", '+', Date(), BigDecimal.TEN, "header")

    @Before
    fun setUp() {
        votesHistoryViewModel =
            VotesHistoryViewModel(interactor, router, timeSectionInteractor)
    }

    @Test
    fun `back button clicked`() {
        votesHistoryViewModel.backButtonClick()

        verify(router).popBackStack()
    }

    @Test
    fun `load history called`() {
        given(timeSectionInteractor.insertDateSections(anyList())).willReturn(
            mutableListOf(
                votesHistoryItem
            )
        )

        votesHistoryViewModel.loadHistory(true)

        verify(timeSectionInteractor).insertDateSections(anyList())

        votesHistoryViewModel.showEmptyLiveData.observeForever {
            assertEquals(false, it)
        }
        votesHistoryViewModel.votesHistoryLiveData.observeForever {
            assertEquals(mutableListOf(votesHistoryItem), it)
        }
    }

    @Test
    fun `load more history called`() {
        votesHistoryViewModel.votesHistoryLiveData.value = mutableListOf(votesHistoryItem)
        given(timeSectionInteractor.insertDateSections(anyList())).willReturn(
            mutableListOf(
                votesHistoryItem
            )
        )

        votesHistoryViewModel.loadMoreHistory()

        verify(timeSectionInteractor).insertDateSections(anyList())

        votesHistoryViewModel.showEmptyLiveData.observeForever {
            assertEquals(false, it)
        }
        votesHistoryViewModel.votesHistoryLiveData.observeForever {
            assertEquals(mutableListOf(votesHistoryItem, votesHistoryItem), it)
        }
    }
}