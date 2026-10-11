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

import io.mockk.coVerifyOrder
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_account_api.domain.model.WalletDraft
import jp.co.soramitsu.feature_account_api.domain.model.WalletRepairAvailableException
import org.junit.Assert.assertTrue
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    lateinit var walletRepository: WalletRepository

    @MockK
    lateinit var runtimeManager: RuntimeManager

    private val account = mockk<SoraAccount>()
    private val uri = mockk<Uri>()

    @Before
    fun setup() {
        coEvery { credentialsRepository.isMnemonicValid(any()) } returns true
        coEvery { credentialsRepository.isRawSeedValid(any()) } returns true
        coEvery { userRepository.insertSoraAccount(any(), any()) } returns Unit
        coEvery { userRepository.setCurSoraAccount(any()) } returns Unit
        coEvery { userRepository.saveRegistrationState(any()) } returns Unit
        coEvery { userRepository.saveNeedsMigration(any(), any()) } returns Unit
        coEvery { userRepository.saveIsMigrationFetched(any(), any()) } returns Unit
        coEvery { userRepository.updateAccountName(any(), any()) } returns Unit
        coEvery { fileManager.writeExternalCacheText(any(), any()) } returns uri
        multiaccountInteractor = MultiaccountInteractor(
            userRepository,
            credentialsRepository,
            fileManager,
            runtimeManager,
            walletRepository
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
        coVerify { userRepository.insertSoraAccount(account, false) }
        coVerify { userRepository.setCurSoraAccount(account) }
        coVerify { userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED) }
    }

    @Test
    fun `import from mnemonic stores keys and adds the account in one step`() = runTest {
        val mnemonic = "mnemonic"
        val accountName = "accountName"
        val draft = draft("cnImported", accountName)
        val imported = SoraAccount("cnImported", accountName)
        coEvery { credentialsRepository.draftFromMnemonic(mnemonic, accountName) } returns draft
        coEvery { userRepository.accountExists("cnImported") } returns false
        coEvery { credentialsRepository.commitDraft(draft) } returns imported

        assertEquals(imported, multiaccountInteractor.importFromMnemonic(mnemonic, accountName))

        coVerifyOrder {
            credentialsRepository.draftFromMnemonic(mnemonic, accountName)
            userRepository.accountExists("cnImported")
            credentialsRepository.commitDraft(draft)
            userRepository.insertSoraAccount(imported, false)
            userRepository.setCurSoraAccount(imported)
            userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
        }
        assertTrue(draft.isDestroyedForTest())
    }

    @Test
    fun `importing an existing account reports it and stores nothing`() = runTest {
        val draft = draft("cnExisting", "Existing")
        coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
        coEvery { userRepository.accountExists("cnExisting") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns emptySet()

        val error = runCatching {
            multiaccountInteractor.importFromMnemonic("mnemonic", "Existing")
        }.exceptionOrNull()

        assertEquals(
            ResponseCode.ACCOUNT_ALREADY_IMPORTED,
            (error as? SoraException)?.errorResponseCode,
        )
        coVerify(exactly = 0) { credentialsRepository.commitDraft(any()) }
        coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
    }

    @Test
    fun `importing the phrase of a read-only account completes its keys and makes it usable`() =
        runTest {
            val draft = draft("cnReadOnly", "Imported name")
            val stored = SoraAccount("cnReadOnly", "Imported name")
            val repaired = SoraAccount("cnReadOnly", "Partial")
            coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
            coEvery { userRepository.accountExists("cnReadOnly") } returns true
            coEvery { userRepository.unavailableWalletIds() } returns setOf("cnReadOnly")
            coEvery { credentialsRepository.commitDraft(draft) } returns stored
            coEvery { userRepository.completeUnavailableWallet("cnReadOnly") } returns repaired
            coEvery { userRepository.setCurSoraAccount(repaired) } returns Unit
            coEvery {
                userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
            } returns Unit

            assertEquals(
                repaired,
                multiaccountInteractor.importFromMnemonic("mnemonic", "Imported name"),
            )

            coVerifyOrder {
                // Add-only commit (it proves every stored value matches), then verification.
                credentialsRepository.commitDraft(draft)
                userRepository.completeUnavailableWallet("cnReadOnly")
                userRepository.setCurSoraAccount(repaired)
            }
            // The existing account row is kept as it is (no insert, no rename).
            coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
        }

    @Test
    fun `read-only account with conflicting stored keys is offered the repair, never removal`() =
        runTest {
            val draft = draft("cnUnreadable", "Name")
            coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
            coEvery { userRepository.accountExists("cnUnreadable") } returns true
            coEvery { userRepository.unavailableWalletIds() } returns setOf("cnUnreadable")
            coEvery { userRepository.retrievePin() } returns "135790"
            coEvery { credentialsRepository.commitDraft(draft) } throws
                SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)

            val offer = runCatching {
                multiaccountInteractor.importFromMnemonic("mnemonic", "Name")
            }.exceptionOrNull() as WalletRepairAvailableException

            assertEquals("cnUnreadable", offer.address)
            assertTrue(offer.accountExists)
            assertTrue(offer.pinRequired)
            // Nothing was written by the refused import.
            coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }
            coVerify(exactly = 0) { userRepository.completeUnavailableWallet(any()) }
            coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
            assertTrue(draft.isDestroyedForTest())
        }

    @Test
    fun `conflicting keys without an account row are offered the repair too`() = runTest {
        val draft = draft("cnOrphan", "Name")
        coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
        coEvery { userRepository.accountExists("cnOrphan") } returns false
        coEvery { userRepository.retrievePin() } returns ""
        coEvery { credentialsRepository.commitDraft(draft) } throws
            SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)

        val offer = runCatching {
            multiaccountInteractor.importFromMnemonic("mnemonic", "Name")
        }.exceptionOrNull() as WalletRepairAvailableException

        assertFalse(offer.accountExists)
        // No PIN is set on this phone, so there is none to ask for.
        assertFalse(offer.pinRequired)
        coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
    }

    @Test
    fun `repair needs the PIN and then replaces the keys of the read-only account in place`() =
        runTest {
            val draft = draft("cnBroken", "Typed name")
            val repaired = SoraAccount("cnBroken", "Kept name")
            coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
            coEvery { userRepository.accountExists("cnBroken") } returns true
            coEvery { userRepository.unavailableWalletIds() } returns setOf("cnBroken")
            coEvery { userRepository.retrievePin() } returns "135790"
            coEvery { credentialsRepository.repairWithDraft(draft) } returns
                SoraAccount("cnBroken", "Typed name")
            coEvery { userRepository.completeUnavailableWallet("cnBroken") } returns repaired
            coEvery { userRepository.setCurSoraAccount(repaired) } returns Unit
            coEvery {
                userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
            } returns Unit

            val wrongPin = runCatching {
                multiaccountInteractor.repairFromMnemonic("mnemonic", "Typed name", "000000")
            }.exceptionOrNull()
            assertEquals(
                ResponseCode.WRONG_PIN_CODE,
                (wrongPin as? SoraException)?.errorResponseCode,
            )
            // A wrong PIN stops before the phrase is even turned into keys.
            coVerify(exactly = 0) { credentialsRepository.draftFromMnemonic(any(), any()) }
            coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }

            assertEquals(
                repaired,
                multiaccountInteractor.repairFromMnemonic("mnemonic", "Typed name", "135790"),
            )
            coVerifyOrder {
                credentialsRepository.repairWithDraft(draft)
                userRepository.completeUnavailableWallet("cnBroken")
                userRepository.setCurSoraAccount(repaired)
            }
            // The account row is kept (no insert, no removal).
            coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
            coVerify(exactly = 0) { userRepository.createWalletDeletionPreview(any()) }
        }

    @Test
    fun `repair is refused for an account that can already sign`() = runTest {
        val draft = draft("cnFine", "Name")
        coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
        coEvery { userRepository.accountExists("cnFine") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns emptySet()
        coEvery { userRepository.retrievePin() } returns "135790"

        val error = runCatching {
            multiaccountInteractor.repairFromMnemonic("mnemonic", "Name", "135790")
        }.exceptionOrNull()

        assertEquals(
            ResponseCode.ACCOUNT_ALREADY_IMPORTED,
            (error as? SoraException)?.errorResponseCode,
        )
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }
    }

    @Test
    fun `import from raw seed stores keys and adds the account`() = runTest {
        val rawSeed = "seed"
        val accountName = "accountName"
        val draft = draft("cnSeed", accountName)
        val imported = SoraAccount("cnSeed", accountName)
        coEvery { credentialsRepository.draftFromRawSeed(rawSeed, accountName) } returns draft
        coEvery { userRepository.accountExists("cnSeed") } returns false
        coEvery { credentialsRepository.commitDraft(draft) } returns imported

        multiaccountInteractor.importFromRawSeed(rawSeed, accountName)

        coVerify { credentialsRepository.commitDraft(draft) }
        coVerify { userRepository.insertSoraAccount(imported, false) }
        coVerify { userRepository.saveNeedsMigration(false, imported) }
    }

    @Test
    fun `new wallet draft is committed only when the flow finishes`() = runTest {
        val draft = draft("cnNew", "New")
        val created = SoraAccount("cnNew", "New")
        coEvery { credentialsRepository.createDraft("New") } returns draft

        assertEquals(draft, multiaccountInteractor.createDraft("New"))
        coVerify(exactly = 0) { credentialsRepository.commitDraft(any()) }

        coEvery { userRepository.accountExists("cnNew") } returns false
        coEvery { credentialsRepository.commitDraft(draft) } returns created
        multiaccountInteractor.createUserFromDraft(draft)

        coVerifyOrder {
            credentialsRepository.commitDraft(draft)
            userRepository.insertSoraAccount(created, true)
            userRepository.setCurSoraAccount(created)
            userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
        }
    }

    @Test
    fun `import from an encrypted JSON file stores the key pair and adds the account`() = runTest {
        val draft = jsonDraft("cnFromJson", "Restored")
        val imported = SoraAccount("cnFromJson", "Restored")
        coEvery { credentialsRepository.draftsFromJson("{json}", "password", "Restored") } returns
            listOf(draft)
        coEvery { userRepository.accountExists("cnFromJson") } returns false
        coEvery { credentialsRepository.commitDraft(draft) } returns imported

        val result = multiaccountInteractor.importFromJson("{json}", "password", "Restored")

        assertEquals(listOf(imported), result.imported)
        assertEquals(0, result.alreadyHere)
        assertEquals(0, result.needRepair)
        coVerifyOrder {
            credentialsRepository.draftsFromJson("{json}", "password", "Restored")
            credentialsRepository.commitDraft(draft)
            userRepository.insertSoraAccount(imported, false)
            userRepository.setCurSoraAccount(imported)
            // A key pair has no phrase, so there is no SORA v1 identity to look up.
            userRepository.saveNeedsMigration(false, imported)
            userRepository.saveIsMigrationFetched(true, imported)
        }
        assertTrue(draft.isDestroyedForTest())
    }

    @Test
    fun `a JSON file of an account that can already sign reports it and stores nothing`() = runTest {
        val draft = jsonDraft("cnExisting", "Existing")
        coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns listOf(draft)
        coEvery { userRepository.accountExists("cnExisting") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns emptySet()

        val error = runCatching {
            multiaccountInteractor.importFromJson("{json}", "password", "Existing")
        }.exceptionOrNull()

        assertEquals(
            ResponseCode.ACCOUNT_ALREADY_IMPORTED,
            (error as? SoraException)?.errorResponseCode,
        )
        coVerify(exactly = 0) { credentialsRepository.commitDraft(any()) }
        assertTrue(draft.isDestroyedForTest())
    }

    @Test
    fun `a JSON file makes a read-only account usable again and keeps what was recorded for it`() =
        runTest {
            val draft = jsonDraft("cnReadOnly", "From file")
            val repaired = SoraAccount("cnReadOnly", "Partial")
            coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns listOf(draft)
            coEvery { userRepository.accountExists("cnReadOnly") } returns true
            coEvery { userRepository.unavailableWalletIds() } returns setOf("cnReadOnly")
            coEvery { credentialsRepository.commitDraft(draft) } returns
                SoraAccount("cnReadOnly", "From file")
            coEvery { userRepository.completeUnavailableWallet("cnReadOnly") } returns repaired

            val result = multiaccountInteractor.importFromJson("{json}", "password", "From file")

            assertEquals(listOf(repaired), result.imported)
            coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
            // The account row existed: its recorded SORA v1 state is not overwritten.
            coVerify(exactly = 0) { userRepository.saveNeedsMigration(any(), any()) }
        }

    @Test
    fun `a JSON file over conflicting stored keys offers the confirmed repair`() = runTest {
        val draft = jsonDraft("cnConflict", "Name")
        coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns listOf(draft)
        coEvery { userRepository.accountExists("cnConflict") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns setOf("cnConflict")
        coEvery { userRepository.retrievePin() } returns "135790"
        coEvery { credentialsRepository.commitDraft(draft) } throws
            SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)

        val offer = runCatching {
            multiaccountInteractor.importFromJson("{json}", "password", "Name")
        }.exceptionOrNull() as WalletRepairAvailableException

        assertTrue(offer.accountExists && offer.pinRequired)
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }

        // Confirming with a wrong PIN changes nothing; the right PIN repairs in place.
        val again = jsonDraft("cnConflict", "Name")
        val repaired = SoraAccount("cnConflict", "Kept name")
        coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns listOf(again)
        coEvery { credentialsRepository.repairWithDraft(again) } returns
            SoraAccount("cnConflict", "Name")
        coEvery { userRepository.completeUnavailableWallet("cnConflict") } returns repaired
        val wrongPin = runCatching {
            multiaccountInteractor.repairFromJson("{json}", "password", "Name", pin = "000000")
        }.exceptionOrNull()
        assertEquals(ResponseCode.WRONG_PIN_CODE, (wrongPin as? SoraException)?.errorResponseCode)
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }

        assertEquals(
            repaired,
            multiaccountInteractor.repairFromJson("{json}", "password", "Name", pin = "135790"),
        )
        coVerify(exactly = 1) { credentialsRepository.repairWithDraft(again) }
        assertTrue(again.isDestroyedForTest())
    }

    @Test
    fun `a batch JSON file adds what it can and reports the rest without changing them`() = runTest {
        val fresh = jsonDraft("cnFresh", "Fresh")
        val here = jsonDraft("cnHere", "Here")
        val conflict = jsonDraft("cnConflict", "Conflict")
        val imported = SoraAccount("cnFresh", "Fresh")
        coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns
            listOf(fresh, here, conflict)
        coEvery { userRepository.accountExists("cnFresh") } returns false
        coEvery { userRepository.accountExists("cnHere") } returns true
        coEvery { userRepository.accountExists("cnConflict") } returns false
        coEvery { userRepository.unavailableWalletIds() } returns emptySet()
        coEvery { userRepository.retrievePin() } returns ""
        coEvery { credentialsRepository.commitDraft(fresh) } returns imported
        coEvery { credentialsRepository.commitDraft(conflict) } throws
            SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)

        val result = multiaccountInteractor.importFromJson("{batch}", "password", "")

        assertEquals(listOf(imported), result.imported)
        assertEquals(1, result.alreadyHere)
        assertEquals(1, result.needRepair)
        // Nothing replaces stored keys from a batch file.
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }
        listOf(fresh, here, conflict).forEach { assertTrue(it.isDestroyedForTest()) }
    }

    @Test
    fun `a batch JSON file whose accounts are all here is reported as already imported`() = runTest {
        val first = jsonDraft("cnOne", "One")
        val second = jsonDraft("cnTwo", "Two")
        coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns
            listOf(first, second)
        coEvery { userRepository.accountExists(any()) } returns true
        coEvery { userRepository.unavailableWalletIds() } returns emptySet()

        val error = runCatching {
            multiaccountInteractor.importFromJson("{batch}", "password", "")
        }.exceptionOrNull()

        assertEquals(
            ResponseCode.ACCOUNT_ALREADY_IMPORTED,
            (error as? SoraException)?.errorResponseCode,
        )
    }

    @Test
    fun `the JSON of a Google backup restores only the account the backup names`() = runTest {
        val other = jsonDraft("cnOther", "Other")
        coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns listOf(other)

        val error = runCatching {
            multiaccountInteractor.importFromJson(
                json = "{json}",
                password = "password",
                accountName = "Named",
                expectedAddress = "cnNamed",
            )
        }.exceptionOrNull()

        assertEquals(ResponseCode.JSON_BACKUP_INVALID, (error as? SoraException)?.errorResponseCode)
        coVerify(exactly = 0) { credentialsRepository.commitDraft(any()) }
        assertTrue(other.isDestroyedForTest())
    }

    private fun jsonDraft(address: String, name: String) = WalletDraft(
        address = address,
        accountName = name,
        mnemonic = null,
        seedHex = "",
        keypair = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32)),
        source = WalletDraft.Source.JSON,
        wipeKeypair = { it.nonce.fill(1) },
    )

    private fun draft(address: String, name: String) = WalletDraft(
        address = address,
        accountName = name,
        mnemonic = "word ".repeat(12).trim(),
        seedHex = "00".repeat(32),
        keypair = Sr25519Keypair(ByteArray(64), ByteArray(32), ByteArray(32)),
        source = WalletDraft.Source.MNEMONIC,
        wipeKeypair = { it.nonce.fill(1) },
    )

    private fun WalletDraft.isDestroyedForTest(): Boolean =
        runCatching { keypair }.isFailure

    @Test
    fun `createUser is called`() = runTest {
        multiaccountInteractor.createUser(account)
        coVerify { userRepository.insertSoraAccount(account, true) }
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

    private fun material(words: List<String>, seedHex: String) = WalletBackupMaterial(
        canSign = true,
        phraseWords = words,
        seedHex = seedHex,
        storedPhraseDoesNotMatch = false,
        storedSeedDoesNotMatch = false,
    )

    @Test
    fun `the phrase shown is the stored phrase that re-derives the account`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        coEvery { credentialsRepository.retrieveBackupMaterial(soraAccount) } returns
            material(listOf("alpha", "beta", "gamma"), "")
        assertEquals("alpha beta gamma", multiaccountInteractor.getMnemonic(address))
        // The raw stored text is never what the screens show.
        coVerify(exactly = 0) { credentialsRepository.retrieveMnemonic(any()) }
    }

    @Test
    fun `no phrase or seed is offered when none stored re-derives the account`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        coEvery { credentialsRepository.retrieveBackupMaterial(soraAccount) } returns
            WalletBackupMaterial(
                canSign = true,
                phraseWords = emptyList(),
                seedHex = "",
                storedPhraseDoesNotMatch = true,
                storedSeedDoesNotMatch = false,
            )
        assertEquals("", multiaccountInteractor.getMnemonic(address))
        assertEquals("", multiaccountInteractor.getSeed(address))
        assertTrue(multiaccountInteractor.getBackupMaterial(address).onlyJsonExport)
        coVerify(exactly = 0) { credentialsRepository.retrieveSeed(any()) }
    }

    @Test
    fun `get seed is called`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        val seed = "seed"
        coEvery { credentialsRepository.retrieveBackupMaterial(soraAccount) } returns
            material(emptyList(), seed)
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
