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

package jp.co.soramitsu.feature_multiaccount_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MultiaccountInteractorTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()


    private lateinit var multiaccountInteractor: MultiaccountInteractor

    private val userRepository = mock(UserRepository::class.java)
    private val credentialsRepository = mock(CredentialsRepository::class.java)
    private val fileManager = mock(FileManager::class.java)
    private val assetsInteractor = mock(AssetsInteractor::class.java)
    private val runtimeManager = mock(RuntimeManager::class.java)

    @Before
    fun setup() {
        multiaccountInteractor = MultiaccountInteractor(assetsInteractor, userRepository, credentialsRepository, fileManager, runtimeManager)
    }

    @Test
    fun `is mnemonic valid called`() = runTest {
        val mnemonic = "mnemonic"

        multiaccountInteractor.isMnemonicValid(mnemonic)

        verify(credentialsRepository).isMnemonicValid(mnemonic)
    }

    @Test
    fun `is address valid called`() {
        val address = "address"
        given(runtimeManager.isAddressOk(address)).willReturn(true)

        assertTrue(multiaccountInteractor.isAddressValid(address))
    }

    @Test
    fun `is raw seed valid called`() = runTest {
        val seed = "seed"

        multiaccountInteractor.isRawSeedValid(seed)

        verify(credentialsRepository).isRawSeedValid(seed)
    }

    @Test
    fun `continueRecoverFlow is called`() = runTest {
        val account = mock(SoraAccount::class.java)
        multiaccountInteractor.continueRecoverFlow(account, true)

        verify(userRepository).insertSoraAccount(account)
        verify(userRepository).setCurSoraAccount(account)
        verify(userRepository).saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    @Test
    fun `recoverSoraAccountFromMnemonic is called`() = runTest {
        val mnemonic = "mnemonic"
        val accountName = "accountName"

        multiaccountInteractor.recoverSoraAccountFromMnemonic(mnemonic, accountName)

        verify(credentialsRepository).restoreUserCredentialsFromMnemonic(mnemonic, accountName)
    }

    @Test
    fun `recoverSoraAccountFromRawSeed is called`() = runTest {
        val rawSeed = "seed"
        val accountName = "accountName"

        multiaccountInteractor.recoverSoraAccountFromRawSeed(rawSeed, accountName)

        verify(credentialsRepository).restoreUserCredentialsFromRawSeed(rawSeed, accountName)
    }

    @Test
    fun `createUser is called`() = runTest {
        val account = mock(SoraAccount::class.java)

        multiaccountInteractor.createUser(account, true)

        verify(userRepository).insertSoraAccount(account)
        verify(userRepository).setCurSoraAccount(account)
        verify(userRepository).saveRegistrationState(OnboardingState.INITIAL)
        verify(userRepository).saveNeedsMigration(false, account)
        verify(userRepository).saveIsMigrationFetched(true, account)
    }

    @Test
    fun `saveRegistrationStateFinished is called`() = runTest {
        multiaccountInteractor.saveRegistrationStateFinished()

        verify(userRepository).saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    @Test
    fun `update name is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        given(userRepository.getSoraAccount(address)).willReturn(soraAccount)

        multiaccountInteractor.updateName(address, "newName")

        verify(userRepository).updateAccountName(soraAccount, "newName")
    }

    @Test
    fun `get mnemonic is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        given(userRepository.getSoraAccount(address)).willReturn(soraAccount)
        val mnemonic = "mnemonic"
        given(credentialsRepository.retrieveMnemonic(soraAccount)).willReturn(mnemonic)

        val result = multiaccountInteractor.getMnemonic(address)

        assertEquals(mnemonic, result)
    }

    @Test
    fun `get seed is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        given(userRepository.getSoraAccount(address)).willReturn(soraAccount)
        val seed = "seed"
        given(credentialsRepository.retrieveSeed(soraAccount)).willReturn(seed)

        val result = multiaccountInteractor.getSeed(address)

        assertEquals(seed, result)
    }

    @Test
    fun `get keypair is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        given(userRepository.getSoraAccount(address)).willReturn(soraAccount)
        val keypair = mock(Sr25519Keypair::class.java)
        given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(keypair)

        val result = multiaccountInteractor.getKeypair(address)

        assertEquals(keypair, result)
    }

    @Test
    fun `get json file uri is called with one address`() = runTest {
        val address = "address"
        val password = "address"
        val soraAccount = SoraAccount("address", "name")
        val expectedFileName = "address.json"
        val expectedJson = "{JSON}"
        given(userRepository.getSoraAccount(address)).willReturn(soraAccount)
        given(credentialsRepository.generateJson(listOf(soraAccount), password)).willReturn(expectedJson)


        multiaccountInteractor.getJsonFileUri(listOf(address), password)

        verify(fileManager).writeExternalCacheText(expectedFileName, expectedJson)
    }
}
