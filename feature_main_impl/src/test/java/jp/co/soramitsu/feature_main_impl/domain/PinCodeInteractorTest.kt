/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PinCodeInteractorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var walletRepository: WalletRepository

    private lateinit var interactor: PinCodeInteractor
    private val pin = "123456"

    @Before
    fun setUp() = runTest {
        given(userRepository.retrievePin()).willReturn(pin)
        interactor = PinCodeInteractor(userRepository, credentialsRepository, walletRepository)
    }

    @Test
    fun `save pin called`() = runTest {
        assertEquals(Unit, interactor.savePin(pin))
        verify(userRepository).savePin(pin)
    }

    @Test
    fun `check pin called`() = runTest {
        given(userRepository.retrievePin()).willReturn(pin)
        val result = interactor.checkPin(pin)
        assertTrue(result)
    }

    @Test
    fun `check pin called with wrong pin`() = runTest {
        given(userRepository.retrievePin()).willReturn(pin)
        val result = interactor.checkPin("321445")
        assertFalse(result)
    }

    @Test
    fun `is pin set called`() = runTest {
        assertTrue(interactor.isCodeSet())
    }

    @Test
    fun `is pin set called without setting`() = runTest {
        given(userRepository.retrievePin()).willReturn("")

        assertFalse(interactor.isCodeSet())
    }

    @Test
    fun `reset user called`() = runTest {
        interactor.resetUser()
        verify(userRepository).clearUserData()
    }

    @Test
    fun `set biometry available called`() = runTest {
        interactor.setBiometryAvailable(true)
        verify(userRepository).setBiometryAvailable(true)

        interactor.setBiometryAvailable(false)
        verify(userRepository).setBiometryAvailable(false)
    }

    @Test
    fun `is biometry available called`() = runTest {
        given(userRepository.isBiometryAvailable()).willReturn(true)
        assertTrue(interactor.isBiometryAvailable())
    }

    @Test
    fun `set biometry enabled called`() = runTest {
        interactor.setBiometryEnabled(true)
        verify(userRepository).setBiometryEnabled(true)

        interactor.setBiometryEnabled(false)
        verify(userRepository).setBiometryEnabled(false)
    }

    @Test
    fun `is biometry enabled called`() = runTest {
        given(userRepository.isBiometryEnabled()).willReturn(true)
        assertTrue(interactor.isBiometryEnabled())
    }

    @Test
    fun `is pincode update needed called with old pin`() = runTest {
        val oldPin = "1234"
        given(userRepository.retrievePin()).willReturn(oldPin)
        assertTrue(interactor.isPincodeUpdateNeeded())
    }

    @Test
    fun `is pincode update needed called with empty pin`() = runTest {
        val emptyPin = ""
        given(userRepository.retrievePin()).willReturn(emptyPin)
        assertFalse(interactor.isPincodeUpdateNeeded())
    }

    @Test
    fun `is pincode update needed called with new pin`() = runTest {
        given(userRepository.retrievePin()).willReturn(pin)
        assertFalse(interactor.isPincodeUpdateNeeded())
    }

    @Test
    fun `needs migration called`() = runTest {
        val account = mock(SoraAccount::class.java)
        given(userRepository.getCurSoraAccount()).willReturn(account)
        given(userRepository.isMigrationFetched(account)).willReturn(true)

        interactor.needsMigration()

        verify(userRepository).needsMigration(account)
    }

    @Test
    fun `needs migration called with migration fetched false`() = runTest {
        val account = mock(SoraAccount::class.java)
        val irohaData = mock(IrohaData::class.java)
        val address = "address"
        val needsMigration = true
        given(irohaData.address).willReturn(address)
        given(userRepository.getCurSoraAccount()).willReturn(account)
        given(userRepository.isMigrationFetched(account)).willReturn(false)
        given(credentialsRepository.getIrohaData(account)).willReturn(irohaData)
        given(walletRepository.needsMigration(address)).willReturn(needsMigration)

        interactor.needsMigration()

        verify(userRepository).saveNeedsMigration(needsMigration, account)
        verify(userRepository).saveIsMigrationFetched(true, account)
        verify(userRepository).needsMigration(account)
    }
}
