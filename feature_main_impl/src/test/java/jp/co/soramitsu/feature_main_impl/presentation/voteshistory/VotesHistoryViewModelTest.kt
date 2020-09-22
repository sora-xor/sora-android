package jp.co.soramitsu.feature_main_impl.presentation.voteshistory

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.TimeSectionInteractor
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class VotesHistoryViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: MainInteractor
    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var preloader: WithPreloader
    @Mock private lateinit var timeSectionInteractor: TimeSectionInteractor

    private lateinit var votesHistoryViewModel: VotesHistoryViewModel

    private val VOTES_HISTORY_PER_PAGE = 50
    private val votesHistory = VotesHistory("message", "100", BigDecimal.TEN)
    private val votesHistoryItem = VotesHistoryItem("message", '+', Date(), BigDecimal.TEN, "header")

    @Before fun setUp() {
        votesHistoryViewModel = VotesHistoryViewModel(interactor, router, preloader, timeSectionInteractor)
    }

    @Test fun `back button clicked`() {
        votesHistoryViewModel.backButtonClick()

        verify(router).popBackStack()
    }

    @Test fun `load history called`() {
        given(interactor.getVotesHistory(true, 0, VOTES_HISTORY_PER_PAGE)).willReturn(Single.just(mutableListOf(votesHistory)))
        given(timeSectionInteractor.insertDateSections(anyNonNull())).willReturn(mutableListOf(votesHistoryItem))

        votesHistoryViewModel.loadHistory(true)

        verify(preloader).showPreloader()
        verify(preloader).hidePreloader()

        verify(timeSectionInteractor).insertDateSections(anyNonNull())

        votesHistoryViewModel.showEmptyLiveData.observeForever {
            assertEquals(false, it)
        }
        votesHistoryViewModel.votesHistoryLiveData.observeForever {
            assertEquals(mutableListOf(votesHistoryItem), it)
        }
    }

    @Test fun `load more history called`() {
        votesHistoryViewModel.votesHistoryLiveData.value = mutableListOf(votesHistoryItem)
        given(interactor.getVotesHistory(true, 0, VOTES_HISTORY_PER_PAGE)).willReturn(Single.just(mutableListOf(votesHistory)))
        given(timeSectionInteractor.insertDateSections(anyNonNull())).willReturn(mutableListOf(votesHistoryItem))

        votesHistoryViewModel.loadMoreHistory()

        verify(timeSectionInteractor).insertDateSections(anyNonNull())

        votesHistoryViewModel.showEmptyLiveData.observeForever {
            assertEquals(false, it)
        }
        votesHistoryViewModel.votesHistoryLiveData.observeForever {
            assertEquals(mutableListOf(votesHistoryItem, votesHistoryItem), it)
        }
    }
}