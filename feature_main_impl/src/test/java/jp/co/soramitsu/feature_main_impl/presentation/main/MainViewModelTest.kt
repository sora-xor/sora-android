/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertNotNull
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

@RunWith(MockitoJUnitRunner::class)
class MainViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()
    @Rule
    @JvmField
    val schedulersRule = RxSchedulersRule()

    @Mock
    private lateinit var interactor: MainInteractor
    @Mock
    private lateinit var router: MainRouter
    @Mock
    private lateinit var preloader: WithPreloader

    private lateinit var mainViewModel: MainViewModel

    private val votesSubject = BehaviorSubject.createDefault(BigDecimal.ZERO)

    @Before
    fun setUp() {
        given(interactor.syncVotes()).willReturn(Completable.complete())

        mainViewModel = MainViewModel(interactor, router, preloader)
    }

//    @Test
//    fun `viewModel has been initialized and projects liveData values are not empty`() {
//        assertNotNull(mainViewModel.completedProjectsLiveData.value)
//        assertNotNull(mainViewModel.votedProjectsLiveData.value)
//        assertNotNull(mainViewModel.allProjectsLiveData.value)
//    }

    @Test
    fun `click on votes calls router showVotesScreen() function`() {
        mainViewModel.votesClick()

        verify(router).showVotesHistory()
    }
}