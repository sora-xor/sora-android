/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.mockk.every
import io.mockk.mockkObject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MainInteractorTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    private lateinit var interactor: MainInteractor

    private val soraAccount = SoraAccount("a", "n")

    @Before
    fun setUp() = runTest {
        given(userRepository.getCurSoraAccount()).willReturn(soraAccount)
        interactor = MainInteractor(
            userRepository,
            credentialsRepository,
        )
    }

    @Test
    fun `getMnemonic() function returns not empty mnemonic`() = runTest {
        val mnemonic = "test mnemonic"
        given(credentialsRepository.retrieveMnemonic(soraAccount)).willReturn(mnemonic)
        val mnemonicActual = interactor.getMnemonic()
        verify(credentialsRepository).retrieveMnemonic(soraAccount)
        assertEquals(mnemonic, mnemonicActual)
    }

    @Test
    fun `getInviteCode() calls userRepository getParentInviteCode()`() = runTest {
        val expectedResult = "parentInviteCode"
        given(userRepository.getParentInviteCode()).willReturn(expectedResult)

        assertEquals(expectedResult, interactor.getInviteCode())
        verify(userRepository).getParentInviteCode()
    }

    @Test
    fun `getAppVersion() calls userRepository getAppVersion()`() = runTest {
        val expectedResult = "version"
        mockkObject(OptionsProvider)
        every { OptionsProvider.CURRENT_VERSION_NAME } returns expectedResult
        assertEquals(expectedResult, interactor.getAppVersion())
    }
}
