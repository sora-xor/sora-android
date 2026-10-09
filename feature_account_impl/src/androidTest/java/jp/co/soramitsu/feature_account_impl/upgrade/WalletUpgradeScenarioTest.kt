package jp.co.soramitsu.feature_account_impl.upgrade

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.mockk
import java.io.File
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.WalletMigrationIntegrity
import jp.co.soramitsu.core_db.WalletRecoveryKind
import jp.co.soramitsu.core_db.WalletSigningGate
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_impl.data.repository.CredentialsRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositorySr25519Crypto
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsCredentialsDatasource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsUserDatasource
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationManager
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationOutcome
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationSr25519Crypto
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades storage written by the released apps' own code (see HistoricalWalletWriters.kt)
 * through the production 3.9.0 path: the upgrade gate, the Room 77 builder with every
 * migration, the encrypted preference readers, the repositories and the wallet verification.
 *
 * Every scenario ends with each expected wallet usable: same address as before, verified for
 * signing, and a fresh sr25519 signature that verifies against the address's public key. Where a
 * single account genuinely cannot be used (its keys are incomplete or unreadable) only that
 * account is read-only and the others still sign. Old key material is never deleted or changed.
 *
 * An "app process" is modelled by new instances of every component and a DataStore owned by a
 * scope that is cancelled when the process ends.
 */
