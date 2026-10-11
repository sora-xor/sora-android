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
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.feature_account_api.domain.model.RepairPinLockedException
import jp.co.soramitsu.feature_account_api.domain.model.RetainedWallet
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

    /** The PIN screen's persistent counter and lock timer, as the repository stores them. */
    private var pinTries = 0
    private var pinLockStartedAt = 0L

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
        // Nothing is missing unless a test says so, and the PIN screen's counter starts clean.
        coEvery { credentialsRepository.addMissingBackup(any()) } returns false
        coEvery { userRepository.refreshVerifiedWallet(any()) } returns Unit
        coEvery { userRepository.retrievePinTriesUsed() } answers { pinTries }
        coEvery { userRepository.savePinTriesUsed(any()) } answers { pinTries = firstArg() }
        coEvery { userRepository.resetTriesUsed() } answers { pinTries = 0 }
        coEvery { userRepository.retrieveTimerStartedTimestamp() } answers { pinLockStartedAt }
        coEvery { userRepository.saveTimerStartedTimestamp(any()) } answers { pinLockStartedAt = firstArg() }
        coEvery { userRepository.resetTimerStartedTimestamp() } answers { pinLockStartedAt = 0 }
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
    fun `the backup of a read-only account stays restorable and only one that signs is left out`() =
        runTest {
            coEvery { userRepository.accountExists("cnNotHere") } returns false
            coEvery { userRepository.accountExists("cnReadOnly") } returns true
            coEvery { userRepository.accountExists("cnSigns") } returns true
            coEvery { userRepository.unavailableWalletIds() } returns setOf("cnReadOnly")

            val restores = multiaccountInteractor.backupRestores(
                listOf("cnSigns", "cnReadOnly", "cnNotHere")
            )

            // The read-only account is listed on this phone, and its backup is still offered:
            // restoring it is how it becomes usable again without being removed.
            assertEquals(
                mapOf(
                    "cnReadOnly" to MultiaccountInteractor.BackupRestore.REPAIRS_READ_ONLY_ACCOUNT,
                    "cnNotHere" to MultiaccountInteractor.BackupRestore.ADDS_ACCOUNT,
                ),
                restores,
            )
            // In the order of the backups.
            assertEquals(listOf("cnReadOnly", "cnNotHere"), restores.keys.toList())
            // Looking at the list writes nothing.
            coVerify(exactly = 0) { credentialsRepository.commitDraft(any()) }
            coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }
            coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
            coVerify(exactly = 0) { userRepository.completeUnavailableWallet(any()) }
        }

    @Test
    fun `once a read-only account is repaired its backup is no longer offered`() = runTest {
        coEvery { userRepository.accountExists("cnReadOnly") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns setOf("cnReadOnly") andThen emptySet()

        assertEquals(
            setOf("cnReadOnly"),
            multiaccountInteractor.backupRestores(listOf("cnReadOnly")).keys,
        )
        // The account was verified in between (import or confirmed repair).
        assertTrue(multiaccountInteractor.backupRestores(listOf("cnReadOnly")).isEmpty())
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
    fun `finishing a new wallet again after the account was already added completes that attempt`() =
        runTest {
            // The first try added the account and failed afterwards (for example a full disk
            // while the registration state was saved). Trying again must not end in
            // WALLET_DRAFT_ALREADY_ACCOUNT for ever.
            val draft = draft("cnNew", "New")
            val added = SoraAccount("cnNew", "New")
            coEvery { userRepository.accountExists("cnNew") } returns true
            coEvery { userRepository.unavailableWalletIds() } returns emptySet()
            coEvery { userRepository.getSoraAccount("cnNew") } returns added

            assertEquals(added, multiaccountInteractor.createUserFromDraft(draft))

            coVerify(exactly = 0) { credentialsRepository.commitDraft(any()) }
            coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
            coVerifyOrder {
                userRepository.setCurSoraAccount(added)
                userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
            }
        }

    @Test
    fun `a new wallet never takes over an account that cannot sign`() = runTest {
        val draft = draft("cnNew", "New")
        coEvery { userRepository.accountExists("cnNew") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns setOf("cnNew")

        val error = runCatching { multiaccountInteractor.createUserFromDraft(draft) }.exceptionOrNull()

        assertEquals("WALLET_DRAFT_ALREADY_ACCOUNT", error?.message)
        coVerify(exactly = 0) { userRepository.setCurSoraAccount(any()) }
        coVerify(exactly = 0) { credentialsRepository.commitDraft(any()) }
    }

    // ---- "Wallets found on this phone", behind the stored PIN --------------------------------

    @Test
    fun `wallets found on this phone are offered only behind a PIN that is stored and readable`() =
        runTest {
            coEvery { userRepository.retainedWallets() } returns listOf(
                RetainedWallet("cnKept", needsRecoveryPhrase = false),
                RetainedWallet("cnLeftByRemoveAll", needsRecoveryPhrase = true),
            )

            coEvery { userRepository.retrievePin() } returns "135790"
            assertEquals(listOf("cnKept"), multiaccountInteractor.walletsRestorableWithPin())

            // No PIN: nothing the person could prove here.
            coEvery { userRepository.retrievePin() } returns ""
            assertEquals(emptyList<String>(), multiaccountInteractor.walletsRestorableWithPin())

            // A PIN that cannot be read confirms nothing either.
            coEvery { userRepository.retrievePin() } throws WalletDecryptionException("PIN_UNREADABLE")
            assertEquals(emptyList<String>(), multiaccountInteractor.walletsRestorableWithPin())
        }

    @Test
    fun `the stored PIN restores the wallets found on this phone and never one left by Remove all accounts`() =
        runTest {
            coEvery { userRepository.retrievePin() } returns "135790"
            coEvery { userRepository.retainedWallets() } returns listOf(
                RetainedWallet("cnA", needsRecoveryPhrase = false),
                RetainedWallet("cnLeftByRemoveAll", needsRecoveryPhrase = true),
                RetainedWallet("cnB", needsRecoveryPhrase = false),
            )
            coEvery { userRepository.adoptRetainedWallet("cnA", "Restored", false) } returns
                SoraAccount("cnA", "Restored")
            coEvery { userRepository.adoptRetainedWallet("cnB", "Restored", false) } throws
                IllegalStateException("RETAINED_WALLET_ADDRESS_MISMATCH")

            val result = multiaccountInteractor.restoreRetainedWallets("135790", "Restored")

            assertEquals(listOf(SoraAccount("cnA", "Restored")), result.restored)
            assertEquals(listOf("cnB"), result.notRestorable)
            coVerify(exactly = 0) { userRepository.adoptRetainedWallet("cnLeftByRemoveAll", any(), any()) }
        }

    @Test
    fun `a wrong PIN restores nothing and is counted like on the PIN screen`() = runTest {
        coEvery { userRepository.retrievePin() } returns "135790"
        coEvery { userRepository.retainedWallets() } returns
            listOf(RetainedWallet("cnA", needsRecoveryPhrase = false))
        multiaccountInteractor.now = { 1_000_000L }

        repeat(2) {
            val error = runCatching {
                multiaccountInteractor.restoreRetainedWallets("000000", "Restored")
            }.exceptionOrNull()
            assertEquals(ResponseCode.WRONG_PIN_CODE, (error as? SoraException)?.errorResponseCode)
        }
        // The third wrong entry locks PIN entry, here and on the PIN screen.
        val locked = runCatching {
            multiaccountInteractor.restoreRetainedWallets("000000", "Restored")
        }.exceptionOrNull()
        assertTrue(locked is RepairPinLockedException)
        assertEquals(3, pinTries)
        assertEquals(1_000_000L, pinLockStartedAt)
        // While locked, the right PIN is refused too.
        val stillLocked = runCatching {
            multiaccountInteractor.restoreRetainedWallets("135790", "Restored")
        }.exceptionOrNull()
        assertTrue(stillLocked is RepairPinLockedException)

        coVerify(exactly = 0) { userRepository.adoptRetainedWallet(any(), any(), any()) }
    }

    @Test
    fun `without a stored PIN nothing is restored from the phone`() = runTest {
        coEvery { userRepository.retrievePin() } returns ""
        coEvery { userRepository.retainedWallets() } returns
            listOf(RetainedWallet("cnA", needsRecoveryPhrase = false))

        val error = runCatching {
            multiaccountInteractor.restoreRetainedWallets("", "Restored")
        }.exceptionOrNull()

        assertEquals(ResponseCode.WRONG_PIN_CODE, (error as? SoraException)?.errorResponseCode)
        coVerify(exactly = 0) { userRepository.adoptRetainedWallet(any(), any(), any()) }
    }

    @Test
    fun `a storage failure while restoring ends the action instead of calling a wallet not restorable`() =
        runTest {
            coEvery { userRepository.retrievePin() } returns "135790"
            coEvery { userRepository.retainedWallets() } returns
                listOf(RetainedWallet("cnA", needsRecoveryPhrase = false))
            coEvery { userRepository.adoptRetainedWallet("cnA", any(), any()) } throws
                KeyMaterialUnavailableException("KEYSTORE_UNAVAILABLE", transient = true)

            val error = runCatching {
                multiaccountInteractor.restoreRetainedWallets("135790", "Restored")
            }.exceptionOrNull()

            assertTrue(error is KeyMaterialUnavailableException)
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
        assertTrue(result.needRepair.isEmpty())
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
        // The account with other stored keys is named, so the same file can repair it after
        // one confirmation. The import itself replaced nothing.
        assertEquals(
            listOf(MultiaccountInteractor.RepairCandidate("cnConflict", "Conflict", accountExists = false)),
            result.needRepair,
        )
        assertFalse(result.repairPinRequired)
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }
        listOf(fresh, here, conflict).forEach { assertTrue(it.isDestroyedForTest()) }
    }

    @Test
    fun `a batch JSON file repairs the accounts it named after one confirmation`() = runTest {
        val broken = jsonDraft("cnBroken", "Broken")
        val orphan = jsonDraft("cnOrphan", "Orphan")
        val fine = jsonDraft("cnFine", "Fine")
        val other = jsonDraft("cnOther", "Other")
        val repaired = SoraAccount("cnBroken", "Kept name")
        val adopted = SoraAccount("cnOrphan", "Orphan")
        coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns
            listOf(broken, orphan, fine, other)
        coEvery { userRepository.retrievePin() } returns "135790"
        coEvery { userRepository.accountExists("cnBroken") } returns true
        coEvery { userRepository.accountExists("cnOrphan") } returns false
        coEvery { userRepository.accountExists("cnFine") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns setOf("cnBroken")
        coEvery { credentialsRepository.repairWithDraft(broken) } returns
            SoraAccount("cnBroken", "Broken")
        coEvery { credentialsRepository.repairWithDraft(orphan) } returns adopted
        coEvery { userRepository.completeUnavailableWallet("cnBroken") } returns repaired

        // A wrong PIN repairs nothing and does not even open the file.
        val wrongPin = runCatching {
            multiaccountInteractor.repairFromJsonBatch(
                json = "{batch}",
                password = "password",
                accountName = "",
                pin = "000000",
                addresses = listOf("cnBroken", "cnOrphan", "cnFine"),
            )
        }.exceptionOrNull()
        assertEquals(ResponseCode.WRONG_PIN_CODE, (wrongPin as? SoraException)?.errorResponseCode)
        coVerify(exactly = 0) { credentialsRepository.draftsFromJson(any(), any(), any()) }

        val result = multiaccountInteractor.repairFromJsonBatch(
            json = "{batch}",
            password = "password",
            accountName = "",
            pin = "135790",
            // cnFine can sign by now: it is left as it is. cnOther was not named: untouched.
            addresses = listOf("cnBroken", "cnOrphan", "cnFine"),
        )

        assertEquals(listOf(repaired, adopted), result)
        coVerify(exactly = 1) { credentialsRepository.repairWithDraft(broken) }
        coVerify(exactly = 1) { credentialsRepository.repairWithDraft(orphan) }
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(fine) }
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(other) }
        // The listed account keeps its row; the key set without a row becomes an account.
        coVerify(exactly = 0) { userRepository.insertSoraAccount(repaired, any()) }
        coVerify(exactly = 1) { userRepository.insertSoraAccount(adopted, false) }
        listOf(broken, orphan, fine, other).forEach { assertTrue(it.isDestroyedForTest()) }
    }

    @Test
    fun `a batch repair that names an account the file does not hold replaces nothing`() = runTest {
        val broken = jsonDraft("cnBroken", "Broken")
        coEvery { credentialsRepository.draftsFromJson(any(), any(), any()) } returns listOf(broken)
        coEvery { userRepository.retrievePin() } returns ""

        val error = runCatching {
            multiaccountInteractor.repairFromJsonBatch(
                json = "{batch}",
                password = "password",
                accountName = "",
                pin = "",
                addresses = listOf("cnBroken", "cnMissing"),
            )
        }.exceptionOrNull()

        assertEquals(ResponseCode.JSON_BACKUP_INVALID, (error as? SoraException)?.errorResponseCode)
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }
    }

    @Test
    fun `wrong repair PINs count through the PIN screen's counter and lock PIN entry`() = runTest {
        val draft = draft("cnBroken", "Name")
        coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
        coEvery { userRepository.retrievePin() } returns "135790"
        coEvery { userRepository.accountExists("cnBroken") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns setOf("cnBroken")
        var clock = 1_000_000L
        multiaccountInteractor.now = { clock }
        // One wrong entry was already made on the PIN screen.
        pinTries = 1

        val second = runCatching {
            multiaccountInteractor.repairFromMnemonic("mnemonic", "Name", "000000")
        }.exceptionOrNull()
        assertEquals(ResponseCode.WRONG_PIN_CODE, (second as? SoraException)?.errorResponseCode)
        assertEquals(2, pinTries)
        assertEquals(0L, pinLockStartedAt)

        // The third wrong entry in a row locks PIN entry for a minute, as on the PIN screen.
        val third = runCatching {
            multiaccountInteractor.repairFromMnemonic("mnemonic", "Name", "111111")
        }.exceptionOrNull() as RepairPinLockedException
        assertEquals(60_000L, third.remainingMillis)
        assertEquals(3, pinTries)
        assertEquals(clock, pinLockStartedAt)

        // While it is locked not even the right PIN is looked at, here or by reopening.
        clock += 20_000L
        val locked = runCatching {
            multiaccountInteractor.repairFromMnemonic("mnemonic", "Name", "135790")
        }.exceptionOrNull() as RepairPinLockedException
        assertEquals(40_000L, locked.remainingMillis)
        assertEquals(3, pinTries)
        coVerify(exactly = 0) { credentialsRepository.draftFromMnemonic(any(), any()) }
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }

        // After the lock the next wrong entry locks for the next, longer step.
        clock += 60_000L
        val fourth = runCatching {
            multiaccountInteractor.repairFromMnemonic("mnemonic", "Name", "222222")
        }.exceptionOrNull() as RepairPinLockedException
        assertEquals(5 * 60_000L, fourth.remainingMillis)
        assertEquals(4, pinTries)

        // The right PIN after the lock repairs and resets the counter and the timer.
        clock += 5 * 60_000L
        val repaired = SoraAccount("cnBroken", "Kept name")
        coEvery { credentialsRepository.repairWithDraft(draft) } returns SoraAccount("cnBroken", "Name")
        coEvery { userRepository.completeUnavailableWallet("cnBroken") } returns repaired
        assertEquals(
            repaired,
            multiaccountInteractor.repairFromMnemonic("mnemonic", "Name", "135790"),
        )
        assertEquals(0, pinTries)
        assertEquals(0L, pinLockStartedAt)
    }

    @Test
    fun `a stored PIN that cannot be read is never treated as no PIN`() = runTest {
        val draft = draft("cnBroken", "Name")
        coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
        coEvery { userRepository.retrievePin() } throws
            jp.co.soramitsu.common.util.WalletDecryptionException("PIN_UNREADABLE")
        coEvery { userRepository.accountExists("cnBroken") } returns true
        coEvery { userRepository.unavailableWalletIds() } returns setOf("cnBroken")
        coEvery { credentialsRepository.commitDraft(draft) } throws
            SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)

        val offer = runCatching {
            multiaccountInteractor.importFromMnemonic("mnemonic", "Name")
        }.exceptionOrNull() as WalletRepairAvailableException
        // No PIN can be asked for, and a plain tap is not enough either.
        assertFalse(offer.pinRequired)
        assertTrue(offer.screenLockRequired)

        val again = draft("cnBroken", "Name")
        coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns again
        val unconfirmed = runCatching {
            multiaccountInteractor.repairFromMnemonic("mnemonic", "Name", pin = "")
        }.exceptionOrNull()
        assertEquals(
            ResponseCode.REPAIR_NEEDS_SCREEN_LOCK,
            (unconfirmed as? SoraException)?.errorResponseCode,
        )
        coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }

        // Confirmed with the phone's screen lock: the repair runs.
        val repaired = SoraAccount("cnBroken", "Kept name")
        coEvery { credentialsRepository.repairWithDraft(again) } returns SoraAccount("cnBroken", "Name")
        coEvery { userRepository.completeUnavailableWallet("cnBroken") } returns repaired
        assertEquals(
            repaired,
            multiaccountInteractor.repairFromMnemonic(
                "mnemonic",
                "Name",
                pin = "",
                screenLockConfirmed = true,
            ),
        )
    }

    @Test
    fun `the phrase of a wallet that works but has no phrase stored is kept as its backup`() =
        runTest {
            val draft = draft("cnFromFile", "Name")
            coEvery { credentialsRepository.draftFromMnemonic(any(), any()) } returns draft
            coEvery { userRepository.accountExists("cnFromFile") } returns true
            coEvery { userRepository.unavailableWalletIds() } returns emptySet()
            coEvery { credentialsRepository.addMissingBackup(draft) } returns true

            val error = runCatching {
                multiaccountInteractor.importFromMnemonic("mnemonic", "Name")
            }.exceptionOrNull()

            // Still "already here", and it says that the phrase was kept.
            assertEquals(
                ResponseCode.ACCOUNT_ALREADY_IMPORTED_BACKUP_SAVED,
                (error as? SoraException)?.errorResponseCode,
            )
            coVerifyOrder {
                credentialsRepository.addMissingBackup(draft)
                // The backup label and the SORA 3 accounts are recorded right away.
                userRepository.refreshVerifiedWallet("cnFromFile")
            }
            // No key value is written and the account row is not touched.
            coVerify(exactly = 0) { credentialsRepository.commitDraft(any()) }
            coVerify(exactly = 0) { credentialsRepository.repairWithDraft(any()) }
            coVerify(exactly = 0) { userRepository.insertSoraAccount(any(), any()) }
            assertTrue(draft.isDestroyedForTest())
        }

    @Test
    fun `opening a backup file never runs on the caller's thread`() = runTest {
        val caller = Thread.currentThread()
        var importThread: Thread? = null
        var repairThread: Thread? = null
        var exportThread: Thread? = null
        val draft = jsonDraft("cnFromJson", "Restored")
        val imported = SoraAccount("cnFromJson", "Restored")
        coEvery { credentialsRepository.draftsFromJson("{json}", any(), any()) } answers {
            importThread = Thread.currentThread()
            listOf(draft)
        }
        coEvery { userRepository.accountExists("cnFromJson") } returns false
        coEvery { credentialsRepository.commitDraft(draft) } returns imported
        multiaccountInteractor.importFromJson("{json}", "password", "Restored")

        val again = jsonDraft("cnFromJson", "Restored")
        coEvery { userRepository.retrievePin() } returns ""
        coEvery { credentialsRepository.draftsFromJson("{repair}", any(), any()) } answers {
            repairThread = Thread.currentThread()
            listOf(again)
        }
        coEvery { credentialsRepository.repairWithDraft(again) } returns imported
        multiaccountInteractor.repairFromJson("{repair}", "password", "Restored", pin = "")

        coEvery { userRepository.getSoraAccount("cnFromJson") } returns imported
        coEvery { credentialsRepository.generateJson(listOf(imported), "password") } answers {
            exportThread = Thread.currentThread()
            "{export}"
        }
        multiaccountInteractor.getJsonFileUri(listOf("cnFromJson"), "password")

        listOf(importThread, repairThread, exportThread).forEach { worker ->
            assertTrue(worker != null && worker !== caller)
        }
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
        val nothingMatches = WalletBackupMaterial(
            canSign = true,
            phraseWords = emptyList(),
            seedHex = "",
            storedPhraseDoesNotMatch = true,
            storedSeedDoesNotMatch = false,
        )
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        coEvery { credentialsRepository.retrieveBackupMaterial(soraAccount) } returns nothingMatches
        coEvery { credentialsRepository.retrieveBackupMaterialForSeed(soraAccount) } returns
            nothingMatches
        assertEquals("", multiaccountInteractor.getMnemonic(address))
        assertEquals("", multiaccountInteractor.getSeed(address))
        assertTrue(multiaccountInteractor.getBackupMaterial(address).onlyJsonExport)
        coVerify(exactly = 0) { credentialsRepository.retrieveSeed(any()) }
    }

    @Test
    fun `the seed comes from one judgement of the stored secrets`() = runTest {
        val address = "address"
        val soraAccount = SoraAccount("address", "name")
        coEvery { userRepository.getSoraAccount(address) } returns soraAccount
        val seed = "seed"
        coEvery { credentialsRepository.retrieveBackupMaterialForSeed(soraAccount) } returns
            material(emptyList(), seed)

        assertEquals(seed, multiaccountInteractor.getSeed(address))
        // The Raw seed screen: what may be shown and the seed itself, in the same answer.
        val shown = multiaccountInteractor.getSeedBackupMaterial(address)
        assertTrue(shown.hasSeed)
        assertEquals(seed, shown.seedHex)

        // One judgement per call, never the three the screen used to make.
        coVerify(exactly = 2) { credentialsRepository.retrieveBackupMaterialForSeed(soraAccount) }
        coVerify(exactly = 0) { credentialsRepository.retrieveBackupMaterial(any()) }
        coVerify(exactly = 0) { credentialsRepository.retrieveSeed(any()) }
    }

    @Test
    fun `judging the stored secrets for a backup screen never runs on the caller's thread`() =
        runTest {
            val caller = Thread.currentThread()
            val workers = mutableListOf<Thread>()
            val soraAccount = SoraAccount("address", "name")
            coEvery { userRepository.getSoraAccount("address") } returns soraAccount
            coEvery { credentialsRepository.retrieveBackupMaterial(soraAccount) } answers {
                workers += Thread.currentThread()
                material(listOf("alpha", "beta"), "seed")
            }
            coEvery { credentialsRepository.retrieveBackupMaterialForSeed(soraAccount) } answers {
                workers += Thread.currentThread()
                material(listOf("alpha", "beta"), "seed")
            }

            // Account options (is a phrase available), Passphrase, Raw seed, Google backup.
            multiaccountInteractor.getMnemonic(soraAccount)
            multiaccountInteractor.getBackupMaterial("address")
            multiaccountInteractor.getSeedBackupMaterial("address")
            multiaccountInteractor.getSeed("address")

            assertEquals(4, workers.size)
            workers.forEach { worker -> assertTrue(worker !== caller) }
        }

    @Test
    fun `accounts that cannot be exported with the others are named and no file is written`() =
        runTest {
            val works = SoraAccount("cnWorks", "Works")
            val broken = SoraAccount("cnBroken", "Broken")
            val alsoBroken = SoraAccount("cnAlsoBroken", "Also broken")
            listOf(works, broken, alsoBroken).forEach { account ->
                coEvery { userRepository.getSoraAccount(account.substrateAddress) } returns account
                coEvery { credentialsRepository.retrieveBackupMaterial(account) } returns
                    WalletBackupMaterial(
                        canSign = account == works,
                        phraseWords = emptyList(),
                        seedHex = "",
                        storedPhraseDoesNotMatch = false,
                        storedSeedDoesNotMatch = false,
                    )
            }
            coEvery {
                credentialsRepository.generateJson(listOf(works, broken, alsoBroken), "password")
            } throws SoraException.businessError(ResponseCode.ACCOUNT_KEYS_NOT_EXPORTABLE)

            val error = runCatching {
                multiaccountInteractor.getJsonFileUri(
                    listOf("cnWorks", "cnBroken", "cnAlsoBroken"),
                    "password",
                )
            }.exceptionOrNull()

            assertTrue(error is MultiaccountInteractor.AccountsNotExportableException)
            assertEquals(
                listOf(broken, alsoBroken),
                (error as MultiaccountInteractor.AccountsNotExportableException).accounts,
            )
            coVerify(exactly = 0) { fileManager.writeExternalCacheText(any(), any()) }
        }

    @Test
    fun `one account that cannot be exported keeps the message that needs no name`() = runTest {
        val broken = SoraAccount("cnBroken", "Broken")
        coEvery { userRepository.getSoraAccount("cnBroken") } returns broken
        coEvery { credentialsRepository.generateJson(listOf(broken), "password") } throws
            SoraException.businessError(ResponseCode.ACCOUNT_KEYS_NOT_EXPORTABLE)

        val error = runCatching {
            multiaccountInteractor.getJsonFileUri(listOf("cnBroken"), "password")
        }.exceptionOrNull()

        assertEquals(
            ResponseCode.ACCOUNT_KEYS_NOT_EXPORTABLE,
            (error as? SoraException)?.errorResponseCode,
        )
        coVerify(exactly = 0) { fileManager.writeExternalCacheText(any(), any()) }
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
