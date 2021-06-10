/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PersonalDataEditViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()
    @Rule
    @JvmField
    val schedulersRule = RxSchedulersRule()

    @Mock
    lateinit var interactor: MainInteractor
    @Mock
    lateinit var router: MainRouter
    @Mock
    lateinit var progress: WithProgress

    private lateinit var personalDataEditViewModel: PersonalDataEditViewModel

    private val accountName = "AccountName"

    @Before
    fun setUp() {
        given(interactor.getAccountName()).willReturn(Single.just(accountName))
        given(interactor.saveAccountName(accountName)).willReturn(Completable.complete())
        personalDataEditViewModel = PersonalDataEditViewModel(interactor, router, progress)
    }

    @Test
    fun `back button pressed`() {
        personalDataEditViewModel.backPressed()

        verify(router).popBackStack()
    }

    @Test
    fun `account name input changed`() {
        personalDataEditViewModel.accountNameChanged("")

        personalDataEditViewModel.nextButtonEnableLiveData.value?.let {
            Assert.assertFalse(it)
        }

        personalDataEditViewModel.accountNameChanged(accountName)

        personalDataEditViewModel.nextButtonEnableLiveData.value?.let {
            Assert.assertTrue(it)
        }
    }

    @Test
    fun `account name input done`() {
        personalDataEditViewModel.saveData(accountName)

        verify(router).popBackStack()
    }
}