@RunWith(AndroidJUnit4::class)
class WalletUpgradeScenarioTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context =
        InstrumentationRegistry.getInstrumentation().context
    private val codec = Sora2AddressCodec()

    @Before
    fun cleanInstall() {
        resetWalletStorage(context)
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
    }

    @After
    fun cleanUp() {
        resetWalletStorage(context)
        WalletUpgradeBackup.prepare(context)
    }

    @Test
    fun release3863MultiAccountUpgradesAndSigns() = runBlocking {
        val twelve = phraseWallet(TWELVE_WORDS)
        val twentyFour = phraseWallet(TWENTY_FOUR_WORDS)
        val fifteen = phraseWallet(FIFTEEN_WORDS)
        val twentyOne = phraseWallet(TWENTY_ONE_WORDS)
        val seed = rawSeedWallet(RAW_SEED)
        // Derivation is unchanged: pinned public vectors from 3.8.6.3.
        assertEquals(TWELVE_WORDS_ADDRESS, twelve.address)
        assertEquals(TWENTY_FOUR_WORDS_ADDRESS, twentyFour.address)

        val before = release3863 {
            created(twelve)
            created(twentyFour)
            created(fifteen)
            created(twentyOne)
            importedSeed(seed)
            listOf(twelve, twentyFour, fifteen, twentyOne, seed).forEach { finishedAccount(it) }
            select(twentyFour)
            registrationFinished()
        }
        room(
            73,
            twelve to "Twelve",
            twentyFour to "Twenty four",
            fifteen to "Fifteen",
            twentyOne to "Twenty one",
            seed to "Seed",
        )

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(
                listOf(twelve, twentyFour, fifteen, twentyOne, seed),
                selected = twentyFour,
            )
            launch.assertCiphertextsKept(before)
            assertEquals("MNEMONIC_UNSUPPORTED", launch.identity(fifteen).secretSource)
            assertEquals("MNEMONIC_UNSUPPORTED", launch.identity(twentyOne).secretSource)
            assertEquals("RAW_SEED", launch.identity(seed).secretSource)
        }
        // A second start finds nothing to change.
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(
                listOf(twelve, twentyFour, fifteen, twentyOne, seed),
                selected = twentyFour,
            )
        }
        assertEquals(1, backups().size)
    }

    @Test
    fun release233SingleAccountUpgradesAndSigns() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        val before = release233 { install(wallet, name = "Main wallet") }
        room(50)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
            assertEquals(
                listOf(SoraAccountLocal(wallet.address, "Main wallet")),
                launch.database.accountDao().getAccounts(),
            )
            launch.assertCiphertextsKept(before)
        }
        assertTrue(backups().any { it.name == "wallet-upgrade-backup-v50-to-77" })
    }

    @Test
    fun abandonedFirstCreateIsSetAsideAndAdoptedOnImport() = runBlocking {
        val abandoned = phraseWallet(TWENTY_FOUR_WORDS)
        // 3.8.6.3 stored the keys at "Continue"; the person backed out before the account row.
        val before = release3863 { created(abandoned) }
        room(73)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(setOf(abandoned.address), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            launch.assertCiphertextsKept(before)

            // Onboarding: the person imports the same phrase. The stored keys are proven to
            // match and re-used; nothing is rewritten.
            launch.importPhrase(TWENTY_FOUR_WORDS, "Restored")
            launch.assertCiphertextsKept(before)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(abandoned), selected = abandoned)
        }
    }

    @Test
    fun abandonedAddAccountDoesNotBlockTheExistingWallet() = runBlocking {
        val main = phraseWallet(TWELVE_WORDS)
        val abandoned = phraseWallet(TWENTY_FOUR_WORDS)
        val before = release3863 {
            created(main)
            finishedAccount(main)
            select(main)
            registrationFinished()
            created(abandoned)
        }
        room(73, main to "Main")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(setOf(abandoned.address), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(main), selected = main)
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun danglingSelectionFallsBackToAnExistingAccount() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val second = rawSeedWallet(RAW_SEED)
        release3863 {
            created(first)
            importedSeed(second)
            finishedAccount(first)
            finishedAccount(second)
            // A crash between deleting an account and selecting the next one.
            user.setCurAccountAddress("cnDeletedWalletWithoutRow")
            registrationFinished()
        }
        room(73, first to "First", second to "Second")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(
                "cnDeletedWalletWithoutRow",
                WalletUpgradeBackup.quarantine().staleSelection,
            )
            assertEquals(MigrationOutcome.Verified, launch.verify())
            val fallback = listOf(first.address, second.address).min()
            launch.assertUsable(listOf(first, second))
            assertEquals(fallback, launch.userRepository.getCurSoraAccount().substrateAddress)
            assertEquals(fallback, launch.userDatasource.getCurAccountAddress())
        }
    }

    @Test
    fun partialKeyTripleMakesOnlyThatAccountReadOnly() = runBlocking {
        val main = phraseWallet(TWELVE_WORDS)
        val partial = phraseWallet(TWENTY_FOUR_WORDS)
        val orphanPartial = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            created(main)
            finishedAccount(main)
            select(main)
            registrationFinished()
            // Process killed between the 2nd and 3rd edit of saveKeys (no nonce), for an
            // account whose row exists and for one whose row was never written.
            keysWithoutNonce(partial)
            credentials.saveMnemonic(partial.mnemonic!!, partial.address)
            keysWithoutNonce(orphanPartial)
        }
        room(73, main to "Main", partial to "Partial")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertTrue(
                WalletUpgradeBackup.quarantine().incompleteKeySetWalletIds
                    .containsAll(setOf(partial.address, orphanPartial.address))
            )
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(main), unavailable = listOf(partial.address), selected = main)
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun staleReleaseEraOwnerIsIgnored() = runBlocking {
        val deleted = phraseWallet(TWELVE_WORDS)
        val current = phraseWallet(TWENTY_FOUR_WORDS)
        // 2.3.x stored the first wallet unsuffixed; 2.4+ deleted its account but left those keys.
        val legacy = release233 { install(deleted, name = "Removed in 2.4") }
        val before = release3863 {
            created(current)
            finishedAccount(current)
            select(current)
            registrationFinished()
        }
        room(73, current to "Current")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(deleted.address, WalletUpgradeBackup.quarantine().unlinkedLegacyOwner)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(current), selected = current)
            launch.assertCiphertextsKept(legacy + before)
        }
    }

    @Test
    fun undecryptableAccountIsIsolatedFromTheOthers() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val broken = phraseWallet(TWENTY_FOUR_WORDS)
        val third = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            created(first)
            created(broken)
            importedSeed(third)
            listOf(first, broken, third).forEach { finishedAccount(it) }
            select(broken)
            registrationFinished()
            // Ciphertext that no longer decrypts (wrong padding under the stored AES key).
            prefs.putString("prefs_priv_key${broken.address}", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        }
        room(73, first to "First", broken to "Broken", third to "Third")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(
                listOf(first, third),
                unavailable = listOf(broken.address),
                selected = broken,
            )
            assertEquals("UNAVAILABLE", launch.identity(broken).migrationState)
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun lowStorageMakesAKeysOnlyBackupOrAsksForSpace() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val second = phraseWallet(TWENTY_FOUR_WORDS)
        val before = release3863 {
            created(first)
            created(second)
            finishedAccount(first)
            finishedAccount(second)
            select(first)
            registrationFinished()
        }
        room(73, first to "First", second to "Second")

        // No space at all: the upgrade waits with a forward path and nothing is changed.
        val noSpace = WalletUpgradeBackup.prepareWithAvailableBackupBytesForTest(context, 0L)
        assertTrue(noSpace.isFailure)
        val failure = requireNotNull(WalletUpgradeBackup.blockingFailure())
        assertEquals("INSUFFICIENT_BACKUP_STORAGE", failure.code)
        val needed = requireNotNull(failure.requiredBytes)
        assertEquals(0L, failure.availableBytes)
        val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertEquals(WalletRecoveryKind.STORAGE, options.kind)
        assertTrue(options.canRetry && options.retryAutomatically && options.canRestoreFromPhrase)
        assertFalse(options.canReadOnly)
        assertEquals(73, databaseVersion())
        assertTrue(backups().isEmpty())

        // Enough for the small keys-only backup but not a full copy: continue.
        assertTrue(WalletUpgradeBackup.prepareWithAvailableBackupBytesForTest(context, needed).isSuccess)
        assertTrue("KEYS_ONLY_BACKUP" in WalletUpgradeBackup.nonBlockingWarnings())
        val backup = backups().single()
        val manifest = JSONObject(File(backup, "manifest.json").readText())
        assertEquals("keys-only", manifest.getString("kind"))
        assertEquals(2, manifest.getJSONArray("accounts").length())
        assertFalse(File(backup, "databases/app.db").exists())
        assertTrue(File(backup, "files/datastore/sora_prefs_datastore.preferences_pb").isFile)

        candidate(prepareFirst = false) { launch ->
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = first)
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun killedAfterTheSchemaStepResumesOnNextStart() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        release3863Wallet(wallet)
        room(73, wallet to "Main")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            launch.database.openHelper.writableDatabase // Room 73 -> 77, then the process dies.
        }
        assertEquals(77, databaseVersion())
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
    }

    @Test
    fun killedAfterTheVerificationStartedRetriesAutomatically() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        release3863Wallet(wallet)
        room(73, wallet to "Main")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            launch.database.openHelper.writableDatabase
        }
        // Killed right after the first journal write (VERIFYING, nothing staged yet).
        writeJournal(state = "VERIFYING", accounts = listOf(wallet.address to "Main"), selected = wallet.address)

        // Background starts (workers, boot) prepare only: they never use the retry budget.
        repeat(3) {
            candidate { launch ->
                assertTrue(launch.prepare.isFailure)
                assertEquals(0, WalletUpgradeBackup.automaticMigrationRetriesUsedForTest(context))
            }
        }
        candidate { launch ->
            assertTrue(launch.prepare.isFailure)
            assertTrue(launch.startScreens())
            assertEquals(1, WalletUpgradeBackup.automaticMigrationRetriesUsedForTest(context))
            assertEquals(
                WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED,
                WalletRecoveryCapabilityGate.mode(),
            )
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(WalletRecoveryCapabilityGate.Mode.NORMAL, WalletRecoveryCapabilityGate.mode())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(0, WalletUpgradeBackup.automaticMigrationRetriesUsedForTest(context))
        }
    }

    @Test
    fun killedAfterStagingBeforeActivationRetriesAutomatically() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val second = rawSeedWallet(RAW_SEED)
        release3863 {
            created(first)
            importedSeed(second)
            finishedAccount(first)
            finishedAccount(second)
            select(second)
            registrationFinished()
        }
        room(73, first to "First", second to "Second")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
        }
        // Rewind to the state after the staging transaction and before activation.
        updateDatabase(
            "UPDATE walletMigrationJournal SET state = 'VERIFYING', completedAt = NULL"
        )
        candidate { launch ->
            assertTrue(launch.prepare.isFailure)
            assertTrue(launch.startScreens())
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = second)
        }
    }

    @Test
    fun recoveryJournalFromAnEarlierBuildRetriesAutomatically() = runBlocking {
        val wallet = phraseWallet(TWENTY_FOUR_WORDS)
        release3863Wallet(wallet)
        room(73, wallet to "Main")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            launch.database.openHelper.writableDatabase
        }
        // Earlier candidate builds journaled transient failures and a missing selection.
        writeJournal(
            state = "RECOVERY_REQUIRED",
            accounts = listOf(wallet.address to "Main"),
            selected = "",
            failureCode = "KEYSTORE_UNAVAILABLE",
        )
        candidate { launch ->
            assertTrue(launch.prepare.isFailure)
            assertTrue(launch.startScreens())
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
    }

    @Test
    fun repeatedInterruptionsStopAutoRetryButTryAgainStillWorks() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        release3863Wallet(wallet)
        room(73, wallet to "Main")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            launch.database.openHelper.writableDatabase
        }
        writeJournal(state = "VERIFYING", accounts = listOf(wallet.address to "Main"), selected = wallet.address)
        // Three starts of the app's screens are each interrupted before the retried
        // verification finishes.
        repeat(3) { attempt ->
            candidate { launch ->
                assertTrue(launch.prepare.isFailure)
                assertTrue(launch.startScreens())
                assertEquals(attempt + 1, WalletUpgradeBackup.automaticMigrationRetriesUsedForTest(context))
            }
        }
        candidate { launch ->
            assertTrue(launch.prepare.isFailure)
            assertFalse(launch.startScreens())
            assertEquals(
                "WALLET_MIGRATION_RECOVERY_REQUIRED",
                WalletUpgradeBackup.blockingFailure()?.code,
            )
            val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
            assertTrue(options.canRetry && options.canReadOnly && options.canShowPhraseAfterPin)
            // Try again.
            assertTrue(WalletUpgradeBackup.retry(context).isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
    }

    @Test
    fun interruptedBackupCopyIsCleanedOrSetAsideAndTheUpgradeContinues() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        release3863Wallet(wallet)
        room(73, wallet to "Main")
        // Killed while copying: a redundant prefix of the installed database...
        val staging = File(context.noBackupFilesDir, "wallet-upgrade-backup-v73-to-77.staging")
        File(staging, "databases").mkdirs()
        File(staging, "databases/app.db").writeBytes(
            context.getDatabasePath("app.db").readBytes().copyOf(512)
        )
        // ...and an unexpected leftover from an internal build that cannot be proven redundant.
        val foreign = File(context.noBackupFilesDir, "wallet-upgrade-backup-v72-to-76")
        foreign.mkdirs()
        File(foreign, "note.txt").writeText("internal build leftover")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            // Neither blocks: the copy is removed or renamed aside, the leftover renamed aside.
            assertFalse(staging.exists())
            assertFalse(foreign.exists())
            assertTrue(
                context.noBackupFilesDir.listFiles().orEmpty()
                    .filter { it.name.startsWith("wallet-backup-quarantine-") }
                    .any { File(it, "note.txt").takeIf(File::isFile)?.readText() == "internal build leftover" }
            )
            assertNotNull(backups().singleOrNull { it.name == "wallet-upgrade-backup-v73-to-77" })
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
    }

    @Test
    fun transientKeystoreFailureIsNeverRememberedOrJournaled() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val second = rawSeedWallet(RAW_SEED)
        release3863 {
            created(first)
            importedSeed(second)
            finishedAccount(first)
            finishedAccount(second)
            select(first)
            registrationFinished()
        }
        room(73, first to "First", second to "Second")

        // The Keystore is busy for the first four probes (constructor + one full retry round).
        var busyProbes = 4
        val keyStoreFactory = {
            if (busyProbes-- > 0) throw KeyStoreException("Keystore operation failed (busy)")
            KeyStore.getInstance("AndroidKeyStore")
        }
        candidate(keyStoreFactory = keyStoreFactory) { launch ->
            assertTrue(launch.prepare.isSuccess)
            val outcome = launch.verify()
            assertEquals(MigrationOutcome.Transient("KEYSTORE_UNAVAILABLE"), outcome)
            assertNull(
                launch.database.walletIdentityDao()
                    .getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
            )
            val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
            assertEquals(WalletRecoveryKind.TRANSIENT, options.kind)
            assertTrue(options.retryAutomatically && options.canReadOnly)

            // The Keystore recovers. Try again in the same process, without a restart.
            assertTrue(WalletUpgradeBackup.retry(context).isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = first)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = first)
        }
    }

    @Test
    fun partialKeyTripleIsCompletedByImportingItsPhrase() = runBlocking {
        val main = phraseWallet(TWELVE_WORDS)
        val partial = phraseWallet(TWENTY_FOUR_WORDS)
        val before = release3863 {
            created(main)
            finishedAccount(main)
            select(main)
            registrationFinished()
            // Killed between the 2nd and 3rd edit of saveKeys: no nonce.
            keysWithoutNonce(partial)
            credentials.saveMnemonic(partial.mnemonic!!, partial.address)
        }
        room(73, main to "Main", partial to "Partial")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(main), unavailable = listOf(partial.address), selected = main)
            val options = requireNotNull(launch.userRepository.unavailableWalletOptions(partial.address))
            assertTrue(options.canRepairWithPhrase && options.canShowPhraseAfterPin && options.canReadOnly)

            // The person imports the account's own phrase: only the missing nonce is added.
            launch.importPhrase(TWENTY_FOUR_WORDS, "Typed name")
            launch.assertUsable(listOf(main, partial), selected = partial)
            launch.assertCiphertextsKept(before)
            assertEquals("Partial", launch.userRepository.getCurSoraAccount().accountName)
            assertNull(launch.userRepository.unavailableWalletOptions(partial.address))
        }
        // The next start verifies it like every other account.
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(main, partial), selected = partial)
        }
    }

    @Test
    fun undecryptableAccountCanBeRemovedAndImportedAgain() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val broken = phraseWallet(TWENTY_FOUR_WORDS)
        val before = release3863 {
            created(first)
            created(broken)
            finishedAccount(first)
            finishedAccount(broken)
            select(first)
            registrationFinished()
            prefs.putString("prefs_priv_key${broken.address}", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        }
        room(73, first to "First", broken to "Broken")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first), unavailable = listOf(broken.address), selected = first)
            val options = requireNotNull(launch.userRepository.unavailableWalletOptions(broken.address))
            // A value that cannot be read can never be compared, so it cannot be completed in place.
            assertFalse(options.canRepairWithPhrase)
            assertTrue(options.canRemoveAndImport)
            val refused = runCatching { launch.importPhrase(TWENTY_FOUR_WORDS, "Broken") }
                .exceptionOrNull()
            assertEquals(
                ResponseCode.ACCOUNT_STORAGE_CONFLICT,
                (refused as? SoraException)?.errorResponseCode,
            )
            launch.assertCiphertextsKept(before)

            // The person confirms removing the read-only account. A copy of the old encrypted
            // preferences is kept first.
            launch.remove(broken.address)
            val copy = context.noBackupFilesDir.listFiles().orEmpty()
                .single { it.name.startsWith("wallet-quarantine-") && it.name.endsWith("-removed-unavailable-account") }
            assertTrue(File(copy, "sora_prefs_datastore.preferences_pb").readBytes().isNotEmpty())
            launch.assertUsable(listOf(first), selected = first)

            launch.importPhrase(TWENTY_FOUR_WORDS, "Broken again")
            launch.assertUsable(listOf(first, broken), selected = broken)
        }
    }

    @Test
    fun accountWhoseKeysAnInterruptedRemovalClearedCanBeRestoredOrRemoved() = runBlocking {
        val kept = phraseWallet(TWELVE_WORDS)
        val cleared = phraseWallet(TWENTY_FOUR_WORDS)
        val other = rawSeedWallet(RAW_SEED)
        release3863 {
            created(kept)
            created(cleared)
            importedSeed(other)
            listOf(kept, cleared, other).forEach { finishedAccount(it) }
            select(kept)
            registrationFinished()
        }
        room(73, kept to "Kept", cleared to "Cleared", other to "Other")
        // 3.8.6.3 "Remove account" killed after the preferences edit, before the row delete.
        releasedRemoval(cleared.address, killedAfterPreferences = true)
        // A second account in the same state, removed by the person after the upgrade.
        releasedRemoval(other.address, killedAfterPreferences = true)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(
                listOf(kept),
                unavailable = listOf(cleared.address, other.address),
                selected = kept,
            )
            val options = requireNotNull(launch.userRepository.unavailableWalletOptions(cleared.address))
            assertTrue(options.canRepairWithPhrase)
            assertFalse(options.canShowPhraseAfterPin)

            launch.importPhrase(TWENTY_FOUR_WORDS, "Cleared")
            launch.remove(other.address)
            launch.assertUsable(listOf(kept, cleared), selected = cleared)
        }
    }

    @Test
    fun phoneTransferWithoutTheKeystoreKeyAndOnlyLeftoverKeysSetsUpAgain() = runBlocking {
        val leftover = phraseWallet(TWENTY_FOUR_WORDS)
        // An unfinished create-account flow in 3.8.6.3 left keys without an account.
        release3863 { created(leftover) }
        room(73)
        // "Copy apps & data" to a new phone copies the files but never the Keystore key.
        deleteKeystoreAlias()

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(
                MigrationOutcome.KeysUnavailable("KEYSTORE_KEY_UNAVAILABLE"),
                launch.verify(),
            )
            val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
            assertEquals(WalletRecoveryKind.KEYS_UNAVAILABLE, options.kind)
            assertFalse(options.canReadOnly)
            assertFalse(options.canShowPhraseAfterPin)
            assertTrue(options.canSetUpAgain && options.canRestoreFromPhrase && options.restoreNeedsSetUpAgain)
            assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isSuccess)
        }
        val oldPreferences = dataStoreFile().readBytes()

        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            // The old files were renamed aside, byte for byte; nothing was deleted.
            assertArrayEquals(
                oldPreferences,
                File(setUpAgainDirectory(), "files/datastore/sora_prefs_datastore.preferences_pb").readBytes(),
            )
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            launch.importPhrase(TWENTY_FOUR_WORDS, "Restored")
            launch.assertUsable(listOf(leftover), selected = leftover)
        }
    }

    @Test
    fun phoneTransferWithoutTheKeystoreKeyKeepsBalancesReadOnlyAndSetsUpAgain() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val second = rawSeedWallet(RAW_SEED)
        release3863 {
            created(first)
            importedSeed(second)
            finishedAccount(first)
            finishedAccount(second)
            select(first)
            registrationFinished()
        }
        room(73, first to "First", second to "Second")
        deleteKeystoreAlias()

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(
                MigrationOutcome.KeysUnavailable("KEYSTORE_KEY_UNAVAILABLE"),
                launch.verify(),
            )
            val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
            assertTrue(options.canReadOnly && options.canSetUpAgain)
            assertEquals(
                WalletUpgradeBackup.SafeReadOnlyRoute.ROOM_READ_ONLY,
                WalletUpgradeBackup.authorizeSafeReadOnly(context).getOrThrow(),
            )
            assertEquals(2, launch.database.accountDao().getAccounts().size)
            assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isSuccess)
        }
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertTrue(File(setUpAgainDirectory(), "databases/app.db").isFile)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.importPhrase(TWELVE_WORDS, "First")
            launch.assertUsable(listOf(first), selected = first)
        }
    }

    @Test
    fun release240MultiAccountLayoutUpgradesAndSigns() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        val legacy = release233 { install(wallet, name = "Main wallet") }
        // 2.4.0 rebuilt the database at Room 58 (destructive fallback) and its MigrationManager
        // copied the unsuffixed keys to address-suffixed ones and added the account.
        room(58)
        val copied = release240 { migrate() }

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertTrue(backups().any { it.name == "wallet-upgrade-backup-v58-to-77" })
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
            assertEquals("Main wallet", launch.userRepository.getCurSoraAccount().accountName)
            launch.assertCiphertextsKept(legacy + copied)
        }
    }

    @Test
    fun release240OwnerRemovedLaterIsSetAsideAndCanBeRestored() = runBlocking {
        val original = phraseWallet(TWELVE_WORDS)
        val added = phraseWallet(TWENTY_FOUR_WORDS)
        val legacy = release233 { install(original, name = "Original") }
        room(58)
        release240 { migrate() }
        // A later release added a second account and removed the original one: its suffixed
        // keys and row are gone, the unsuffixed keys and prefs_address_pure remain.
        val before = release3863 {
            created(added)
            finishedAccount(added)
            select(added)
        }
        HistoricalRoom.insertAccounts(context, listOf(added.address to "Added"))
        releasedRemoval(original.address)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(original.address, WalletUpgradeBackup.quarantine().unlinkedLegacyOwner)
            assertTrue(original.address in WalletUpgradeBackup.quarantine().retainedWalletIds)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(added), selected = added)

            // "Restore wallets found on this phone": the unsuffixed keys are proven to derive the
            // address and used as they are; nothing is written.
            launch.userRepository.adoptRetainedWallet(original.address, "Original")
            launch.assertUsable(listOf(original, added), selected = original)
            // The removal in the later release legitimately deleted the suffixed copies.
            launch.assertCiphertextsKept(
                legacy + before.filterKeys { !it.endsWith(original.address) }
            )
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(original, added), selected = original)
        }
    }

    @Test
    fun blankAccountRowNeverBlocksRemovingAccounts() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val second = rawSeedWallet(RAW_SEED)
        release3863 {
            created(first)
            importedSeed(second)
            finishedAccount(first)
            finishedAccount(second)
            select(second)
            registrationFinished()
        }
        room(73, first to "First", second to "Second")
        // A 2.x upgrade that could not derive an address inserted a blank row.
        HistoricalRoom.insertAccounts(context, listOf("" to "Unnamed"))

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(1, WalletUpgradeBackup.quarantine().blankAccountRows)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = second)
            launch.remove(second.address)
            launch.assertUsable(listOf(first), selected = first)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.remove(first.address)
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            // The blank row itself is never deleted.
            assertEquals(listOf(""), launch.database.accountDao().getAccounts().map { it.substrateAddress })
        }
    }

    @Test
    fun clockMovedBackDuringAnInterruptedVerificationNeverBlocks() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        release3863Wallet(wallet)
        room(73, wallet to "Main")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            launch.database.openHelper.writableDatabase
        }
        // Started while the clock was an hour ahead; network time has corrected it since.
        writeJournal(
            state = "VERIFYING",
            accounts = listOf(wallet.address to "Main"),
            selected = wallet.address,
            startedAt = System.currentTimeMillis() + 60 * 60 * 1_000L,
        )
        candidate { launch ->
            assertTrue(launch.prepare.isFailure)
            assertTrue(launch.startScreens())
            assertEquals(MigrationOutcome.Verified, launch.verify())
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
    }

    @Test
    fun keystoreFailureWhileUnwrappingTheKeyIsRetriedWithinTheSameCheck() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        release3863Wallet(wallet)
        room(73, wallet to "Main")
        var failures = 2
        val flakyUnwrap = { key: PrivateKey, wrapped: ByteArray ->
            if (failures > 0) {
                failures -= 1
                throw IllegalBlockSizeException().apply {
                    initCause(java.security.ProviderException("Keystore operation failed"))
                }
            }
            Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, key)
                doFinal(wrapped)
            }
        }
        candidate(rsaDecryptor = flakyUnwrap) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(0, failures)
            assertNull(WalletUpgradeBackup.recoveryOptions())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
    }

    // ---- Released app processes -------------------------------------------------------------

    private suspend fun release3863Wallet(wallet: Fixture) {
        release3863 {
            created(wallet)
            finishedAccount(wallet)
            select(wallet)
            registrationFinished()
        }
    }

    private suspend fun release3863(
        block: suspend Release3863Process.() -> Unit,
    ): Map<String, Any> {
        val process = Release3863Process()
        return try {
            process.block()
            process.snapshot()
        } finally {
            process.end()
        }
    }

    private suspend fun release233(
        block: suspend Release233Process.() -> Unit,
    ): Map<String, Any> {
        val process = Release233Process()
        return try {
            process.block()
            process.snapshot()
        } finally {
            process.end()
        }
    }

    private suspend fun release240(
        block: suspend Release240Process.() -> Unit,
    ): Map<String, Any> {
        val process = Release240Process()
        return try {
            process.block()
            process.snapshot()
        } finally {
            process.end()
        }
    }

    /** "Remove account" in 3.8.6.3, optionally killed between its two steps. */
    private suspend fun releasedRemoval(address: String, killedAfterPreferences: Boolean = false) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            Release3863AccountRemoval(context, historicalDataStore(context, scope))
                .clearAccountData(address, killedAfterPreferences)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            delay(DATASTORE_RELEASE_DELAY_MS)
        }
    }

    private inner class Release240Process {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val dataStore = historicalDataStore(context, scope)
        private val prefs = Release240SoraPreferences(dataStore)
        private val encrypted = Release240EncryptedPreferences(prefs, Release3863EncryptionUtil(context))
        private val manager = Release240MigrationManager(
            userRepository = Release240UserRepository(context, Release240PrefsUserDatasource(prefs)),
            credentialsRepository = Release240CredentialsRepository(
                Release240PrefsCredentialsDatasource(encrypted, prefs),
                toSoraAddress = { publicKey -> requireNotNull(codec.toSoraAddressOrNull(publicKey)) },
            ),
        )

        /** The first start of 2.4.0 after the update from 2.3.x (Splash's MigrationManager). */
        suspend fun migrate() {
            assertTrue(manager.start())
        }

        suspend fun snapshot(): Map<String, Any> =
            dataStore.data.first().asMap().mapKeys { it.key.name }

        suspend fun end() {
            scope.coroutineContext.job.cancelAndJoin()
            context.getSharedPreferences("key_alias", Context.MODE_PRIVATE).edit().commit()
            delay(DATASTORE_RELEASE_DELAY_MS)
        }
    }

    private open inner class ReleasedProcess {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val dataStore = historicalDataStore(context, scope)
        val prefs = Release3863SoraPreferences(dataStore)
        val encryption = Release3863EncryptionUtil(context)
        val encrypted = Release3863EncryptedPreferences(prefs, encryption)

        suspend fun snapshot(): Map<String, Any> =
            dataStore.data.first().asMap().mapKeys { it.key.name }

        suspend fun end() {
            scope.coroutineContext.job.cancelAndJoin()
            // The released code saved the wrapped key with apply(); a process ends after that
            // asynchronous write reaches disk.
            context.getSharedPreferences("key_alias", Context.MODE_PRIVATE).edit().commit()
            delay(DATASTORE_RELEASE_DELAY_MS)
        }
    }

    private inner class Release3863Process : ReleasedProcess() {
        val credentials = Release3863PrefsCredentialsDatasource(encrypted, prefs)
        val user = Release3863PrefsUserDatasource(prefs, encrypted)

        /** CredentialsRepositoryImpl.generateEntropyAndKeysFromMnemonic at b7b0f0fc. */
        suspend fun created(wallet: Fixture) {
            credentials.saveKeys(wallet.keypair, wallet.address)
            credentials.saveMnemonic(requireNotNull(wallet.mnemonic), wallet.address)
        }

        /** CredentialsRepositoryImpl.generateEntropyAndKeysFromRawSeed at b7b0f0fc. */
        suspend fun importedSeed(wallet: Fixture) {
            credentials.saveKeys(wallet.keypair, wallet.address)
            credentials.saveSeed(wallet.seedHex, wallet.address)
        }

        /** MultiaccountInteractor.createUser / continueRecoverFlow at b7b0f0fc (preferences). */
        suspend fun finishedAccount(wallet: Fixture) {
            user.saveNeedsMigration(false, wallet.address)
            user.saveIsMigrationFetched(true, wallet.address)
        }

        suspend fun select(wallet: Fixture) = user.setCurAccountAddress(wallet.address)

        suspend fun registrationFinished() {
            user.saveRegistrationState("REGISTRATION_FINISHED")
            user.savePin("135790")
        }

        /** The first two of saveKeys' three separate edits. */
        suspend fun keysWithoutNonce(wallet: Fixture) {
            encrypted.putEncryptedString(
                "prefs_priv_key${wallet.address}",
                wallet.keypair.privateKey.toHexString(),
            )
            encrypted.putEncryptedString(
                "prefs_pub_key${wallet.address}",
                wallet.keypair.publicKey.toHexString(),
            )
        }
    }

    private inner class Release233Process : ReleasedProcess() {
        val credentials = Release233PrefsCredentialsDatasource(encrypted, prefs)
        val user = Release233PrefsUserDatasource(prefs, encrypted)

        /** CredentialsRepositoryImpl.generateEntropyAndKeys + onboarding at release/sora/2.3.3. */
        suspend fun install(wallet: Fixture, name: String) {
            credentials.saveAddress(wallet.address)
            credentials.saveKeys(wallet.keypair)
            credentials.saveMnemonic(requireNotNull(wallet.mnemonic))
            user.saveAccountName(name)
            user.saveNeedsMigration(false)
            user.saveIsMigrationFetched(true)
            user.saveRegistrationState("REGISTRATION_FINISHED")
            user.savePin("135790")
        }
    }

    private fun room(version: Int, vararg accounts: Pair<Fixture, String>) {
        HistoricalRoom.create(context, instrumentationContext, version)
        if (accounts.isNotEmpty()) {
            HistoricalRoom.insertAccounts(context, accounts.map { (wallet, name) -> wallet.address to name })
        }
    }

    // ---- The 3.9.0 app process ---------------------------------------------------------------

    private suspend fun candidate(
        prepareFirst: Boolean = true,
        keyStoreFactory: (() -> KeyStore)? = null,
        rsaDecryptor: ((PrivateKey, ByteArray) -> ByteArray)? = null,
        newProcess: Boolean = false,
        block: suspend (CandidateProcess) -> Unit,
    ) {
        if (newProcess) {
            // HARNESS: a new OS process has no SharedPreferences cache and runs the first
            // preparation of the process (where a "set up again" request is carried out).
            WalletUpgradeBackup.simulateProcessStartForTest()
        }
        val process = CandidateProcess(prepareFirst, keyStoreFactory, rsaDecryptor)
        try {
            block(process)
        } finally {
            process.end()
        }
    }

    private inner class CandidateProcess(
        prepareFirst: Boolean,
        keyStoreFactory: (() -> KeyStore)?,
        rsaDecryptor: ((PrivateKey, ByteArray) -> ByteArray)?,
    ) {
        // Application.attachBaseContext
        val prepare: Result<Unit> =
            if (prepareFirst) WalletUpgradeBackup.prepare(context) else Result.success(Unit)
        init {
            forgetWrappedKeyCacheIfFileIsGone()
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore: DataStore<Preferences> = SoraPreferences.createDataStore(context, scope)
        val soraPreferences = SoraPreferences(dataStore)
        private val encryptionUtil = when {
            rsaDecryptor != null -> EncryptionUtil(
                context = context,
                keyStoreFactory = keyStoreFactory ?: { KeyStore.getInstance("AndroidKeyStore") },
                rsaDecryptor = rsaDecryptor,
            )
            keyStoreFactory != null -> EncryptionUtil(context, keyStoreFactory)
            else -> EncryptionUtil(context)
        }
        private val encryptedPreferences = EncryptedPreferences(soraPreferences, encryptionUtil)
        val credentialsDatasource = PrefsCredentialsDatasource(encryptedPreferences, soraPreferences)
        val userDatasource = PrefsUserDatasource(soraPreferences, encryptedPreferences)
        val credentials = CredentialsRepositoryImpl(
            credentialsDatasource,
            mockk(relaxed = true),
            codec,
            mockk(relaxed = true),
        )
        private var databaseOpened = false
        val database: AppDatabase by lazy {
            databaseOpened = true
            AppDatabase.newInstance(context)
        }
        val userRepository by lazy {
            UserRepositoryImpl(
                userDatasource,
                credentialsDatasource,
                database,
                CoroutineManager(),
                LanguagesHolder(),
                codec,
                UserRepositorySr25519Crypto(),
            )
        }
        private val migrationManager by lazy {
            MigrationManager(
                userRepository = userRepository,
                credentialsRepository = credentials,
                sora2AddressCodec = codec,
                database = database,
                migrationSr25519Crypto = MigrationSr25519Crypto(),
            )
        }

        suspend fun verify(): MigrationOutcome = migrationManager.startWithOutcome()

        /** SplashInteractor.checkMigration: the app's screens start the verification. */
        fun startScreens(): Boolean = WalletUpgradeBackup.authorizeAutomaticMigrationRetry(context)

        /** The confirmed "Remove account" flow (preview, then confirm). */
        suspend fun remove(vararg addresses: String) {
            val preview = userRepository.createWalletDeletionPreview(addresses.toList())
            userRepository.confirmAndExecuteWalletDeletion(preview)
        }

        suspend fun identity(wallet: Fixture) =
            requireNotNull(database.walletIdentityDao().getWallet(wallet.address))

        /** MultiaccountInteractor.importFromMnemonic (importDraft), step for step. */
        suspend fun importPhrase(phrase: String, name: String): SoraAccount {
            val draft = credentials.draftFromMnemonic(phrase, name)
            try {
                if (userRepository.accountExists(draft.address)) {
                    check(draft.address in userRepository.unavailableWalletIds()) {
                        "ACCOUNT_ALREADY_IMPORTED"
                    }
                    // A read-only account: complete its keys (add-only), then verify it.
                    credentials.commitDraft(draft)
                    val repaired = userRepository.completeUnavailableWallet(draft.address)
                    userRepository.setCurSoraAccount(repaired)
                    userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
                    return repaired
                }
                val account = credentials.commitDraft(draft)
                userRepository.insertSoraAccount(account, false)
                userRepository.setCurSoraAccount(account)
                userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
                return account
            } finally {
                draft.destroy()
            }
        }

        suspend fun assertUsable(
            expected: List<Fixture>,
            unavailable: List<String> = emptyList(),
            selected: Fixture? = null,
        ) {
            assertEquals(
                (expected.map(Fixture::address) + unavailable).toSet(),
                database.accountDao().getAccounts().map(SoraAccountLocal::substrateAddress)
                    .filter(String::isNotBlank)
                    .toSet(),
            )
            assertEquals(unavailable.toSet(), userRepository.unavailableWalletIds())
            expected.forEach { wallet ->
                assertTrue(wallet.address, WalletSigningGate.isAvailable(database, wallet.address))
                val stored = credentials.retrieveKeyPair(SoraAccount(wallet.address, ""))
                try {
                    assertArrayEquals(wallet.keypair.publicKey, stored.publicKey)
                    assertEquals(wallet.address, codec.toSoraAddressOrNull(stored.publicKey))
                    val message = "sora-wallet-upgrade-${wallet.address}".encodeToByteArray()
                    val signature = SignWrapper.sign(
                        MultiChainEncryption.Substrate(EncryptionType.SR25519),
                        message,
                        stored,
                    ).signature
                    assertTrue(
                        "signature for ${wallet.address}",
                        Sr25519JNI.verify(
                            signature,
                            message,
                            requireNotNull(codec.soraPublicKeyOrNull(wallet.address)),
                        ),
                    )
                } finally {
                    stored.privateKey.fill(0)
                    stored.nonce.fill(0)
                }
            }
            unavailable.forEach { address ->
                assertFalse(address, WalletSigningGate.isAvailable(database, address))
            }
            selected?.let {
                assertEquals(it.address, userRepository.getCurSoraAccount().substrateAddress)
            }
        }

        /** Every value the released app wrote is still present, byte for byte. */
        suspend fun assertCiphertextsKept(before: Map<String, Any>) {
            val after = dataStore.data.first().asMap().mapKeys { it.key.name }
            before.filterKeys { key ->
                jp.co.soramitsu.common.data.WalletPreferenceKeys.isRetainedKeyMaterial(key) ||
                    key == "user_pin_code"
            }.forEach { (key, value) ->
                assertEquals(key, value, after[key])
            }
        }

        suspend fun end() {
            if (databaseOpened) database.close()
            scope.coroutineContext.job.cancelAndJoin()
            delay(DATASTORE_RELEASE_DELAY_MS)
        }
    }

    // ---- Fixtures and raw storage helpers -----------------------------------------------------

    private class Fixture(
        val address: String,
        val keypair: Sr25519Keypair,
        val mnemonic: String?,
        val seedHex: String,
    )

    private fun phraseWallet(phrase: String): Fixture {
        val words = MnemonicCreator.fromWords(phrase).words
        val seed = SubstrateSeedFactory.deriveSeed32(words, null).seed
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seed) as Sr25519Keypair
        return Fixture(
            address = requireNotNull(codec.toSoraAddressOrNull(keypair.publicKey)),
            keypair = keypair,
            mnemonic = words,
            seedHex = seed.toHexString(),
        )
    }

    private fun rawSeedWallet(seedHex: String): Fixture {
        val seed = seedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seed) as Sr25519Keypair
        return Fixture(
            address = requireNotNull(codec.toSoraAddressOrNull(keypair.publicKey)),
            keypair = keypair,
            mnemonic = null,
            seedHex = seedHex,
        )
    }

    private fun backups(): List<File> = context.noBackupFilesDir.listFiles().orEmpty()
        .filter { it.name.startsWith("wallet-upgrade-backup-v") }

    /**
     * HARNESS: models a new OS process. Android caches SharedPreferences per process; when the
     * wrapped-key file was renamed aside, a new process starts without it.
     */
    private fun forgetWrappedKeyCacheIfFileIsGone() {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/key_alias.xml")
        if (!file.exists()) {
            context.getSharedPreferences("key_alias", Context.MODE_PRIVATE).edit().clear().commit()
            file.delete()
        }
    }

    private fun deleteKeystoreAlias() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("key_alias")
    }

    private fun dataStoreFile(): File =
        File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb")

    private fun setUpAgainDirectory(): File = context.noBackupFilesDir.listFiles().orEmpty()
        .single { it.name.startsWith("wallet-quarantine-") && it.name.endsWith("-set-up-again") }

    private fun databaseVersion(): Int = SQLiteDatabase.openDatabase(
        context.getDatabasePath("app.db").path,
        null,
        SQLiteDatabase.OPEN_READONLY,
    ).use { it.version }

    private fun updateDatabase(sql: String) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath("app.db").path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { it.execSQL(sql) }
    }

    private fun writeJournal(
        state: String,
        accounts: List<Pair<String, String>>,
        selected: String,
        failureCode: String? = null,
        startedAt: Long = System.currentTimeMillis(),
    ) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath("app.db").path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(
                """
                INSERT OR REPLACE INTO walletMigrationJournal(
                    migrationId, state, legacyAccountCount, verifiedAccountCount,
                    selectedWalletId, integrityHash, failureCode, startedAt, completedAt
                ) VALUES(?, ?, ?, 0, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                    state,
                    accounts.size,
                    selected,
                    WalletMigrationIntegrity.snapshotHashPairs(accounts, selected),
                    failureCode,
                    startedAt,
                    if (state == "RECOVERY_REQUIRED") startedAt + 1 else null,
                ),
            )
        }
    }

    private companion object {
        const val DATASTORE_RELEASE_DELAY_MS = 50L
        const val TWELVE_WORDS =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val TWELVE_WORDS_ADDRESS = "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
        const val TWENTY_FOUR_WORDS =
            "abandon amount liar amount expire adjust cage candy arch gather drum bullet absurd " +
                "math era live bid rhythm alien crouch range attend journey unaware"
        const val TWENTY_FOUR_WORDS_ADDRESS = "cnTAiyqa6dqfhfX5HhJfmPdd56sum2WXFvg1i1UgRReB336yz"
        // Non-zero entropy: all-zero entropies of different lengths derive the same SORA2 seed
        // (PBKDF2 pads the short HMAC key), so "abandon ... address" would collide with the
        // 12-word vector.
        const val FIFTEEN_WORDS =
            "adapt explain ecology dinner glare old unfair empty expect road off suggest dash " +
                "dolphin case"
        const val TWENTY_ONE_WORDS =
            "adapt explain ecology dinner glare old unfair empty expect road off suggest dash " +
                "dolphin captain version oval census uncover inform apology"
        const val RAW_SEED = "cf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
    }
}
