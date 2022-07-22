/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.personal_info

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.navigation.NavController
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import jp.co.soramitsu.feature_multiaccount_impl.presentation.personal_info.PersonalInfoViewModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PersonalInfoViewModelTest {

    private companion object {

        const val ACCOUNT_NAME = "accountName"
    }

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var router: MultiaccountRouter

    @Mock
    private lateinit var navController: NavController

    private lateinit var personalInfoViewModel: PersonalInfoViewModel

    @Before
    fun setUp() {
        personalInfoViewModel =
            PersonalInfoViewModel(
                router
            )
    }

    @Test
    fun `register account name EXPECT trigger alert event`() {
        personalInfoViewModel.register(ACCOUNT_NAME)

        assertNotNull(personalInfoViewModel.screenshotAlertDialogEvent)
    }

    @Test
    fun `screenshot alert ok clicked EXPECT show mnemonic`() = runTest {
        personalInfoViewModel.register(ACCOUNT_NAME)
        advanceUntilIdle()
        personalInfoViewModel.screenshotAlertOkClicked(navController)
        advanceUntilIdle()
        verify(router).showMnemonic(navController, ACCOUNT_NAME)
    }

    @Test
    fun `show term screen EXPECT navigate to term screen`() {
        personalInfoViewModel.showTermsScreen(navController)

        verify(router).showTermsScreen(navController)
    }

    @Test
    fun `show privacy screen EXPECT navigate to privacy screen`() {
        personalInfoViewModel.showPrivacyScreen(navController)

        verify(router).showPrivacyScreen(navController)
    }
}