/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.userverification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Ignore("no tests")
class UserVerificationViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()
    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var router: MainRouter
    @Mock
    private lateinit var interactor: PinCodeInteractor

    private lateinit var userVerificationViewModel: UserVerificationViewModel

    @Before
    fun setUp() {
        userVerificationViewModel = UserVerificationViewModel(router, interactor)
    }
}
