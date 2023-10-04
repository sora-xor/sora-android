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

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class MultiaccountInteractorTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var mockKRule = MockKRule(this)


    private lateinit var multiaccountInteractor: MultiaccountInteractor

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var credentialsRepository: CredentialsRepository

    @MockK
    lateinit var fileManager: FileManager

    @MockK
    lateinit var assetsInteractor: AssetsInteractor

    @MockK
    lateinit var runtimeManager: RuntimeManager

    private val account = mockk<SoraAccount>()
    private val uri = mockk<Uri>()

    @Before
    fun setup() {
        coEvery { credentialsRepository.isMnemonicValid(any()) } returns true
        coEvery { credentialsRepository.isRawSeedValid(any()) } returns true
        coEvery { userRepository.insertSoraAccount(any()) } returns Unit
        coEvery { userRepository.setCurSoraAccount(any()) } returns Unit
        coEvery { assetsInteractor.updateWhitelistBalances() } returns Unit
        coEvery { userRepository.saveRegistrationState(any()) } returns Unit
        coEvery { credentialsRepository.restoreUserCredentialsFromMnemonic(any(), any()) } returns account
        coEvery { credentialsRepository.restoreUserCredentialsFromRawSeed(any(), any()) } returns account
        coEvery { userRepository.saveNeedsMigration(any(), any()) } returns Unit
        coEvery { userRepository.saveIsMigrationFetched(any(), any()) } returns Unit
        coEvery { userRepository.updateAccountName(any(), any()) } returns Unit
        coEvery { fileManager.writeExternalCacheText(any(), any()) } returns uri
        multiaccountInteractor = MultiaccountInteractor(
            assetsInteractor,
            userRepository,
            credentialsRepository,
            fileManager,
            runtimeManager,
        )
    }

    @Test
    fun `is mnemonic valid called`() = runTest {
        val mnemonic = "mnemonic"
        multiaccountInteractor.isMnemonicValid(mnemonic)
        coVerify { credentialsRepository.isMnemonicValid(mnemonic) }
    }

    @Test
    fun `is raw seed valid called`() = runTest {
        val seed = "seed"
        multiaccountInteractor.isRawSeedValid(seed)
        coVerify { credentialsRepository.isRawSeedValid(seed) }
    }

    @Test
    fun `continueRecoverFlow is called`() = runTest {
        multiaccountInteractor.continueRecoverFlow(account)
        coVerify { userRepository.insertSoraAccount(account) }
        coVerify { userRepository.setCurSoraAccount(account) }
        coVerify { userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED) }
    }

    @Test
    fun `recoverSoraAccountFromMnemonic is called`() = runTest {
        val mnemonic = "mnemonic"
        val accountName = "accountName"
        multiaccountInteractor.recoverSoraAccountFromMnemonic(mnemonic, accountName)
        coVerify { credentialsRepository.restoreUserCredentialsFromMnemonic(mnemonic, accountName) }
    }

    @Test
    fun `recoverSoraAccountFromRawSeed is called`() = runTest {
        val rawSeed = "seed"
        val accountName = "accountName"
        multiaccountInteractor.recoverSoraAccountFromRawSeed(rawSeed, accountName)
        coVerify { credentialsRepository.restoreUserCredentialsFromRawSeed(rawSeed, accountName) }
    }

    @Test
    fun `createUser is called`() = runTest {
        multiaccountInteractor.createUser(account)
        coVerify { userRepository.insertSoraAccount(account) }
        coVerify { userRepository.setCurSoraAccount(account) }
        coVerify { userRepository.saveRegistrationState(OnboardingState.INITIAL) }
        coVerify { userRepository.saveNeedsMigration(false, account) }
        coVerify { userRepository.saveIsMigrationFetched(true, account) }
    }

    @Test
    fun `saveRegistrationStateFinished is called`() = runTest {
        multiaccountInteractor.saveRegistrationStateFinished()
        coVerify { userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED) }
    }

    @Test
    fun `update name is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        multiaccountInteractor.updateName(address, "newName")
        coVerify { userRepository.updateAccountName(soraAccount, "newName") }
    }

    @Test
    fun `get mnemonic is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        val mnemonic = "mnemonic"
        coEvery { credentialsRepository.retrieveMnemonic(soraAccount) } returns mnemonic
        val result = multiaccountInteractor.getMnemonic(address)
        assertEquals(mnemonic, result)
    }

    @Test
    fun `get seed is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        val seed = "seed"
        coEvery { credentialsRepository.retrieveSeed(soraAccount) } returns seed
        val result = multiaccountInteractor.getSeed(address)
        assertEquals(seed, result)
    }

    @Test
    fun `get keypair is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        val keypair = mockk<Sr25519Keypair>()
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns keypair
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
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        coEvery {
            credentialsRepository.generateJson(
                listOf(soraAccount),
                password
            )
        } returns expectedJson
        multiaccountInteractor.getJsonFileUri(listOf(address), password)
        coVerify { fileManager.writeExternalCacheText(expectedFileName, expectedJson) }
    }
}
