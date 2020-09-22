/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.reputation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class ReputationViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: MainInteractor
    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var timer: TimerWrapper
    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var numbersFormatter: NumbersFormatter

    private lateinit var reputationViewModel: ReputationViewModel

    @Before fun setUp() {
        reputationViewModel = ReputationViewModel(interactor, router, timer, resourceManager, numbersFormatter)
    }

    @Test fun `load empty reputation calls timer start`() {
        given(interactor.getReputationWithLastVotes(anyBoolean()))
            .willReturn(Single.just(Pair(Reputation(0, 0f, 0), BigDecimal(0))))

        given(timer.isStarted()).willReturn(false)
        given(timer.start(anyLong(), anyLong())).willReturn(Observable.just(1L))

        given(resourceManager.getString(anyInt())).willReturn("25:00")

        reputationViewModel.loadReputation(true)

        verify(interactor).getReputationWithLastVotes(anyBoolean())
        verify(timer).isStarted()
        verify(timer).start(anyLong(), anyLong())
    }

    @Test fun `load empty reputation doesn't start timer second time`() {
        given(interactor.getReputationWithLastVotes(anyBoolean()))
            .willReturn(Single.just(Pair(Reputation(0, 0f, 0), BigDecimal(0))))

        given(timer.isStarted()).willReturn(true)

        reputationViewModel.loadReputation(true)

        verify(interactor).getReputationWithLastVotes(anyBoolean())
        verify(timer).isStarted()
        verifyNoMoreInteractions(timer)
    }

    @Test fun `load reputation when it is not empty`() {
        val reputation = Reputation(1, 1f, 1)
        val lastVotesCount = BigDecimal(100)
        val lastVotesFormatted = "100"

        given(interactor.getReputationWithLastVotes(anyBoolean()))
            .willReturn(Single.just(Pair(reputation, lastVotesCount)))

        given(numbersFormatter.formatInteger(anyNonNull())).willReturn(lastVotesFormatted)

        reputationViewModel.loadReputation(true)

        reputationViewModel.reputationLiveData.observeForever {
            assertEquals(reputation, it)
        }

        reputationViewModel.lastVotesLiveData.observeForever {
            assertEquals(lastVotesFormatted, it)
        }

        verify(interactor).getReputationWithLastVotes(anyBoolean())
        verify(timer).cancel()
    }

    @Test fun `loadInformation() calls getReputationContent() from interactor`() {
        given(interactor.getReputationContent(true)).willReturn(Single.just(emptyList()))

        reputationViewModel.loadInformation(true)

        verify(interactor).getReputationContent(anyBoolean())
    }

    @Test fun `backButtonClick() calls router popBackStackFragment()`() {
        reputationViewModel.backButtonClick()

        verify(router).popBackStack()
    }
}