/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.splash.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class SplashViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: SplashInteractor

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var splashViewModel: SplashViewModel

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log("Splash next screen true") } returns Unit
        whenever(coroutineManager.io).thenReturn(this.coroutineContext[CoroutineDispatcher]!!)
        splashViewModel = SplashViewModel(interactor, coroutineManager)
    }

    @Test
    fun `nextScreen with REGISTRATION_FINISHED`() = runTest {
        given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(true))
        given(interactor.getRegistrationState()).willReturn(OnboardingState.REGISTRATION_FINISHED)
        splashViewModel.nextScreen()
        advanceUntilIdle()
        val r = splashViewModel.showMainScreen.getOrAwaitValue()
        assertEquals(Unit, r)
    }

    @Test
    fun `nextScreen with INITIAL`() = runTest {
        val state = OnboardingState.INITIAL

        given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(true))
        given(interactor.getRegistrationState()).willReturn(state)

        splashViewModel.nextScreen()
        advanceUntilIdle()
        val r = splashViewModel.showOnBoardingScreen.getOrAwaitValue()
        assertEquals(OnboardingState.INITIAL, r)
    }
}
