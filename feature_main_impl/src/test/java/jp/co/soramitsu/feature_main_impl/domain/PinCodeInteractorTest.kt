/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PinCodeInteractorTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var ethereumRepository: EthereumRepository

    @Mock
    private lateinit var walletRepository: WalletRepository

    private lateinit var interactor: PinCodeInteractor
    private val pin = "1234"

    @Before
    fun setUp() {
        given(userRepository.retrievePin()).willReturn(pin)
        interactor = PinCodeInteractor(userRepository, credentialsRepository, walletRepository)
    }

    @Test
    fun `save pin called`() = runBlockingTest {
        assertEquals(Unit, interactor.savePin(pin))
        verify(userRepository).savePin(pin)
    }

    @Test
    fun `check pin called`() {
        given(userRepository.retrievePin()).willReturn(pin)
        val result = interactor.checkPin(pin)
        assertTrue(result)
    }

    @Test
    fun `check pin called with wrong pin`() {
        given(userRepository.retrievePin()).willReturn(pin)
        val result = interactor.checkPin("3214")
        assertFalse(result)
    }

    @Test
    fun `is pin set called`() {
        assertEquals(true, interactor.isCodeSet())
    }

    @Test
    fun `is pin set called without setting`() {
        given(userRepository.retrievePin()).willReturn("")

        assertEquals(false, interactor.isCodeSet())
    }

    @Test
    fun `reset user called`() = runBlockingTest {
        interactor.resetUser()
        verify(userRepository).clearUserData()
    }
}
