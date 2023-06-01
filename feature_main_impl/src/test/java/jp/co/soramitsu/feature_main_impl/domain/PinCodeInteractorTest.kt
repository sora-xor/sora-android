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
    fun `clear account data called`() = runTest {
        val address = "address"

        interactor.clearAccountData(address)

        verify(userRepository).clearAccountData(address)
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
        interactor.fullLogout()
        verify(userRepository).fullLogout()
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

    @Test
    fun `saveTriesUsed called`() = runTest {
        interactor.saveTriesUsed(1)

        verify(userRepository).savePinTriesUsed(1)
    }

    @Test
    fun `saveTimerStartedTimestamp called`() = runTest {
        interactor.saveTimerStartedTimestamp(1)

        verify(userRepository).saveTimerStartedTimestamp(1)
    }

    @Test
    fun `resetTimerStartedTimestamp called`() = runTest {
        interactor.resetTimerStartedTimestamp()

        verify(userRepository).resetTimerStartedTimestamp()
    }

    @Test
    fun `resetTriesUsed called`() = runTest {
        interactor.resetTriesUsed()

        verify(userRepository).resetTriesUsed()
    }

    @Test
    fun `retrieveTriesUsed called`() = runTest {
        val triesUsed = 1
        given(userRepository.retrievePinTriesUsed()).willReturn(triesUsed)

        assertEquals(triesUsed, interactor.retrieveTriesUsed())
    }

    @Test
    fun `retrieveTimerStartedTimestamp called`() = runTest {
        val timestamp = 1L
        given(userRepository.retrieveTimerStartedTimestamp()).willReturn(timestamp)

        assertEquals(timestamp, interactor.retrieveTimerStartedTimestamp())
    }
}
