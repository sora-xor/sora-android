/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ClaimViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: ClaimViewModel

    @Before
    fun setUp() {
        given(walletInteractor.observeMigrationStatus()).willReturn(flow { emit(MigrationStatus.SUCCESS) })
        viewModel = ClaimViewModel(router, walletInteractor, resourceManager)
    }

    @Test
    fun `check init`() = runBlockingTest {
        verify(router).popBackStackFragment()
    }

    @Test
    fun `contacts us click`() {
        val c = "support@sora.org"
        viewModel.contactsUsClicked()
        viewModel.openSendEmailEvent.observeForever {
            assertEquals(c, it)
        }
    }

    @Test
    fun `next click`() = runBlockingTest {
        given(walletInteractor.needsMigration()).willReturn(false)
        viewModel.checkMigrationIsAlreadyFinished()
        verify(router, times(2)).popBackStackFragment()
    }
}