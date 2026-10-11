package jp.co.soramitsu.feature_account_impl.upgrade

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import io.mockk.mockk
import java.io.File
import java.security.KeyPair
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
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.common.account.SelectedAccountSigning
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.common.presentation.viewmodel.FailureNotice
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.SetUpAgainScope
import jp.co.soramitsu.core_db.WalletMigrationIntegrity
import jp.co.soramitsu.core_db.WalletQuarantine
import jp.co.soramitsu.core_db.WalletRecoveryKind
import jp.co.soramitsu.core_db.WalletSigningGate
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.RepairPinLockedException
import jp.co.soramitsu.feature_account_api.domain.model.RetainedWallet
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.feature_account_api.domain.model.WalletRepairAvailableException
import jp.co.soramitsu.feature_account_impl.data.repository.CredentialsRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositorySr25519Crypto
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsCredentialsDatasource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsUserDatasource
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationManager
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationOutcome
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationSr25519Crypto
import jp.co.soramitsu.feature_account_impl.historical.HistoricalSr25519Vectors
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor.BackupRestore
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.AccountListViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ReadOnlyAccountUi
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.TypeCoderFactory
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.encode
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
        assertTrue(options.canRetry && options.retryAutomatically)
        // Freeing space and checking again is the way forward; nothing is set aside.
        assertFalse(options.canSetUpAgain || options.canRestoreFromPhrase)
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
            assertTrue(options.retryAutomatically)
            // Nothing decrypts while the Keystore is busy (the main screen's PIN check
            // included), and nothing is set aside for a state that passes on its own.
            assertFalse(options.canReadOnly || options.canSetUpAgain)
            assertEquals(
                WalletUpgradeBackup.SafeReadOnlyRoute.VAULT_ONLY,
                WalletUpgradeBackup.authorizeSafeReadOnly(context).getOrThrow(),
            )

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
            // Import account, Google: the backup of the read-only account is offered, although
            // the account is listed on this phone. Only the account that works is left out.
            assertEquals(
                mapOf(partial.address to BackupRestore.REPAIRS_READ_ONLY_ACCOUNT),
                launch.backupRestores(main, partial),
            )

            // The person imports the account's own phrase (typed, or restored from that backup):
            // only the missing nonce is added.
            launch.importPhrase(TWENTY_FOUR_WORDS, "Typed name")
            launch.assertUsable(listOf(main, partial), selected = partial)
            launch.assertCiphertextsKept(before)
            assertEquals("Partial", launch.userRepository.getCurSoraAccount().accountName)
            assertNull(launch.userRepository.unavailableWalletOptions(partial.address))
            // It works again, so its backup is not offered a second time.
            assertTrue(launch.backupRestores(main, partial).isEmpty())
        }
        // The next start verifies it like every other account.
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(main, partial), selected = partial)
        }
    }

    @Test
    fun undecryptableAccountIsRepairedInPlaceWithItsPhrase() = runBlocking {
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
            // The journal counts only the account that can sign.
            assertEquals(2, launch.journal().legacyAccountCount)
            assertEquals(1, launch.journal().verifiedAccountCount)
            val options = requireNotNull(launch.userRepository.unavailableWalletOptions(broken.address))
            assertTrue(options.canRepairWithPhrase)
            assertTrue(options.repairReplacesStoredValues)
            // Import account, Google: the backup of the read-only account is offered, next to
            // the backup of a wallet that is not on this phone. The account that works is not.
            val notHere = rawSeedWallet(RAW_SEED)
            assertEquals(
                mapOf(
                    broken.address to BackupRestore.REPAIRS_READ_ONLY_ACCOUNT,
                    notHere.address to BackupRestore.ADDS_ACCOUNT,
                ),
                launch.backupRestores(first, broken, notHere),
            )
            // Looking at that list changes nothing.
            launch.assertCiphertextsKept(before)
            launch.assertUsable(listOf(first), unavailable = listOf(broken.address), selected = first)

            // Accounts, batch export: one backup file for both accounts is refused, and the
            // refusal names the account that has to be left out. No file is written.
            val refused = runCatching {
                launch.interactor.getJsonFileUri(listOf(first.address, broken.address), JSON_PASSWORD)
            }.exceptionOrNull()
            assertEquals(
                listOf(broken.address),
                (refused as? MultiaccountInteractor.AccountsNotExportableException)
                    ?.accounts?.map(SoraAccount::substrateAddress),
            )
            launch.assertCiphertextsKept(before)

            // Importing the phrase never asks to remove the account. A stored value cannot be
            // read, so nothing is written and the confirmed repair is offered.
            val offer = runCatching { launch.importPhrase(TWENTY_FOUR_WORDS, "Broken") }
                .exceptionOrNull() as WalletRepairAvailableException
            assertTrue(offer.accountExists && offer.pinRequired)
            assertEquals(broken.address, offer.address)
            launch.assertCiphertextsKept(before)
            assertTrue(repairCopies().isEmpty())

            // A wrong PIN changes nothing.
            val wrongPin = runCatching { launch.repairWithPhrase(TWENTY_FOUR_WORDS, "Broken", "000000") }
                .exceptionOrNull()
            assertEquals(ResponseCode.WRONG_PIN_CODE, (wrongPin as? SoraException)?.errorResponseCode)
            launch.assertCiphertextsKept(before)

            // Confirmed with the PIN: a copy of the old values is kept, then the keys the phrase
            // derives are written for this account only.
            launch.repairWithPhrase(TWENTY_FOUR_WORDS, "Typed name", "135790")
            val copy = repairCopies().single()
            assertTrue(File(copy, "sora_prefs_datastore.preferences_pb").readBytes().isNotEmpty())
            launch.assertUsable(listOf(first, broken), selected = broken)
            assertEquals("Broken", launch.userRepository.getCurSoraAccount().accountName)
            launch.assertCiphertextsKept(before.filterKeys { !it.endsWith(broken.address) })
            assertNull(launch.userRepository.unavailableWalletOptions(broken.address))
            // Repaired: only the wallet that is not on this phone is still offered.
            assertEquals(
                mapOf(notHere.address to BackupRestore.ADDS_ACCOUNT),
                launch.backupRestores(first, broken, notHere),
            )
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, broken), selected = broken)
            assertEquals(2, launch.journal().verifiedAccountCount)
        }
    }

    @Test
    fun undecryptableAccountCanStillBeRemovedWhenThePersonChoosesTo() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val broken = phraseWallet(TWENTY_FOUR_WORDS)
        release3863 {
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
    fun phoneTransferWithoutTheKeystoreKeyNeverOpensTheMainScreenAndSetsUpAgain() = runBlocking {
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
            // The main screen opens with the PIN check, which decrypts the stored PIN. On this
            // phone that cannot work, so read-only is not offered (it would crash there).
            val pinError = runCatching { launch.userRepository.retrievePin() }.exceptionOrNull()
            assertTrue(pinError is KeyMaterialUnavailableException)
            val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
            assertFalse(options.canReadOnly)
            assertFalse(options.canShowPhraseAfterPin)
            assertEquals(
                WalletUpgradeBackup.SafeReadOnlyRoute.VAULT_ONLY,
                WalletUpgradeBackup.authorizeSafeReadOnly(context).getOrThrow(),
            )
            // Forward paths: Try again, support, and setting the phone up again.
            assertTrue(options.canRetry && options.canContactSupport && options.canSetUpAgain)
            assertEquals(SetUpAgainScope.ALL_WALLET_FILES, options.setUpAgainScope)
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
    fun damagedDatabaseSetUpAgainKeepsEveryKeyAndBothWalletsComeBack() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        // Imported from a raw seed: no phrase was ever written down.
        val second = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            created(first)
            importedSeed(second)
            finishedAccount(first)
            finishedAccount(second)
            select(first)
            registrationFinished()
        }
        room(73, first to "First", second to "Second")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = first)
        }
        val keyFiles = listOf(
            dataStoreFile(),
            File(context.applicationInfo.dataDir, "shared_prefs/key_alias.xml"),
        )
        val keyBytes = keyFiles.associateWith { it.readBytes() }

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            // SQLite reports corruption on the open database.
            WalletUpgradeBackup.noteDatabaseCorruption(context)
            val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
            assertEquals(WalletRecoveryKind.INTEGRITY, options.kind)
            // Only the database (no secrets in it) is set aside; the keys stay in place.
            assertEquals(SetUpAgainScope.DATABASE_ONLY, options.setUpAgainScope)
            assertTrue(options.canRestoreFromPhrase && options.restoreNeedsSetUpAgain)
            assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isSuccess)
        }
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertTrue(File(setUpAgainDirectory(), "databases/app.db").isFile)
            assertFalse(File(setUpAgainDirectory(), "files").exists())
            keyBytes.forEach { (file, bytes) -> assertArrayEquals(file.path, bytes, file.readBytes()) }
            // Both wallets are offered again.
            assertEquals(
                setOf(first.address, second.address),
                WalletUpgradeBackup.quarantine().retainedWalletIds,
            )
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(0, launch.userRepository.getSoraAccountsCount())

            // The written-down phrase re-uses the stored keys after proving they match...
            launch.importPhrase(TWELVE_WORDS, "First")
            // ...and the seed wallet, whose phrase was never written down, is restored from the
            // keys kept on this phone ("Restore wallets found on this phone" in Login & Security,
            // which keeps the wallet the person is using selected).
            launch.userRepository.adoptRetainedWallet(second.address, "Second", makeCurrent = false)
            launch.assertUsable(listOf(first, second), selected = first)
            launch.assertCiphertextsKept(before)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = first)
        }
    }

    @Test
    fun interruptedVerificationWhoseRetryCannotBeSavedShowsRecoveryWithoutOpeningTheDatabase() =
        runBlocking {
            val wallet = phraseWallet(TWELVE_WORDS)
            release3863Wallet(wallet)
            room(73, wallet to "Main")
            candidate { launch ->
                assertTrue(launch.prepare.isSuccess)
                launch.database.openHelper.writableDatabase
            }
            // Killed while verifying on a nearly full phone: the VERIFYING journal remains.
            writeJournal(
                state = "VERIFYING",
                accounts = listOf(wallet.address to "Main"),
                selected = wallet.address,
            )
            // The automatic retry's counter cannot be saved (as with no free space left).
            val blocker = File(context.noBackupFilesDir, ".wallet-migration-auto-retry.tmp")
            assertTrue(blocker.mkdirs() && File(blocker, "occupied").createNewFile())

            candidate(newProcess = true) { launch ->
                assertTrue(launch.prepare.isFailure)
                // SplashActivity: the automatic retry is still expected...
                assertNull(WalletUpgradeBackup.startupFailureToShow(context))
                // ...SplashInteractor: it cannot be authorized.
                assertFalse(launch.startScreens())
                // The database stays closed to every reader.
                assertTrue(runCatching { launch.verify() }.isFailure)
                assertTrue(
                    runCatching { launch.database.walletIdentityDao().getWallets() }.isFailure
                )
                // The recovery screen's code is read without opening the database (this used to
                // crash the app on every start), and the options are shown at once.
                assertEquals("WALLET_MIGRATION_RECOVERY_REQUIRED", launch.failureCode())
                assertEquals(
                    "WALLET_MIGRATION_RECOVERY_REQUIRED",
                    WalletUpgradeBackup.startupFailureToShow(context)?.code,
                )
                assertTrue(requireNotNull(WalletUpgradeBackup.recoveryOptions()).canRetry)

                // Try again does not need the counter.
                assertTrue(WalletUpgradeBackup.retry(context).isSuccess)
                assertEquals(MigrationOutcome.Verified, launch.verify())
                launch.assertUsable(listOf(wallet), selected = wallet)
            }
            assertTrue(blocker.deleteRecursively())
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
    fun release240AccountWithUnreadableCopyKeepsItsReleaseEraKeysWhenRemoved() = runBlocking {
        val original = phraseWallet(TWELVE_WORDS)
        val added = phraseWallet(TWENTY_FOUR_WORDS)
        val legacy = release233 { install(original, name = "Original") }
        room(58)
        // 2.4.0 copied the unsuffixed keys and phrase to address-scoped values and kept the
        // unsuffixed ones with prefs_address_pure = the address.
        release240 { migrate() }
        release3863 {
            created(added)
            finishedAccount(added)
            // The address-scoped private key and phrase no longer decrypt.
            prefs.putString("prefs_priv_key${original.address}", UNDECRYPTABLE)
            prefs.putString("prefs_mnemonic${original.address}", UNDECRYPTABLE)
        }
        HistoricalRoom.insertAccounts(context, listOf(added.address to "Added"))

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(added), unavailable = listOf(original.address))
            val options =
                requireNotNull(launch.userRepository.unavailableWalletOptions(original.address))
            // Importing its phrase repairs it in place (confirmed, because stored values of this
            // account cannot be read); it never has to be removed for that.
            assertTrue(options.canRepairWithPhrase && options.repairReplacesStoredValues)
            // The release-era phrase belongs to this account (its keys derive the address).
            assertTrue(options.canShowPhraseAfterPin)
            assertTrue(options.hasReleaseEraCopy)
            assertTrue(options.canRemoveAndImport)
            assertFalse(options.showPhraseBeforeRemoval)
            assertEquals(
                original.mnemonic,
                launch.credentials.retrieveMnemonic(SoraAccount(original.address, "")),
            )

            // The person removes the read-only account. Its release-era keys are kept.
            launch.remove(original.address)
            launch.assertUsable(listOf(added), selected = added)
            launch.assertCiphertextsKept(legacy)
            assertEquals(original.address, launch.credentialsDatasource.getAddress())

            // "Restore wallets found on this phone" brings it back without the phrase.
            launch.userRepository.adoptRetainedWallet(original.address, "Original")
            launch.assertUsable(listOf(original, added), selected = original)
            launch.assertCiphertextsKept(legacy)
        }
    }

    @Test
    fun removingAnAccountKeepsReleaseEraKeysThatBelongToAnotherAddress() = runBlocking {
        val stale = phraseWallet(TWELVE_WORDS)
        val first = phraseWallet(TWENTY_FOUR_WORDS)
        val second = rawSeedWallet(RAW_SEED)
        // 2.3.3 left unsuffixed keys of one wallet...
        val legacy = release233 { install(stale, name = "Old") }
        // ...and prefs_address_pure later named a different account.
        release3863 {
            created(first)
            importedSeed(second)
            finishedAccount(first)
            finishedAccount(second)
            select(first)
            registrationFinished()
            prefs.putString("prefs_address_pure", first.address)
        }
        room(73, first to "First", second to "Second")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = first)

            launch.remove(first.address)
            launch.assertUsable(listOf(second), selected = second)
            // The unsuffixed keys derive another address: they were not the removed account's
            // and are kept byte for byte.
            launch.assertCiphertextsKept(
                legacy.filterKeys { it != "prefs_address_pure" && it != "user_pin_code" }
            )
            launch.userRepository.adoptRetainedWallet(stale.address, "Old")
            launch.assertUsable(listOf(stale, second), selected = stale)
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

    @Test
    fun release300SharedPreferencesWalletUpgradesAndSigns() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        // SORA 3.0.0 (Aug 2021): plain SharedPreferences, address in prefs_address_pure, Room 44.
        val before = release2021 { restored(wallet, name = "Main wallet", plainAddress = true) }
        val originalPreferences = soraPrefsXml().readBytes()
        room(44)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(WalletQuarantine.NONE, WalletUpgradeBackup.quarantine())
            // The released preference file is in the pre-migration backup, byte for byte, before
            // anything opens the DataStore or Room.
            assertArrayEquals(
                originalPreferences,
                File(preMigrationBackup(44), "shared_prefs/sora_prefs.xml").readBytes(),
            )
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
            assertEquals(
                listOf(SoraAccountLocal(wallet.address, "Main wallet")),
                launch.database.accountDao().getAccounts(),
            )
            launch.assertCiphertextsKept(before)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun release210SharedPreferencesWalletWithEncryptedAddressUpgradesAndSigns() = runBlocking {
        val wallet = phraseWallet(TWENTY_FOUR_WORDS)
        // SORA 2.1.0 (Jun 2021): the address is stored encrypted under prefs_address, no account
        // name was ever saved, Room 40.
        val before = release2021 { restored(wallet, name = null, plainAddress = false) }
        val originalPreferences = soraPrefsXml().readBytes()
        room(40)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(WalletQuarantine.NONE, WalletUpgradeBackup.quarantine())
            assertArrayEquals(
                originalPreferences,
                File(preMigrationBackup(40), "shared_prefs/sora_prefs.xml").readBytes(),
            )
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
            assertEquals(
                listOf(wallet.address),
                launch.database.accountDao().getAccounts().map(SoraAccountLocal::substrateAddress),
            )
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun soraV1IrohaWalletOpensAtOnboardingKeepsItsPhraseAndTheSamePhraseImports() = runBlocking {
        // SORA v1 (Iroha, 1.6.1): unsuffixed Ed25519 DID keys without a nonce, a 15-word phrase,
        // no SORA2 address, Room 33. No SORA2 account can be made from these keys.
        assertEquals(32, V1_DID_KEY_PAIR.private.encoded.size)
        assertEquals(32, V1_DID_KEY_PAIR.public.encoded.size)
        val before = release161 { registered(FIFTEEN_WORDS) }
        assertFalse("v1 stored no nonce", "prefs_key_nonce" in before)
        val originalPreferences = soraPrefsXml().readBytes()
        room(33)
        val irohaAddressLookalike = requireNotNull(codec.toSoraAddressOrNull(V1_DID_PUBLIC_KEY))
        val sora2 = phraseWallet(FIFTEEN_WORDS)

        candidate { launch ->
            // Never blocked: the v1 material is set aside, untouched, and the backup holds it.
            assertTrue(launch.prepare.isSuccess)
            assertTrue(WalletUpgradeBackup.quarantine().unscopedMaterialWithoutRecordedOwner)
            assertTrue(WalletUpgradeBackup.quarantine().retainedWalletIds.isEmpty())
            assertTrue(WalletUpgradeBackup.recoveryFacts().hasUnscopedPhraseOrSeed)
            assertNull(WalletUpgradeBackup.recoveryFacts().legacyOwner)
            assertArrayEquals(
                originalPreferences,
                File(preMigrationBackup(33), "shared_prefs/sora_prefs.xml").readBytes(),
            )
            assertEquals(MigrationOutcome.Verified, launch.verify())
            // Registration had finished in v1, but there is no SORA2 account: onboarding.
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            assertEquals(
                OnboardingState.REGISTRATION_FINISHED,
                launch.userDatasource.retrieveRegistratrionState(),
            )
            launch.assertCiphertextsKept(before)

            // "Restore wallets found on this phone" cannot turn the Ed25519 key into a SORA2
            // account: refused, nothing written, no crash.
            val adoption = runCatching {
                launch.userRepository.adoptRetainedWallet(irohaAddressLookalike, "v1", true)
            }
            // The v1 key pair has no sr25519 nonce: it is not a complete SORA2 signing key.
            assertEquals("SIGNING_KEY_INCOMPLETE", adoption.exceptionOrNull()?.message)
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            launch.assertCiphertextsKept(before)

            // Safe Mode's "Show my recovery phrase" reads the v1 phrase (read only).
            assertEquals(FIFTEEN_WORDS, launch.credentialsDatasource.retrieveMnemonic(""))

            // Importing that phrase gives its SORA2 account (as 3.8.6.3 did), which signs.
            launch.importPhrase(FIFTEEN_WORDS, "Restored")
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(sora2), selected = sora2)
            launch.assertCiphertextsKept(before)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(sora2), selected = sora2)
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun everySora2PhraseLengthImportsOnANewInstallAndSigns() = runBlocking {
        val phrases = listOf(
            TWELVE_WORDS,
            FIFTEEN_WORDS,
            EIGHTEEN_WORDS,
            TWENTY_ONE_WORDS,
            TWENTY_FOUR_WORDS,
        )
        val wallets = phrases.map(::phraseWallet)
        assertEquals(listOf(12, 15, 18, 21, 24), phrases.map { it.split(" ").size })
        assertEquals(phrases.size, wallets.map(Fixture::address).toSet().size)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            phrases.forEachIndexed { index, phrase ->
                launch.importPhrase(phrase, "Imported ${phrase.split(" ").size}")
                assertEquals(wallets[index].address, launch.userRepository.getCurSoraAccount().substrateAddress)
            }
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(wallets, selected = wallets.last())
            // Nexus derivation stays 12/24 only; the other lengths are SORA2 only.
            listOf(1, 2, 3).forEach { index ->
                assertEquals("MNEMONIC_UNSUPPORTED", launch.identity(wallets[index]).secretSource)
            }
        }
    }

    // ---- Released app processes -------------------------------------------------------------

    // ---- What could sign before must sign after (audit A1, A3 and the whole class) -------------

    @Test
    fun seedCachedAs64BytesBy3820KeepsSigningAndReadsAs32Bytes() = runBlocking {
        val affected = phraseWallet(TWELVE_WORDS)
        val control = phraseWallet(TWENTY_FOUR_WORDS)
        release3863 {
            created(affected)
            created(control)
            finishedAccount(affected)
            finishedAccount(control)
            select(affected)
            registrationFinished()
            // On 3.8.2.0 the person opened "Show raw seed": the uncropped value was cached.
            assertEquals(128, showRawSeedOn3820(affected).length)
        }
        // 3.8.6.3 keeps that value as it is (it never compares it) and caches 32 bytes for an
        // account first viewed there.
        val before = release3863 {
            assertEquals(128, showRawSeedOn3863(affected).length)
            assertEquals(64, showRawSeedOn3863(control).length)
        }
        room(73, affected to "Affected", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(affected, control), selected = affected)
            launch.assertCiphertextsKept(before)
            assertEquals("MNEMONIC", launch.identity(affected).secretSource)
            assertEquals(2, launch.journal().verifiedAccountCount)
            assertEquals("MATCHES", launch.report(affected).seed)
            assertTrue("SEED_STORED_AS_64_BYTES" in launch.report(affected).diagnostics)
            assertEquals(launch.networkIds(control), launch.networkIds(affected))

            // Display, export and backup get the canonical 32 bytes; the stored value stays.
            assertEquals(
                affected.seedHex,
                launch.credentials.retrieveSeed(SoraAccount(affected.address, "")),
            )
            val material = launch.backupMaterial(affected)
            assertEquals(affected.seedHex, material.seedHex)
            assertEquals(TWELVE_WORDS.split(" "), material.phraseWords)
            // The Raw seed screen (one judgement that carries the seed) shows the same 32 bytes
            // and, with a seed already stored, writes nothing.
            assertEquals(
                affected.seedHex,
                launch.interactor.getSeedBackupMaterial(affected.address).seedHex,
            )
            assertEquals(128, launch.credentialsDatasource.retrieveSeed(affected.address).length)
            launch.assertCiphertextsKept(before)

            // Importing the same phrase says the wallet is already here. Nothing is removed.
            val again = runCatching { launch.importPhrase(TWELVE_WORDS, "Again") }.exceptionOrNull()
            assertEquals(
                ResponseCode.ACCOUNT_ALREADY_IMPORTED,
                (again as? SoraException)?.errorResponseCode,
            )
            // The account can be removed like any other: the removal check accepts the seed.
            launch.userRepository.createWalletDeletionPreview(listOf(control.address))
            launch.assertCiphertextsKept(before)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(affected, control), selected = affected)
        }
    }

    @Test
    fun seed64KeySetWithoutAccountRowIsRestoredFromThePhone() = runBlocking {
        val orphan = phraseWallet(TWELVE_WORDS)
        val before = release3863 {
            created(orphan)
            finishedAccount(orphan)
            select(orphan)
            registrationFinished()
            assertEquals(128, showRawSeedOn3820(orphan).length)
        }
        // The database was lost (a database-only restore): the keys have no account row.
        room(73)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertTrue(orphan.address in WalletUpgradeBackup.quarantine().retainedWalletIds)
            // "Restore this wallet" in Login & Security.
            launch.userRepository.adoptRetainedWallet(orphan.address, "Restored wallet")
            launch.assertUsable(listOf(orphan), selected = orphan)
            launch.assertCiphertextsKept(before)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(orphan), selected = orphan)
        }
    }

    @Test
    fun seed64KeySetWithoutAccountRowIsAdoptedByImportingItsPhrase() = runBlocking {
        val orphan = phraseWallet(TWELVE_WORDS)
        val before = release3863 {
            created(orphan)
            finishedAccount(orphan)
            select(orphan)
            registrationFinished()
            assertEquals(128, showRawSeedOn3820(orphan).length)
        }
        room(73)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.importPhrase(TWELVE_WORDS, "Imported")
            launch.assertUsable(listOf(orphan), selected = orphan)
            // Adopted unchanged: nothing was rewritten, the 64-byte value included.
            launch.assertCiphertextsKept(before)
            assertTrue(repairCopies().isEmpty())
        }
    }

    @Test
    fun phraseStoredWithCommasBy232UpgradesAndSigns() =
        commaPhraseStoredBy232(typed = TWELVE_WORDS.replace(" ", ","), promoted = false)

    @Test
    fun phraseStoredWithCommaAndSpaceBy232UpgradesAndSigns() =
        commaPhraseStoredBy232(typed = TWELVE_WORDS.replace(" ", ", "), promoted = false)

    @Test
    fun phraseStoredWithCommasBy232AndPromotedBy240UpgradesAndSigns() =
        commaPhraseStoredBy232(typed = TWELVE_WORDS.replace(" ", ","), promoted = true)

    @Test
    fun phraseStoredWithCommaAndSpaceBy232AndPromotedBy240UpgradesAndSigns() =
        commaPhraseStoredBy232(typed = TWELVE_WORDS.replace(" ", ", "), promoted = true)

    private fun commaPhraseStoredBy232(typed: String, promoted: Boolean) = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        assertEquals(TWELVE_WORDS_ADDRESS, wallet.address)
        var before = release233 { restoredOn232(wallet, typed = typed, name = "Main wallet") }
        if (promoted) {
            // 2.4.0 copied the unsuffixed values, the phrase text included, to suffixed keys.
            room(58)
            before = before + release240 { migrate() }
        } else {
            room(50)
        }

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
            launch.assertCiphertextsKept(before)
            assertEquals("MNEMONIC", launch.identity(wallet).secretSource)
            assertEquals("MATCHES", launch.report(wallet).phrase)
            assertEquals(1, launch.journal().verifiedAccountCount)
            // The stored text keeps its commas; what is shown and derived uses the parsed words.
            assertEquals(typed, launch.credentials.retrieveMnemonic(SoraAccount(wallet.address, "")))
            val material = launch.backupMaterial(wallet)
            assertEquals(TWELVE_WORDS.split(" "), material.phraseWords)
            assertEquals(wallet.seedHex, material.seedHex)
            // Nexus accounts are the ones the same words with spaces derive.
            val stored = launch.database.walletIdentityDao().getNetworkAccounts(wallet.address)
                .associate { it.networkId to it.address }
            NexusNetworks.admitted.forEach { network ->
                val expected = IrohaKeyDerivation.derive(TWELVE_WORDS, network)
                try {
                    assertEquals(expected.address, stored[network.id.wireId])
                } finally {
                    expected.destroy()
                }
            }
            // Importing the phrase, typed with spaces, says the wallet is already here.
            val again = runCatching { launch.importPhrase(TWELVE_WORDS, "Again") }.exceptionOrNull()
            assertEquals(
                ResponseCode.ACCOUNT_ALREADY_IMPORTED,
                (again as? SoraException)?.errorResponseCode,
            )
            launch.assertCiphertextsKept(before)
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
    }

    @Test
    fun keyBytesOfTheReleasedLibraryUpgradeUnchangedSignAndExport() = runBlocking {
        // Every checked-in vector of the released native library: phrases of 12, 15, 18, 21 and
        // 24 words (two of them asserted by fearless-utils' own tests) and a raw seed.
        val vectors = HistoricalSr25519Vectors.ALL
        val wallets = vectors.map { vector ->
            vector.phrase?.let(::phraseWallet) ?: rawSeedWallet(vector.seed)
        }
        val before = release3863 {
            wallets.forEach { wallet ->
                if (wallet.mnemonic != null) created(wallet) else importedSeed(wallet)
                finishedAccount(wallet)
            }
            select(wallets.first())
            registrationFinished()
        }
        room(73, *wallets.mapIndexed { index, wallet -> wallet to "Wallet $index" }.toTypedArray())

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            // The same addresses, and a fresh signature of every stored key verifies.
            launch.assertUsable(wallets, selected = wallets.first())
            assertEquals(wallets.size, launch.journal().verifiedAccountCount)
            launch.assertCiphertextsKept(before)
            vectors.zip(wallets).forEach { (vector, wallet) ->
                val account = SoraAccount(wallet.address, "Wallet")
                // The stored bytes are still exactly the ones the released library derived.
                val stored = launch.credentials.retrieveKeyPair(account)
                assertEquals(vector.id, vector.privateKey, stored.privateKey.toHexString())
                assertEquals(vector.id, vector.nonce, stored.nonce.toHexString())
                assertEquals(vector.id, vector.publicKey, stored.publicKey.toHexString())
                val report = launch.report(wallet)
                assertTrue(vector.id, report.canSign)
                assertTrue(vector.id, report.diagnostics.none { "DIFFER_FROM_DERIVATION" in it })
                // The export works and holds the same key pair.
                val json = launch.credentials.generateJson(listOf(account), JSON_PASSWORD)
                val draft = launch.credentials.draftsFromJson(json, JSON_PASSWORD, "Wallet").single()
                try {
                    assertEquals(vector.id, wallet.address, draft.address)
                    assertEquals(vector.id, vector.privateKey, draft.keypair.privateKey.toHexString())
                    assertEquals(vector.id, vector.nonce, draft.keypair.nonce.toHexString())
                } finally {
                    draft.destroy()
                }
            }
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun everyBackupFormatTheAppOffersRestoresTheSameAccount() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        release3863 {
            created(wallet)
            finishedAccount(wallet)
            select(wallet)
            registrationFinished()
            // The seed cache holds the uncropped 64 bytes (audit A1).
            assertEquals(128, showRawSeedOn3820(wallet).length)
        }
        room(73, wallet to "Main")
        val account = SoraAccount(wallet.address, "Main")

        // What the app shows, exports or backs up for this account.
        var phrase = ""
        var rawSeed = ""
        var json = ""
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            phrase = launch.backupMaterial(wallet).phraseWords.joinToString(" ")
            rawSeed = launch.credentials.retrieveSeed(account)
            json = launch.credentials.generateJson(listOf(account), JSON_PASSWORD)
        }
        assertEquals(TWELVE_WORDS, phrase)
        assertEquals(64, rawSeed.length)

        // Each of them, alone, on a phone that holds nothing.
        val restores = listOf<Pair<String, suspend (CandidateProcess) -> SoraAccount>>(
            "phrase" to { launch -> launch.importPhrase(phrase, "Restored") },
            "raw seed" to { launch -> launch.importSeed(rawSeed, "Restored") },
            "JSON" to { launch -> launch.importJson(json, JSON_PASSWORD, "Restored") },
        )
        restores.forEach { (format, restore) ->
            cleanInstall()
            candidate(newProcess = true) { launch ->
                assertTrue(format, launch.prepare.isSuccess)
                assertEquals(format, MigrationOutcome.Verified, launch.verify())
                assertEquals(format, wallet.address, restore(launch).substrateAddress)
            }
            candidate(newProcess = true) { launch ->
                assertTrue(format, launch.prepare.isSuccess)
                assertEquals(format, MigrationOutcome.Verified, launch.verify())
                launch.assertUsable(listOf(wallet), selected = wallet)
                if (format == "JSON") {
                    // A wallet that is only a key pair: no phrase or seed is offered, the
                    // account signs, and its own export restores it again.
                    assertEquals("LEGACY_SECRET", launch.identity(wallet).secretSource)
                    val material = launch.backupMaterial(wallet)
                    assertTrue(material.canSign && material.onlyJsonExport)
                    val again = launch.credentials.generateJson(listOf(account), JSON_PASSWORD)
                    val draft = launch.credentials.draftsFromJson(again, JSON_PASSWORD, "").single()
                    try {
                        assertEquals(wallet.address, draft.address)
                        assertArrayEquals(wallet.keypair.privateKey, draft.keypair.privateKey)
                    } finally {
                        draft.destroy()
                    }
                    // A wrong password or another file changes nothing and says why.
                    val wrong = runCatching {
                        launch.credentials.draftsFromJson(again, "another password", "")
                    }.exceptionOrNull()
                    assertEquals(
                        ResponseCode.JSON_BACKUP_WRONG_PASSWORD,
                        (wrong as? SoraException)?.errorResponseCode,
                    )
                    // The same file again: the wallet is already here.
                    val twice = runCatching {
                        launch.importJson(json, JSON_PASSWORD, "Again")
                    }.exceptionOrNull()
                    assertEquals(
                        ResponseCode.ACCOUNT_ALREADY_IMPORTED,
                        (twice as? SoraException)?.errorResponseCode,
                    )
                }
            }
        }
    }

    @Test
    fun storedKeyOfAnotherWalletIsRepairedWithItsEncryptedJsonExport() = runBlocking {
        val broken = phraseWallet(TWELVE_WORDS)
        val foreign = rawSeedWallet(RAW_SEED)
        // The export made while the account still worked.
        var json = ""
        release3863 {
            created(broken)
            finishedAccount(broken)
            select(broken)
            registrationFinished()
        }
        room(73, broken to "Main")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            json = launch.credentials.generateJson(listOf(SoraAccount(broken.address, "Main")), JSON_PASSWORD)
        }
        // Later the stored private key and nonce are another wallet's (they do not derive the
        // address), and no phrase is stored any more.
        cleanInstall()
        release3863 {
            credentials.saveKeys(
                Sr25519Keypair(foreign.keypair.privateKey, broken.keypair.publicKey, foreign.keypair.nonce),
                broken.address,
            )
            finishedAccount(broken)
            select(broken)
            registrationFinished()
        }
        room(73, broken to "Main")

        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(emptyList(), unavailable = listOf(broken.address), selected = broken)
            assertEquals("SIGNING_KEY_NOT_OF_ADDRESS", launch.report(broken).signingRefusal)
            assertEquals(0, launch.journal().verifiedAccountCount)
            // Keys that do not belong to the account are never exported as its backup.
            val refused = runCatching {
                launch.credentials.generateJson(listOf(SoraAccount(broken.address, "Main")), JSON_PASSWORD)
            }.exceptionOrNull()
            assertEquals(
                ResponseCode.ACCOUNT_KEYS_NOT_EXPORTABLE,
                (refused as? SoraException)?.errorResponseCode,
            )

            // Importing the backup file offers the repair and writes nothing.
            val offer = runCatching { launch.importJson(json, JSON_PASSWORD, "Main") }.exceptionOrNull()
            assertTrue(offer.toString(), offer is WalletRepairAvailableException)
            assertTrue(repairCopies().isEmpty())
            // Confirmed with the PIN: the old values are copied first, then the key pair of the
            // file is written, and the account signs again under the same address.
            val repaired = launch.repairWithJson(json, JSON_PASSWORD, "Main", pin = "135790")
            assertEquals(broken.address, repaired.substrateAddress)
            assertEquals(1, repairCopies().size)
            launch.assertUsable(listOf(broken), selected = broken)
        }
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(broken), selected = broken)
            assertEquals(1, launch.journal().verifiedAccountCount)
        }
    }

    @Test
    fun rawSeedPastedWithALineBreakOn3863KeepsSigningAndIsStillItsSeed() = runBlocking {
        val wallet = rawSeedWallet(RAW_SEED)
        val control = phraseWallet(TWELVE_WORDS)
        // The 64 characters as a note app wrapped them onto two lines.
        val typed = RAW_SEED.take(32) + "\n" + RAW_SEED.drop(32)
        val before = release3863 {
            importedSeedAsTyped(wallet, typed)
            created(control)
            finishedAccount(wallet)
            finishedAccount(control)
            select(wallet)
            registrationFinished()
        }
        room(73, wallet to "Seed wallet", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet, control), selected = wallet)
            launch.assertCiphertextsKept(before)
            // The stored text, line break included, is this account's seed.
            assertEquals(typed, launch.credentialsDatasource.retrieveSeed(wallet.address))
            assertEquals("RAW_SEED", launch.identity(wallet).secretSource)
            assertEquals("MATCHES", launch.report(wallet).seed)
            // What is shown, exported and backed up is the 64 characters the import accepts.
            val material = launch.backupMaterial(wallet)
            assertTrue(material.canSign && material.hasSeed && !material.storedSeedDoesNotMatch)
            assertEquals(RAW_SEED, material.seedHex)
            assertEquals(RAW_SEED, launch.credentials.retrieveSeed(SoraAccount(wallet.address, "")))
            // Typing the same seed again, with or without the line break: already here.
            listOf(typed, RAW_SEED).forEach { again ->
                val error = runCatching { launch.importSeed(again, "Again") }.exceptionOrNull()
                assertEquals(
                    ResponseCode.ACCOUNT_ALREADY_IMPORTED,
                    (error as? SoraException)?.errorResponseCode,
                )
            }
            launch.assertCiphertextsKept(before)
        }

        // The seed as it is shown restores the same account on an empty phone, and a seed
        // imported by this version is stored as its 64 characters.
        cleanInstall()
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(wallet.address, launch.importSeed(typed, "Restored").substrateAddress)
            assertEquals(RAW_SEED, launch.credentialsDatasource.retrieveSeed(wallet.address))
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
    }

    @Test
    fun brokenKeyNextToASeedStoredWithALineBreakIsRepairedWithThatSeed() = runBlocking {
        val broken = rawSeedWallet(RAW_SEED)
        val control = phraseWallet(TWELVE_WORDS)
        val foreign = phraseWallet(TWENTY_FOUR_WORDS)
        val typed = RAW_SEED.take(32) + "\n" + RAW_SEED.drop(32)
        val before = release3863 {
            // The key stored under this account's address does not derive that address.
            credentials.saveKeys(foreign.keypair, broken.address)
            credentials.saveSeed(typed, broken.address)
            created(control)
            finishedAccount(broken)
            finishedAccount(control)
            select(control)
            registrationFinished()
        }
        room(73, broken to "Broken", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(control), unavailable = listOf(broken.address), selected = control)
            // Its own seed is still recognised and shown as its backup.
            assertEquals(RAW_SEED, launch.backupMaterial(broken).seedHex)

            // Typing the seed exactly as it was first pasted offers the repair ...
            val offer = runCatching { launch.importSeed(typed, "Broken") }.exceptionOrNull()
            assertTrue(offer.toString(), offer is WalletRepairAvailableException)
            launch.assertCiphertextsKept(before)
            // ... and the confirmed repair accepts it as proven and writes the proven keys.
            val repaired = launch.interactor.repairFromRawSeed(typed, "Broken", "135790")
            assertEquals(broken.address, repaired.substrateAddress)
            assertEquals(1, repairCopies().size)
            launch.assertUsable(listOf(control, broken), selected = broken)
            assertEquals(RAW_SEED, launch.credentialsDatasource.retrieveSeed(broken.address))
            launch.assertCiphertextsKept(before.filterKeys { !it.endsWith(broken.address) })
        }
    }

    @Test
    fun phraseEnteredForAWalletRestoredFromItsBackupFileIsKeptAsItsBackup() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        release3863 {
            created(wallet)
            finishedAccount(wallet)
            select(wallet)
            registrationFinished()
        }
        room(73, wallet to "Main")
        var json = ""
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            json = launch.credentials.generateJson(listOf(SoraAccount(wallet.address, "Main")), JSON_PASSWORD)
        }

        // A new phone: the wallet is restored from its backup file and is only a key pair.
        cleanInstall()
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.importJson(json, JSON_PASSWORD, "Restored")
            launch.assertUsable(listOf(wallet), selected = wallet)
            assertEquals("LEGACY_SECRET", launch.identity(wallet).secretSource)
            assertTrue(launch.backupMaterial(wallet).onlyJsonExport)
            assertEquals(setOf("sora2"), launch.networkIds(wallet))
            val keysBefore = launch.dataStore.data.first().asMap().mapKeys { it.key.name }
                .filterKeys { it.endsWith(wallet.address) }
            val key = launch.credentials.retrieveKeyPair(SoraAccount(wallet.address, ""))

            // The person enters the wallet's phrase in Import account, typed with commas.
            val answer = runCatching {
                launch.importPhrase(TWELVE_WORDS.replace(" ", ", "), "Typed name")
            }.exceptionOrNull()
            // "Already here", and the phrase was kept as its backup.
            assertEquals(
                ResponseCode.ACCOUNT_ALREADY_IMPORTED_BACKUP_SAVED,
                (answer as? SoraException)?.errorResponseCode,
            )
            // Add-only: every value stored before is unchanged, the key included.
            launch.assertCiphertextsKept(keysBefore)
            val after = launch.credentials.retrieveKeyPair(SoraAccount(wallet.address, ""))
            assertArrayEquals(key.privateKey, after.privateKey)
            assertArrayEquals(key.nonce, after.nonce)
            assertEquals(TWELVE_WORDS, launch.credentialsDatasource.retrieveMnemonic(wallet.address))
            // The account keeps its row and name, shows its phrase and seed, and has its
            // SORA 3 accounts at once (they need a recorded 12- or 24-word phrase).
            launch.assertUsable(listOf(wallet), selected = wallet)
            assertEquals("Restored", launch.userRepository.getCurSoraAccount().accountName)
            assertEquals("MNEMONIC", launch.identity(wallet).secretSource)
            val material = launch.backupMaterial(wallet)
            assertEquals(TWELVE_WORDS.split(" "), material.phraseWords)
            assertEquals(wallet.seedHex, material.seedHex)
            NexusNetworks.admitted.forEach { network ->
                assertTrue(network.id.wireId, network.id.wireId in launch.networkIds(wallet))
            }
            assertTrue(repairCopies().isEmpty())

            // Entered once more: plainly already here, nothing more is written.
            val stored = launch.dataStore.data.first().asMap().mapKeys { it.key.name }
            val again = runCatching { launch.importPhrase(TWELVE_WORDS, "Again") }.exceptionOrNull()
            assertEquals(
                ResponseCode.ACCOUNT_ALREADY_IMPORTED,
                (again as? SoraException)?.errorResponseCode,
            )
            assertEquals(stored, launch.dataStore.data.first().asMap().mapKeys { it.key.name })
        }
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
            assertEquals("MNEMONIC", launch.identity(wallet).secretSource)
            assertEquals(1, launch.journal().verifiedAccountCount)
        }
    }

    @Test
    fun wrongRepairPinsCountThroughThePinScreensCounterAndLockIt() = runBlocking {
        val broken = phraseWallet(TWELVE_WORDS)
        val control = phraseWallet(TWENTY_FOUR_WORDS)
        val foreign = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            credentials.saveKeys(foreign.keypair, broken.address)
            credentials.saveMnemonic(requireNotNull(broken.mnemonic), broken.address)
            created(control)
            finishedAccount(broken)
            finishedAccount(control)
            select(control)
            registrationFinished()
        }
        room(73, broken to "Broken", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            val offer = runCatching { launch.importPhrase(TWELVE_WORDS, "Broken") }.exceptionOrNull()
            assertTrue(offer.toString(), (offer as? WalletRepairAvailableException)?.pinRequired == true)

            // Two wrong PINs, then a third: PIN entry is locked, as on the PIN screen, and the
            // lock is what the PIN screen reads at its next start.
            repeat(2) {
                val wrong = runCatching { launch.repairWithPhrase(TWELVE_WORDS, "Broken", "00000$it") }
                    .exceptionOrNull()
                assertEquals(ResponseCode.WRONG_PIN_CODE, (wrong as? SoraException)?.errorResponseCode)
            }
            assertEquals(2, launch.userRepository.retrievePinTriesUsed())
            val third = runCatching { launch.repairWithPhrase(TWELVE_WORDS, "Broken", "999999") }
                .exceptionOrNull()
            assertTrue(third.toString(), third is RepairPinLockedException)
            assertEquals(3, launch.userRepository.retrievePinTriesUsed())
            assertTrue(launch.userRepository.retrieveTimerStartedTimestamp() > 0)
            // Starting the import again does not give new tries: not even the right PIN is
            // accepted while it is locked. Nothing was changed by any of this.
            val locked = runCatching { launch.repairWithPhrase(TWELVE_WORDS, "Broken", "135790") }
                .exceptionOrNull()
            assertTrue(locked.toString(), locked is RepairPinLockedException)
            launch.assertCiphertextsKept(before)
            assertTrue(repairCopies().isEmpty())
            launch.assertUsable(listOf(control), unavailable = listOf(broken.address), selected = control)

            // After the lock (the timer is moved back here instead of waiting a minute) the
            // right PIN repairs the account and clears the counter and the timer.
            launch.userRepository.saveTimerStartedTimestamp(System.currentTimeMillis() - 61_000)
            launch.repairWithPhrase(TWELVE_WORDS, "Broken", "135790")
            assertEquals(0, launch.userRepository.retrievePinTriesUsed())
            assertEquals(0L, launch.userRepository.retrieveTimerStartedTimestamp())
            launch.assertUsable(listOf(control, broken), selected = broken)
        }
    }

    @Test
    fun accountThatSignsWithoutAProvenBackupKeepsACopyWhenItIsRemoved() = runBlocking {
        val working = phraseWallet(TWELVE_WORDS)
        val backedUp = phraseWallet(EIGHTEEN_WORDS)
        val control = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            credentials.saveKeys(working.keypair, working.address)
            // The phrase stored next to this key belongs to a different wallet: nothing on the
            // phone is a backup of this account, and there is no phrase to show before removal.
            credentials.saveMnemonic(TWENTY_FOUR_WORDS, working.address)
            created(backedUp)
            importedSeed(control)
            listOf(working, backedUp, control).forEach { finishedAccount(it) }
            select(control)
            registrationFinished()
        }
        room(73, working to "Working", backedUp to "Backed up", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(working, backedUp, control), selected = control)
            val copies = {
                context.noBackupFilesDir.listFiles().orEmpty()
                    .filter { it.name.startsWith("wallet-quarantine-") && it.name.contains("-removed-") }
            }

            // An account whose own phrase is stored: removing it needs no copy.
            launch.remove(backedUp.address)
            assertTrue(copies().isEmpty())

            // The account that signs but has no proven backup on the phone: the confirmed
            // removal first keeps a copy of the encrypted preference files, which still hold
            // its key and the other wallet's phrase.
            val stored = launch.dataStore.data.first().asMap().mapKeys { it.key.name }
            launch.remove(working.address)
            val copy = copies().single()
            assertTrue(copy.name, copy.name.endsWith("-removed-account-without-backup"))
            val kept = File(copy, "sora_prefs_datastore.preferences_pb").readBytes()
            listOf("prefs_priv_key", "prefs_key_nonce", "prefs_mnemonic").forEach { field ->
                val ciphertext = stored.getValue(field + working.address) as String
                assertTrue(field, String(kept, Charsets.ISO_8859_1).contains(ciphertext))
                assertEquals(before[field + working.address], ciphertext)
            }
            launch.assertUsable(listOf(control), selected = control)
        }
    }

    @Test
    fun batchBackupFileRepairsTheAccountItNamesAfterOneConfirmation() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        val broken = phraseWallet(TWENTY_FOUR_WORDS)
        val foreign = rawSeedWallet(RAW_SEED)
        release3863 {
            created(first)
            created(broken)
            finishedAccount(first)
            finishedAccount(broken)
            select(first)
            registrationFinished()
        }
        room(73, first to "First", broken to "Broken")
        // The batch backup of both accounts, made while both worked. It is the person's only
        // backup of the second one.
        var batch = ""
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            batch = batchBackupFile(
                listOf(first to "First", broken to "Broken").map { (wallet, name) ->
                    launch.credentials.generateJson(listOf(SoraAccount(wallet.address, name)), JSON_PASSWORD)
                },
            )
        }
        // Later the keys stored for the second account are another wallet's, and no phrase is
        // stored for it any more.
        cleanInstall()
        val before = release3863 {
            created(first)
            credentials.saveKeys(
                Sr25519Keypair(foreign.keypair.privateKey, broken.keypair.publicKey, foreign.keypair.nonce),
                broken.address,
            )
            finishedAccount(first)
            finishedAccount(broken)
            select(first)
            registrationFinished()
        }
        room(73, first to "First", broken to "Broken")

        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first), unavailable = listOf(broken.address), selected = first)

            // Import account, Backup file (JSON): the first account is already here; the second
            // is named for the repair. Nothing was written and nothing was copied.
            val result = launch.interactor.importFromJson(batch, JSON_PASSWORD, "")
            assertTrue(result.imported.isEmpty())
            assertEquals(1, result.alreadyHere)
            assertEquals(listOf(broken.address), result.needRepair.map { it.address })
            assertTrue(result.needRepair.single().accountExists)
            assertTrue(result.repairPinRequired && !result.repairScreenLockRequired)
            launch.assertCiphertextsKept(before)
            assertTrue(repairCopies().isEmpty())

            // A wrong PIN changes nothing.
            val wrongPin = runCatching {
                launch.interactor.repairFromJsonBatch(
                    json = batch,
                    password = JSON_PASSWORD,
                    accountName = "",
                    pin = "000000",
                    addresses = listOf(broken.address),
                )
            }.exceptionOrNull()
            assertEquals(ResponseCode.WRONG_PIN_CODE, (wrongPin as? SoraException)?.errorResponseCode)
            launch.assertCiphertextsKept(before)

            // Confirmed once with the PIN: a copy of the old values is kept, the key pair of
            // the file is written for that account only, and it signs under the same address.
            val repaired = launch.interactor.repairFromJsonBatch(
                json = batch,
                password = JSON_PASSWORD,
                accountName = "",
                pin = "135790",
                addresses = result.needRepair.map { it.address },
            )
            assertEquals(listOf(broken.address), repaired.map { it.substrateAddress })
            assertEquals(1, repairCopies().size)
            launch.assertUsable(listOf(first, broken), selected = broken)
            assertEquals("Broken", launch.userRepository.getCurSoraAccount().accountName)
            launch.assertCiphertextsKept(before.filterKeys { !it.endsWith(broken.address) })

            // The same file again: every account in it is already here.
            val again = runCatching { launch.interactor.importFromJson(batch, JSON_PASSWORD, "") }
                .exceptionOrNull()
            assertEquals(
                ResponseCode.ACCOUNT_ALREADY_IMPORTED,
                (again as? SoraException)?.errorResponseCode,
            )
        }
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, broken), selected = broken)
            assertEquals(2, launch.journal().verifiedAccountCount)
        }
    }

    /**
     * HARNESS: a batch backup file in the format `JsonAccountsEncoder.generate(List)` writes:
     * the one-account exports as a JSON array, encrypted as a whole with scrypt and
     * xsalsa20-poly1305 under the same password. The production batch writer needs the app's
     * CryptoAssistant, which this harness does not build, so the outer layer is made with the
     * library's own encoder for that format (the one the one-account export uses). The
     * production reader opens it; `JsonAccountsExportRoundTripTest` proves the production writer
     * and reader agree.
     */
    private fun batchBackupFile(accountExports: List<String>): String {
        val gson = Gson()
        val accounts = JsonArray()
        accountExports.forEach { accounts.add(gson.fromJson(it, JsonElement::class.java)) }
        val encryption = listOf("scrypt", "xsalsa20-poly1305")
        val outer = requireNotNull(TypeCoderFactory.getEncoder(encryption)).encode(
            gson.toJson(accounts).encodeToByteArray(),
            JSON_PASSWORD.encodeToByteArray(),
        )
        return JSONObject()
            .put("encoded", android.util.Base64.encodeToString(outer, android.util.Base64.NO_WRAP))
            .put(
                "encoding",
                JSONObject()
                    .put("content", org.json.JSONArray(listOf("batch-pkcs8")))
                    .put("type", org.json.JSONArray(encryption))
                    .put("version", "3"),
            )
            .put("accounts", org.json.JSONArray())
            .toString()
    }

    @Test
    fun backupMaterialThatCannotBeReadNeverBlocksSigning() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        val control = phraseWallet(TWENTY_FOUR_WORDS)
        val before = release3863 {
            created(wallet)
            created(control)
            finishedAccount(wallet)
            finishedAccount(control)
            select(wallet)
            registrationFinished()
            assertEquals(64, showRawSeedOn3863(wallet).length)
            // The phrase and the cached seed of one account no longer decrypt; its key does.
            prefs.putString("prefs_mnemonic${wallet.address}", UNDECRYPTABLE)
            prefs.putString("prefs_seed${wallet.address}", UNDECRYPTABLE)
        }
        room(73, wallet to "Main", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            // The stored key signs, so the account signs.
            launch.assertUsable(listOf(wallet, control), selected = wallet)
            launch.assertCiphertextsKept(before)
            assertEquals(2, launch.journal().verifiedAccountCount)
            assertEquals("LEGACY_SECRET", launch.identity(wallet).secretSource)
            assertEquals("UNREADABLE", launch.report(wallet).phrase)
            assertEquals("UNREADABLE", launch.report(wallet).seed)
            // Nothing unreadable is offered as a backup; the encrypted JSON export is.
            val material = launch.backupMaterial(wallet)
            assertTrue(material.canSign && material.onlyJsonExport)
            assertTrue(material.storedPhraseUnreadable && material.storedSeedUnreadable)
            assertFalse(material.storedPhraseDoesNotMatch || material.storedSeedDoesNotMatch)
            val json = launch.credentials.generateJson(listOf(SoraAccount(wallet.address, "Main")), JSON_PASSWORD)
            val draft = launch.credentials.draftsFromJson(json, JSON_PASSWORD, "Main").single()
            try {
                assertEquals(wallet.address, draft.address)
            } finally {
                draft.destroy()
            }
            // Importing its phrase says the wallet is already here; nothing is rewritten.
            val again = runCatching { launch.importPhrase(TWELVE_WORDS, "Again") }.exceptionOrNull()
            assertEquals(
                ResponseCode.ACCOUNT_ALREADY_IMPORTED,
                (again as? SoraException)?.errorResponseCode,
            )
            launch.assertCiphertextsKept(before)
        }
    }

    @Test
    fun nexusAccountFirstDerivedFromACommaTextKeepsItsIdentity() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        val typed = TWELVE_WORDS.replace(" ", ", ")
        val before = release233 { restoredOn232(wallet, typed = typed, name = "Main wallet") }
        room(50)

        // A new derivation uses the words: the identities of the phrase written with spaces.
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
        }
        // An earlier build of this version derived them from the stored text, commas included.
        val earlier = NexusNetworks.admitted.associate { network ->
            val derived = IrohaKeyDerivation.derive(
                mnemonic = typed,
                profile = network.derivationProfile,
                phraseText = IrohaKeyDerivation.PhraseText.AS_FIRST_DERIVED,
            )
            try {
                network.id.wireId to (derived.address to derived.publicKey.toHexString())
            } finally {
                derived.destroy()
            }
        }
        earlier.forEach { (networkId, identity) ->
            updateDatabase(
                "UPDATE networkAccounts SET address = '${identity.first}', " +
                    "publicKey = '${identity.second}' " +
                    "WHERE walletId = '${wallet.address}' AND networkId = '$networkId'"
            )
        }

        repeat(2) {
            candidate(newProcess = true) { launch ->
                assertTrue(launch.prepare.isSuccess)
                assertEquals(MigrationOutcome.Verified, launch.verify())
                launch.assertUsable(listOf(wallet), selected = wallet)
                launch.assertCiphertextsKept(before)
                // Address and public key are still the ones first derived, on every start.
                val stored = launch.database.walletIdentityDao().getNetworkAccounts(wallet.address)
                    .filter { it.networkId != "sora2" }
                    .associate { it.networkId to (it.address to it.publicKey) }
                assertEquals(earlier, stored)
                // And signing re-derives exactly that account from the stored text.
                NexusNetworks.admitted.forEach { network ->
                    val (address, publicKey) = earlier.getValue(network.id.wireId)
                    val derived = requireNotNull(
                        IrohaKeyDerivation.deriveExisting(
                            mnemonic = launch.credentials.retrieveMnemonic(SoraAccount(wallet.address, "")),
                            profile = network.derivationProfile,
                            existingAddress = address,
                            existingPublicKey = HistoricalSr25519Vectors.hex(publicKey),
                        )
                    )
                    derived.destroy()
                }
            }
        }
    }

    @Test
    fun upperCaseHexAndPrefixedValuesUpgradeAndSign() = runBlocking {
        val phrase = phraseWallet(TWELVE_WORDS)
        val seed = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            listOf(phrase, seed).forEach { wallet ->
                // The same bytes written as upper-case hex with a 0X prefix.
                encrypted.putEncryptedString(
                    "prefs_priv_key${wallet.address}",
                    "0X" + wallet.keypair.privateKey.toHexString().uppercase(),
                )
                encrypted.putEncryptedString(
                    "prefs_pub_key${wallet.address}",
                    "0X" + wallet.keypair.publicKey.toHexString().uppercase(),
                )
                encrypted.putEncryptedString(
                    "prefs_key_nonce${wallet.address}",
                    wallet.keypair.nonce.toHexString().uppercase(),
                )
                finishedAccount(wallet)
            }
            credentials.saveMnemonic(requireNotNull(phrase.mnemonic), phrase.address)
            credentials.saveSeed("0X" + phrase.seedHex.uppercase(), phrase.address)
            credentials.saveSeed("0X" + seed.seedHex.uppercase(), seed.address)
            select(phrase)
            registrationFinished()
        }
        room(73, phrase to "Phrase", seed to "Seed")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(phrase, seed), selected = phrase)
            launch.assertCiphertextsKept(before)
            assertEquals("MNEMONIC", launch.identity(phrase).secretSource)
            assertEquals("RAW_SEED", launch.identity(seed).secretSource)
            assertEquals("MATCHES", launch.report(phrase).seed)
            assertEquals(seed.seedHex, launch.backupMaterial(seed).seedHex)
            assertEquals(phrase.seedHex, launch.credentials.retrieveSeed(SoraAccount(phrase.address, "")))
        }
    }

    @Test
    fun storedNonceThatDiffersFromAFreshDerivationStillSigns() = runBlocking {
        val wallet = phraseWallet(TWELVE_WORDS)
        val orphan = phraseWallet(TWENTY_FOUR_WORDS)
        // The same private scalar and public key with a nonce that is not the derived one. The
        // nonce only seeds the signature's randomness, so such a key pair signed in 3.8.6.3.
        fun withOtherNonce(fixture: Fixture) = Sr25519Keypair(
            fixture.keypair.privateKey.copyOf(),
            fixture.keypair.publicKey.copyOf(),
            ByteArray(32) { (it * 5 + 9).toByte() },
        )
        val before = release3863 {
            credentials.saveKeys(withOtherNonce(wallet), wallet.address)
            credentials.saveMnemonic(requireNotNull(wallet.mnemonic), wallet.address)
            finishedAccount(wallet)
            select(wallet)
            registrationFinished()
            // The same kind of key set left without an account row.
            credentials.saveKeys(withOtherNonce(orphan), orphan.address)
            credentials.saveMnemonic(requireNotNull(orphan.mnemonic), orphan.address)
        }
        room(73, wallet to "Wallet")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(wallet), selected = wallet)
            launch.assertCiphertextsKept(before)
            assertEquals("MNEMONIC", launch.identity(wallet).secretSource)
            assertEquals(
                setOf("NONCE_BYTES_DIFFER_FROM_DERIVATION"),
                launch.report(wallet).diagnostics,
            )
            // Importing the phrase of the key set without an account adopts it unchanged.
            launch.importPhrase(TWENTY_FOUR_WORDS, "Adopted")
            launch.assertUsable(listOf(wallet, orphan), selected = orphan)
            launch.assertCiphertextsKept(before)
            assertTrue(repairCopies().isEmpty())
        }
    }

    @Test
    fun storedPhraseOfAnotherWalletNeverBlocksAWorkingKeyAndIsNotShownAsItsBackup() = runBlocking {
        val working = phraseWallet(TWELVE_WORDS)
        val control = phraseWallet(EIGHTEEN_WORDS)
        val before = release3863 {
            credentials.saveKeys(working.keypair, working.address)
            // The phrase stored next to this key belongs to a different wallet.
            credentials.saveMnemonic(TWENTY_FOUR_WORDS, working.address)
            created(control)
            finishedAccount(working)
            finishedAccount(control)
            select(working)
            registrationFinished()
        }
        room(73, working to "Working", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            // The stored key signs for the address, so the account signs.
            launch.assertUsable(listOf(working, control), selected = working)
            launch.assertCiphertextsKept(before)
            assertEquals("LEGACY_SECRET", launch.identity(working).secretSource)
            assertEquals("DOES_NOT_MATCH", launch.report(working).phrase)
            assertEquals(2, launch.journal().verifiedAccountCount)
            // No Nexus identity is derived from a phrase that is not this wallet's.
            assertEquals(setOf("sora2"), launch.networkIds(working))

            // The other wallet's phrase is never offered as this account's backup; the encrypted
            // JSON export (made from the stored key) is.
            val material = launch.backupMaterial(working)
            assertTrue(material.canSign && material.onlyJsonExport && material.storedPhraseDoesNotMatch)
            assertTrue(material.phraseWords.isEmpty() && material.seedHex.isEmpty())
            assertTrue(
                runCatching { launch.credentials.retrieveSeed(SoraAccount(working.address, "")) }.isFailure
            )
            launch.credentials.generateJson(listOf(SoraAccount(working.address, "Working")), "password")
            // Nothing was cached or changed by looking.
            launch.assertCiphertextsKept(before)
            assertEquals("", launch.credentialsDatasource.retrieveSeed(working.address))
        }
    }

    @Test
    fun storedKeyOfAnotherWalletIsReadOnlyAndRepairedWithItsPhrase() = runBlocking {
        val broken = phraseWallet(TWELVE_WORDS)
        val control = phraseWallet(TWENTY_FOUR_WORDS)
        val foreign = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            // The key stored under this account's address does not derive that address.
            credentials.saveKeys(foreign.keypair, broken.address)
            credentials.saveMnemonic(requireNotNull(broken.mnemonic), broken.address)
            created(control)
            finishedAccount(broken)
            finishedAccount(control)
            select(control)
            registrationFinished()
        }
        room(73, broken to "Broken", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(control), unavailable = listOf(broken.address), selected = control)
            launch.assertCiphertextsKept(before)
            assertEquals("SORA_ADDRESS_MISMATCH", launch.report(broken).signingRefusal)
            // Truthful journal: one of two accounts can sign.
            assertEquals(2, launch.journal().legacyAccountCount)
            assertEquals(1, launch.journal().verifiedAccountCount)
            // Its own phrase is still stored and shown as its backup.
            assertEquals(TWELVE_WORDS.split(" "), launch.backupMaterial(broken).phraseWords)
            assertFalse(launch.backupMaterial(broken).canSign)

            // Importing its phrase: stored key values differ and the stored key does not prove
            // the address, so nothing is written and the confirmed repair is offered.
            val offer = runCatching { launch.importPhrase(TWELVE_WORDS, "Broken") }
                .exceptionOrNull() as WalletRepairAvailableException
            assertTrue(offer.accountExists && offer.pinRequired)
            launch.assertCiphertextsKept(before)

            launch.repairWithPhrase(TWELVE_WORDS, "Broken", "135790")
            assertEquals(1, repairCopies().size)
            launch.assertUsable(listOf(broken, control), selected = broken)
            // Only this account's key values changed.
            launch.assertCiphertextsKept(before.filterKeys { !it.endsWith(broken.address) })
        }
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(broken, control), selected = broken)
            assertEquals(2, launch.journal().verifiedAccountCount)
            assertEquals("MNEMONIC", launch.identity(broken).secretSource)
        }
    }

    @Test
    fun conflictingKeySetWithoutAccountRowIsRepairedByImportingItsPhrase() = runBlocking {
        val main = phraseWallet(TWELVE_WORDS)
        val orphan = phraseWallet(TWENTY_FOUR_WORDS)
        val foreign = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            created(main)
            finishedAccount(main)
            select(main)
            registrationFinished()
            // Keys without an account row that do not derive the address they are stored under.
            credentials.saveKeys(foreign.keypair, orphan.address)
        }
        room(73, main to "Main")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(main), selected = main)
            // "Restore this wallet" cannot use these keys...
            assertTrue(
                runCatching { launch.userRepository.adoptRetainedWallet(orphan.address, "Orphan") }.isFailure
            )
            launch.assertCiphertextsKept(before)
            // ...and importing the phrase offers the confirmed repair instead of a dead end.
            val offer = runCatching { launch.importPhrase(TWENTY_FOUR_WORDS, "Orphan") }
                .exceptionOrNull() as WalletRepairAvailableException
            assertFalse(offer.accountExists)
            launch.assertCiphertextsKept(before)

            launch.repairWithPhrase(TWENTY_FOUR_WORDS, "Orphan", "135790")
            assertEquals(1, repairCopies().size)
            launch.assertUsable(listOf(main, orphan), selected = orphan)
            launch.assertCiphertextsKept(before.filterKeys { !it.endsWith(orphan.address) })
        }
    }

    // ---- ak2 (audit A2): a read-only account is labelled, explained and repairable -----------

    @Test
    fun readOnlyAccountShowsItsLabelItsRepairAndSendExplainsItself() = runBlocking {
        val broken = phraseWallet(TWELVE_WORDS)
        val control = phraseWallet(TWENTY_FOUR_WORDS)
        val foreign = rawSeedWallet(RAW_SEED)
        release3863 {
            // The key stored under this account's address does not derive that address.
            credentials.saveKeys(foreign.keypair, broken.address)
            credentials.saveMnemonic(requireNotNull(broken.mnemonic), broken.address)
            created(control)
            finishedAccount(broken)
            finishedAccount(control)
            // The person had this account selected.
            select(broken)
            registrationFinished()
        }
        room(73, broken to "Broken", control to "Control")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            val users = launch.userRepository
            assertEquals(broken.address, users.getCurSoraAccount().substrateAddress)

            // The label of the main screen and of every screen where the person signs: the
            // selected account is reported as read-only, from the database.
            assertEquals(
                SelectedAccountSigning(broken.address, readOnly = true),
                withTimeout(SCREEN_TIMEOUT_MS) {
                    users.flowSelectedAccountSigning().first { it.address == broken.address }
                },
            )
            assertEquals(setOf(broken.address), users.flowUnavailableWalletIds().first())

            // Why, and what can be done (UserRepository.unavailableWalletOptions).
            val options = requireNotNull(users.unavailableWalletOptions(broken.address))
            assertEquals(UnavailableWalletReason.KEY_NOT_USABLE, options.reason)
            assertTrue(options.canRepairWithPhrase)
            assertTrue(options.canShowPhraseAfterPin)
            assertNull(users.unavailableWalletOptions(control.address))

            // Send explains itself: the signer refuses this account with the code the screens
            // turn into "This account can't send from this phone ... nothing was signed or
            // sent", also when a screen wrapped it as an unexpected error (as Send does).
            val refusal =
                requireNotNull(WalletSigningGate.unavailableReason(launch.database, broken.address))
            assertEquals(FailureNotice.SIGNING_WALLET_UNAVAILABLE, refusal)
            assertEquals(
                FailureNotice.ACCOUNT_READ_ONLY,
                FailureNotice.of(SoraException.unexpectedError(IllegalStateException(refusal))),
            )
            assertNull(WalletSigningGate.unavailableReason(launch.database, control.address))

            // The account list labels exactly this account.
            val list = onMainThread {
                AccountListViewModel(
                    launch.interactor,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                )
            }
            awaitOnScreen("two rows") { list.accountListScreenState.value?.accountList?.size == 2 }
            val rows = requireNotNull(list.accountListScreenState.value).accountList
            assertTrue(rows.single { it.account.substrateAddress == broken.address }.readOnly)
            assertFalse(rows.single { it.account.substrateAddress == control.address }.readOnly)

            // Account options: the reason, and the repair action first.
            val details = onMainThread {
                AccountDetailsViewModel(
                    launch.interactor,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    CloudBackupProvider(false) { error("Google backup is not used here") },
                    CoroutineManager(),
                    broken.address,
                )
            }
            awaitOnScreen("read-only panel") { details.accountDetailsScreenState.value?.readOnly != null }
            assertEquals(
                ReadOnlyAccountUi(
                    reason = UnavailableWalletReason.KEY_NOT_USABLE,
                    canRestoreWithPhrase = true,
                    canRestoreFromGoogle = false,
                    canShowBackup = true,
                    canRemove = true,
                ),
                details.accountDetailsScreenState.value?.readOnly,
            )
            onMainThread { details.onRestoreKeysWithPhrase() }
            assertEquals(
                AccountDetailsViewModel.RestoreKeys.WITH_PHRASE,
                details.restoreKeysEvent.value,
            )
            // Its own phrase is what the removal screen would show first (invariant 4).
            assertEquals(TWELVE_WORDS.split(" "), launch.backupMaterial(broken).phraseWords)

            // "Restore keys with recovery phrase": Import account, the phrase, the PIN.
            launch.repairWithPhrase(TWELVE_WORDS, "Broken", "135790")

            // The labels go at once, without a restart, and the account signs.
            awaitOnScreen("panel gone") { details.accountDetailsScreenState.value?.readOnly == null }
            awaitOnScreen("label gone") {
                list.accountListScreenState.value?.accountList?.none { it.readOnly } == true
            }
            assertEquals(
                SelectedAccountSigning(broken.address, readOnly = false),
                withTimeout(SCREEN_TIMEOUT_MS) {
                    users.flowSelectedAccountSigning().first { !it.readOnly }
                },
            )
            assertNull(WalletSigningGate.unavailableReason(launch.database, broken.address))
            launch.assertUsable(listOf(broken, control), selected = broken)
        }
    }

    @Test
    fun removingTheOnlyAccountEndsCleanlyWithTheAccountListStillOpen() = runBlocking {
        // The candidate crashed here (IllegalArgumentException "Required value was null" from
        // getCurSoraAccount): the account list, still on the back stack, asked for the selected
        // account as soon as the list became empty.
        val only = phraseWallet(TWELVE_WORDS)
        release3863Wallet(only)
        room(73, only to "Only")

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            val list = onMainThread {
                AccountListViewModel(
                    launch.interactor,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                )
            }
            awaitOnScreen("one row") { list.accountListScreenState.value?.accountList?.size == 1 }

            launch.remove(only.address)

            awaitOnScreen("empty list") { list.accountListScreenState.value?.accountList?.isEmpty() == true }
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            assertEquals(
                SelectedAccountSigning.NONE,
                withTimeout(SCREEN_TIMEOUT_MS) {
                    launch.userRepository.flowSelectedAccountSigning().first { it.address.isEmpty() }
                },
            )
            // Nothing was reported as a failure either.
            assertNull(list.errorFromResourceLiveData.value)
        }
        // The app restarts: onboarding, with nothing left to restore without a phrase.
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            assertEquals(emptyList<RetainedWallet>(), launch.userRepository.retainedWallets())
            // The same phrase brings the account back at the same address.
            launch.importPhrase(TWELVE_WORDS, "Only")
            launch.assertUsable(listOf(only), selected = only)
        }
    }

    @Test
    fun removeAllAccountsLeavesKeySetsThatOnlyTheirOwnPhraseBringsBack() = runBlocking {
        val main = phraseWallet(TWELVE_WORDS)
        val leftover = phraseWallet(TWENTY_FOUR_WORDS)
        val before = release3863 {
            created(main)
            finishedAccount(main)
            select(main)
            registrationFinished()
            // A "Create account" abandoned in 3.8.6.3: keys and phrase without an account.
            created(leftover)
        }
        room(73, main to "Main")
        val leftoverBefore = before.filterKeys { it.endsWith(leftover.address) }
        assertTrue(leftoverBefore.isNotEmpty())

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            // Behind the owner's PIN the leftover wallet can be restored from the phone.
            assertEquals(
                listOf(RetainedWallet(leftover.address, needsRecoveryPhrase = false)),
                launch.userRepository.retainedWallets(),
            )
            assertEquals(listOf(leftover.address), launch.interactor.walletsRestorableWithPin())

            // The owner removes the only account before handing the phone on.
            launch.remove(main.address)

            val after = launch.dataStore.data.first().asMap().mapKeys { it.key.name }
            // The PIN and the removed account's keys are gone; the leftover set is untouched.
            assertFalse(after.containsKey("user_pin_code"))
            assertTrue(after.keys.none { it.endsWith(main.address) })
            launch.assertCiphertextsKept(leftoverBefore)
            // And it is marked, in the same edit, as needing its own recovery phrase.
            assertEquals(
                setOf(leftover.address),
                WalletPreferenceKeys.decodeRetainedNeedsPhrase(
                    after[WalletPreferenceKeys.RETAINED_NEEDS_PHRASE] as String?
                ),
            )
        }
        // The next person opens the app.
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(
                listOf(RetainedWallet(leftover.address, needsRecoveryPhrase = true)),
                launch.userRepository.retainedWallets(),
            )
            // Onboarding does not offer it: no PIN is stored, and it is marked.
            assertEquals(emptyList<String>(), launch.interactor.walletsRestorableWithPin())
            // They set their own PIN (as after creating any wallet). Still nothing to restore.
            launch.userRepository.savePin("246810")
            assertEquals(emptyList<String>(), launch.interactor.walletsRestorableWithPin())
            val restored = runCatching {
                launch.interactor.restoreRetainedWallets("246810", "Restored wallet")
            }.getOrThrow()
            assertTrue(restored.restored.isEmpty() && restored.notRestorable.isEmpty())
            // "Restore this wallet" in Login & Security is refused by the repository itself.
            val refusal = runCatching {
                launch.userRepository.adoptRetainedWallet(leftover.address, "Restored wallet")
            }.exceptionOrNull()
            assertEquals("RETAINED_WALLET_NEEDS_RECOVERY_PHRASE", refusal?.message)
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            launch.assertCiphertextsKept(leftoverBefore)

            // Whoever knows the wallet's recovery phrase brings it back: the import adopts the
            // stored keys unchanged.
            launch.importPhrase(TWENTY_FOUR_WORDS, "Mine")
            launch.assertCiphertextsKept(leftoverBefore)
            launch.assertUsable(listOf(leftover), selected = leftover)
            // Proven by its own phrase, it is this person's wallet again: the mark is gone
            // (the marker itself is removed when it names nothing).
            assertEquals(emptySet<String>(), launch.credentialsDatasource.retainedKeySetsNeedingPhrase())
            assertFalse(
                launch.dataStore.data.first().asMap().keys
                    .any { it.name == WalletPreferenceKeys.RETAINED_NEEDS_PHRASE }
            )
        }
        // Later the database is lost ("Set up this device again", database only). The wallet
        // is found behind the PIN its owner set, like any other wallet of theirs.
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            WalletUpgradeBackup.noteDatabaseCorruption(context)
            assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isSuccess)
        }
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            assertEquals(
                listOf(RetainedWallet(leftover.address, needsRecoveryPhrase = false)),
                launch.userRepository.retainedWallets(),
            )
            assertEquals(listOf(leftover.address), launch.interactor.walletsRestorableWithPin())
            val result = launch.interactor.restoreRetainedWallets("246810", "Restored wallet")
            assertEquals(listOf(leftover.address), result.restored.map { it.substrateAddress })
            launch.assertCiphertextsKept(leftoverBefore)
            launch.assertUsable(listOf(leftover), selected = leftover)
        }
    }

    @Test
    fun removeAllAccountsMarksTheReleaseEraSetAndItsPhraseBringsItBack() = runBlocking {
        // A 2.3.3 wallet, migrated by 2.4.0. A later release added a second account and removed
        // the original one: its address-scoped keys and row are gone, the unsuffixed keys and
        // prefs_address_pure remain. That leftover is the one most likely to hold funds.
        val original = phraseWallet(TWELVE_WORDS)
        val added = phraseWallet(TWENTY_FOUR_WORDS)
        val legacy = release233 { install(original, name = "Original") }
        room(58)
        release240 { migrate() }
        release3863 {
            created(added)
            finishedAccount(added)
            select(added)
        }
        HistoricalRoom.insertAccounts(context, listOf(added.address to "Added"))
        releasedRemoval(original.address)
        val unsuffixed = legacy.filterKeys { it != "user_pin_code" }

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(added), selected = added)
            // Behind the owner's PIN the release-era wallet can be restored from the phone.
            assertEquals(
                listOf(RetainedWallet(original.address, needsRecoveryPhrase = false)),
                launch.userRepository.retainedWallets(),
            )

            // The owner removes the only account.
            launch.remove(added.address)

            val after = launch.dataStore.data.first().asMap().mapKeys { it.key.name }
            assertFalse(after.containsKey("user_pin_code"))
            assertTrue(after.keys.none { it.endsWith(added.address) })
            // The release-era set stays byte for byte, with its owner record, and is marked by
            // its own id in the same edit.
            launch.assertCiphertextsKept(unsuffixed)
            assertEquals(original.address, launch.credentialsDatasource.getAddress())
            assertEquals(
                setOf(WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID),
                WalletPreferenceKeys.decodeRetainedNeedsPhrase(
                    after[WalletPreferenceKeys.RETAINED_NEEDS_PHRASE] as String?
                ),
            )
        }
        // The next person opens the app and sets their own PIN.
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            assertEquals(
                listOf(RetainedWallet(original.address, needsRecoveryPhrase = true)),
                launch.userRepository.retainedWallets(),
            )
            assertEquals(emptyList<String>(), launch.interactor.walletsRestorableWithPin())
            // What Safe Mode goes by: the owner is listed for a phrase, and that phrase is the
            // release-era one (nothing is stored under the address), so the mark hides it.
            WalletUpgradeBackup.recoveryFacts().let { facts ->
                assertEquals(original.address, facts.legacyOwner)
                assertTrue(original.address in facts.phraseWalletIds)
                assertFalse(original.address in facts.scopedPhraseWalletIds)
            }
            launch.userRepository.savePin("246810")
            assertEquals(emptyList<String>(), launch.interactor.walletsRestorableWithPin())
            val refusal = runCatching {
                launch.userRepository.adoptRetainedWallet(original.address, "Restored wallet")
            }.exceptionOrNull()
            assertEquals("RETAINED_WALLET_NEEDS_RECOVERY_PHRASE", refusal?.message)
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            launch.assertCiphertextsKept(unsuffixed)

            // Its own recovery phrase brings it back at the same address. The release-era
            // values are not touched, and their mark goes with the proof.
            launch.importPhrase(TWELVE_WORDS, "Mine again")
            launch.assertUsable(listOf(original), selected = original)
            launch.assertCiphertextsKept(unsuffixed)
            assertEquals(emptySet<String>(), launch.credentialsDatasource.retainedKeySetsNeedingPhrase())
        }
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(original), selected = original)
            // The import stored the phrase under the address: it is the account's own now.
            assertTrue(original.address in WalletUpgradeBackup.recoveryFacts().scopedPhraseWalletIds)
        }
    }

    @Test
    fun walletsFoundOnThisPhoneAreRestoredFromOnboardingBehindThePin() = runBlocking {
        val first = phraseWallet(TWELVE_WORDS)
        // Imported from a raw seed: no phrase was ever written down.
        val second = rawSeedWallet(RAW_SEED)
        val before = release3863 {
            created(first)
            importedSeed(second)
            finishedAccount(first)
            finishedAccount(second)
            select(first)
            registrationFinished()
        }
        room(73, first to "First", second to "Second")
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            launch.assertUsable(listOf(first, second), selected = first)
            // While both are accounts, nothing waits to be restored.
            assertEquals(emptyList<String>(), launch.interactor.walletsRestorableWithPin())
        }
        // The database is set aside ("Set up this device again", database only); the keys and
        // the PIN stay.
        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            WalletUpgradeBackup.noteDatabaseCorruption(context)
            assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isSuccess)
        }
        candidate(newProcess = true) { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            // Onboarding: no account, but two wallets are found behind the PIN used here.
            assertEquals(0, launch.userRepository.getSoraAccountsCount())
            assertEquals(
                setOf(first.address, second.address),
                launch.interactor.walletsRestorableWithPin().toSet(),
            )

            // A wrong PIN restores nothing and is counted like on the PIN screen.
            val wrong = runCatching {
                launch.interactor.restoreRetainedWallets("000000", "Restored wallet")
            }.exceptionOrNull()
            assertEquals(ResponseCode.WRONG_PIN_CODE, (wrong as? SoraException)?.errorResponseCode)
            assertEquals(1, launch.userRepository.retrievePinTriesUsed())
            assertEquals(0, launch.userRepository.getSoraAccountsCount())

            // The PIN that was used on this phone restores both, with every stored byte kept.
            val result = launch.interactor.restoreRetainedWallets("135790", "Restored wallet")
            assertEquals(
                setOf(first.address, second.address),
                result.restored.map { it.substrateAddress }.toSet(),
            )
            assertTrue(result.notRestorable.isEmpty())
            assertEquals(0, launch.userRepository.retrievePinTriesUsed())
            launch.assertCiphertextsKept(before)
            launch.assertUsable(
                listOf(first, second),
                selected = listOf(first, second).first { it.address == result.restored.first().substrateAddress },
            )
        }
    }

    @Test
    fun aPinSetForTheFirstTimeNeverUnlocksKeySetsThatWereOnThePhoneBeforeIt() = runBlocking {
        // 3.8.6.3 stored a new wallet's keys at "Continue". This one was abandoned before any
        // account or PIN existed on the phone.
        val abandoned = phraseWallet(TWENTY_FOUR_WORDS)
        val mine = phraseWallet(TWELVE_WORDS)
        val before = release3863 { created(abandoned) }
        room(73)

        candidate { launch ->
            assertTrue(launch.prepare.isSuccess)
            assertEquals(MigrationOutcome.Verified, launch.verify())
            // Onboarding: no PIN is stored, so nothing is offered to restore from the phone.
            assertEquals(emptyList<String>(), launch.interactor.walletsRestorableWithPin())

            // Someone sets the app up with their own wallet and their own PIN.
            launch.importPhrase(TWELVE_WORDS, "Mine")
            launch.userRepository.savePin("246810")

            // The key set that was there before that PIN is marked, byte for byte unchanged...
            launch.assertCiphertextsKept(before)
            assertEquals(
                listOf(RetainedWallet(abandoned.address, needsRecoveryPhrase = true)),
                launch.userRepository.retainedWallets(),
            )
            // ...and the new PIN does not restore it.
            val refusal = runCatching {
                launch.userRepository.adoptRetainedWallet(abandoned.address, "Restored wallet")
            }.exceptionOrNull()
            assertEquals("RETAINED_WALLET_NEEDS_RECOVERY_PHRASE", refusal?.message)
            assertTrue(launch.interactor.restoreRetainedWallets("246810", "Restored wallet").restored.isEmpty())
            launch.assertUsable(listOf(mine), selected = mine)

            // Changing the PIN later marks nothing new and keeps the mark.
            launch.userRepository.savePin("135790")
            assertEquals(
                listOf(RetainedWallet(abandoned.address, needsRecoveryPhrase = true)),
                launch.userRepository.retainedWallets(),
            )

            // Its own recovery phrase adopts the stored keys unchanged.
            launch.importPhrase(TWENTY_FOUR_WORDS, "Found again")
            launch.assertCiphertextsKept(before)
            launch.assertUsable(listOf(mine, abandoned), selected = abandoned)
            // Proven by its phrase, it is no longer marked.
            assertEquals(emptySet<String>(), launch.credentialsDatasource.retainedKeySetsNeedingPhrase())
        }
    }

    /** Runs [block] on the main thread, where screens (and their LiveData) are created. */
    private fun <T> onMainThread(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    /** Waits until a screen's state shows [what]. */
    private suspend fun awaitOnScreen(what: String, condition: () -> Boolean) {
        val shown = withTimeoutOrNull(SCREEN_TIMEOUT_MS) {
            while (!onMainThread(condition)) delay(SCREEN_POLL_MS)
            true
        }
        assertTrue("not shown: $what", shown == true)
    }

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

    private fun release2021(block: Release2021Process.() -> Unit): Map<String, Any> {
        val process = Release2021Process()
        return try {
            process.block()
            process.snapshot()
        } finally {
            process.end()
        }
    }

    private fun release161(block: Release161Process.() -> Unit): Map<String, Any> {
        val process = Release161Process()
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

    /** A release that wrote plain SharedPreferences `sora_prefs` (1.6.1, 2.1.0, 3.0.0). */
    private open inner class SharedPreferencesProcess {
        private val shared = context.getSharedPreferences("sora_prefs", Context.MODE_PRIVATE)
        val prefs = Release300Preferences(shared)
        val encryption = Release3863EncryptionUtil(context)
        val encrypted = Release300EncryptedPreferences(prefs, encryption)

        fun snapshot(): Map<String, Any> =
            shared.all.filterValues { it != null }.mapValues { (_, value) -> value as Any }

        fun end() {
            // The released code wrote with apply(); a process ends after those writes reach disk.
            shared.edit().commit()
            context.getSharedPreferences("key_alias", Context.MODE_PRIVATE).edit().commit()
        }
    }

    private inner class Release2021Process : SharedPreferencesProcess() {
        val user = Release300PrefsUserDatasource(prefs, encrypted)

        /**
         * CredentialsRepositoryImpl.restoreUserCredentials at c8f87a48 / 5011848c
         * (generateEntropyAndKeys, then generateAndSaveClaimData), then the PIN and the finished
         * registration. [plainAddress] picks the 3.0.0 or the 2.1.0 credentials datasource.
         */
        fun restored(wallet: Fixture, name: String?, plainAddress: Boolean) {
            val keypair = FearlessKeypair(
                wallet.keypair.privateKey,
                wallet.keypair.publicKey,
                wallet.keypair.nonce,
            )
            val claimKeys = V1_DID_KEY_PAIR
            if (plainAddress) {
                Release300PrefsCredentialsDatasource(encrypted, prefs).apply {
                    saveAddress(wallet.address)
                    saveKeys(keypair)
                    saveMnemonic(requireNotNull(wallet.mnemonic))
                    saveIrohaAddress(V1_IROHA_ACCOUNT_ID)
                    saveIrohaKeys(claimKeys)
                    saveSignature(ByteArray(64) { it.toByte() })
                }
            } else {
                Release210PrefsCredentialsDatasource(encrypted).apply {
                    saveAddress(wallet.address)
                    saveKeys(keypair)
                    saveMnemonic(requireNotNull(wallet.mnemonic))
                    saveIrohaAddress(V1_IROHA_ACCOUNT_ID)
                    saveIrohaKeys(claimKeys)
                    saveSignature(ByteArray(64) { it.toByte() })
                }
            }
            name?.let(user::saveAccountName)
            user.savePin("135790")
            user.saveRegistrationState("REGISTRATION_FINISHED")
            // PinCodeInteractor after the v1 migration check.
            user.saveNeedsMigration(false)
            user.saveIsMigrationFetched(true)
        }
    }

    private inner class Release161Process : SharedPreferencesProcess() {
        val did = Release161PrefsDidDatasource(encrypted)
        val user = Release161PrefsUserDatasource(prefs, encrypted)

        /** DidRepositoryImpl.registerUserDdo at d9e84dfb, then the PIN and the finished registration. */
        fun registered(phrase: String) {
            did.saveDdo(V1_DDO_JSON)
            did.saveKeys(V1_DID_KEY_PAIR)
            did.saveMnemonic(phrase)
            user.savePin("135790")
            user.saveRegistrationState("REGISTRATION_FINISHED")
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

        /**
         * CredentialsRepositoryImpl.restoreUserCredentialsFromRawSeed at b7b0f0fc for a seed
         * [typed] as it was pasted: `generateEntropyAndKeysFromRawSeed(rawSeed.removeHexPrefix())`
         * derives the keys from `rawSeed.fromHex()` (BouncyCastle `Hex.decode`, which skips
         * spaces, tabs and line breaks) and stores the text itself with `saveSeed(rawSeed, ..)`.
         */
        suspend fun importedSeedAsTyped(wallet: Fixture, typed: String) {
            val rawSeed = typed.removePrefix("0x")
            // 3.8.6.3 accepted it (isRawSeedValid) and derived this wallet's key pair from it.
            assertEquals(32, rawSeed.fromHex().size)
            assertArrayEquals(HistoricalSr25519Vectors.hex(wallet.seedHex), rawSeed.fromHex())
            credentials.saveKeys(wallet.keypair, wallet.address)
            credentials.saveSeed(rawSeed, wallet.address)
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

        /** 3.8.2.0 (and 3.6.0.0 to 3.8.1.1): "Show raw seed" or the account-screen Google backup. */
        suspend fun showRawSeedOn3820(wallet: Fixture): String =
            Release3820SeedCache(credentials).retrieveSeed(wallet.address)

        /** The same screens on 3.8.6.3. */
        suspend fun showRawSeedOn3863(wallet: Fixture): String =
            Release3863SeedCache(credentials).retrieveSeed(wallet.address)

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

        /**
         * 2.3.2 "Restore account": RecoveryViewModel -> CredentialsRepositoryImpl
         * .restoreUserCredentials([typed]) and the onboarding preferences. [typed] is the
         * recovery text exactly as entered (lower-cased by the input filter), commas included.
         */
        suspend fun restoredOn232(wallet: Fixture, typed: String, name: String) {
            Release232CredentialsRepository(
                credentialsPrefs = Release232PrefsCredentialsDatasource(encrypted, prefs),
                keyPairOf = { mnemonic ->
                    // The same words give the same key pair whatever separates them.
                    val keypair = phraseWallet(mnemonic).keypair
                    FearlessKeypair(keypair.privateKey, keypair.publicKey, keypair.nonce)
                },
                toSoraAddress = { publicKey -> requireNotNull(codec.toSoraAddressOrNull(publicKey)) },
            ).restoreUserCredentials(typed)
            user.saveAccountName(name)
            user.saveNeedsMigration(false)
            user.saveIsMigrationFetched(true)
            user.saveRegistrationState("REGISTRATION_FINISHED")
            user.savePin("135790")
        }

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
            // The production encoder of the encrypted JSON export and its import. (Only the
            // batch export uses CryptoAssistant; JsonAccountsExportRoundTripTest covers it.)
            JsonAccountsEncoder(Gson(), mockk(relaxed = true), JsonSeedEncoder(Gson())),
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

        /** SplashInteractor.getMigrationFailureCode: the code for the recovery screen. */
        suspend fun failureCode(): String? = migrationManager.failureCode()

        /** SplashInteractor.checkMigration: the app's screens start the verification. */
        fun startScreens(): Boolean = WalletUpgradeBackup.authorizeAutomaticMigrationRetry(context)

        /** The confirmed "Remove account" flow (preview, then confirm). */
        suspend fun remove(vararg addresses: String) {
            val preview = userRepository.createWalletDeletionPreview(addresses.toList())
            userRepository.confirmAndExecuteWalletDeletion(preview)
        }

        suspend fun identity(wallet: Fixture) =
            requireNotNull(database.walletIdentityDao().getWallet(wallet.address))

        /**
         * The production import and repair entry point of the app, on the real repositories of
         * this process (real Keystore, DataStore, Room and native sr25519). Its three other
         * constructor parameters are never used by an import or a repair.
         */
        val interactor by lazy {
            MultiaccountInteractor(
                userRepository = userRepository,
                credentialsRepository = credentials,
                fileManager = mockk(relaxed = true),
                runtimeManager = mockk(relaxed = true),
                walletRepository = mockk(relaxed = true),
            )
        }

        /**
         * Import account, Google: the backups the list offers on this phone, and what restoring
         * each does. (`OnboardingViewModel.getBackupedAccountsFiltered` asks exactly this.)
         */
        suspend fun backupRestores(vararg wallets: Fixture): Map<String, BackupRestore> =
            interactor.backupRestores(wallets.map(Fixture::address))

        /** Import account, Passphrase. */
        suspend fun importPhrase(phrase: String, name: String): SoraAccount =
            interactor.importFromMnemonic(phrase, name)

        /** Import account, Raw seed. */
        suspend fun importSeed(seedHex: String, name: String): SoraAccount =
            interactor.importFromRawSeed(seedHex, name)

        /** Import account, Backup file (JSON), for a one-account file. */
        suspend fun importJson(json: String, password: String, name: String): SoraAccount =
            interactor.importFromJson(json, password, name).imported.single()

        /** The confirmed repair from a one-account backup file. */
        suspend fun repairWithJson(json: String, password: String, name: String, pin: String): SoraAccount =
            interactor.repairFromJson(json, password, name, pin)

        /** The confirmed repair from a recovery phrase. */
        suspend fun repairWithPhrase(phrase: String, name: String, pin: String): SoraAccount =
            interactor.repairFromMnemonic(phrase, name, pin)

        /** What the last verification run found for [wallet]'s stored secrets. */
        fun report(wallet: Fixture) = migrationManager.lastSecretsReports.getValue(wallet.address)

        suspend fun journal() = requireNotNull(
            database.walletIdentityDao().getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        )

        suspend fun backupMaterial(wallet: Fixture) =
            credentials.retrieveBackupMaterial(SoraAccount(wallet.address, ""))

        suspend fun networkIds(wallet: Fixture): Set<String> =
            database.walletIdentityDao().getNetworkAccounts(wallet.address).map { it.networkId }.toSet()

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

    /**
     * A wallet as a released app stored it. The stored key bytes are the checked-in vector of
     * the released native library for this phrase (HistoricalSr25519Vectors: schnorrkel 0.9.1
     * called as the shipped `sr25519-java` calls it), not whatever today's library returns.
     * Today's library must derive exactly those bytes, which is asserted here for every fixture.
     */
    private fun phraseWallet(phrase: String): Fixture {
        val words = MnemonicCreator.fromWords(phrase).words
        val vector = requireNotNull(HistoricalSr25519Vectors.byPhrase(words)) {
            "no historical key vector for this phrase"
        }
        val seed = SubstrateSeedFactory.deriveSeed32(words, null).seed
        assertEquals(vector.seed, seed.toHexString())
        return historicalWallet(vector.id, mnemonic = words)
    }

    private fun rawSeedWallet(seedHex: String): Fixture {
        val vector = requireNotNull(HistoricalSr25519Vectors.bySeed(seedHex)) {
            "no historical key vector for this seed"
        }
        return historicalWallet(vector.id, mnemonic = null)
    }

    private fun historicalWallet(vectorId: String, mnemonic: String?): Fixture {
        val vector = HistoricalSr25519Vectors.byId(vectorId)
        val hex = HistoricalSr25519Vectors::hex
        val today = SubstrateKeypairFactory.generate(EncryptionType.SR25519, hex(vector.seed))
            as Sr25519Keypair
        assertEquals(vector.privateKey, today.privateKey.toHexString())
        assertEquals(vector.nonce, today.nonce.toHexString())
        assertEquals(vector.publicKey, today.publicKey.toHexString())
        assertEquals(vector.soraAddress, codec.toSoraAddressOrNull(hex(vector.publicKey)))
        return Fixture(
            address = vector.soraAddress,
            keypair = Sr25519Keypair(hex(vector.privateKey), hex(vector.publicKey), hex(vector.nonce)),
            mnemonic = mnemonic,
            seedHex = vector.seed,
        )
    }

    /** Copies of the old encrypted preference files kept by a confirmed in-place repair. */
    private fun repairCopies(): List<File> = context.noBackupFilesDir.listFiles().orEmpty()
        .filter { it.name.startsWith("wallet-quarantine-") && it.name.endsWith("-repaired-account") }

    private fun backups(): List<File> = context.noBackupFilesDir.listFiles().orEmpty()
        .filter { it.name.startsWith("wallet-upgrade-backup-v") }

    /** The one backup made before Room migrated a database of [sourceVersion]. */
    private fun preMigrationBackup(sourceVersion: Int): File =
        backups().single { it.name.startsWith("wallet-upgrade-backup-v$sourceVersion-to-77") }

    private fun soraPrefsXml(): File =
        File(context.applicationInfo.dataDir, "shared_prefs/sora_prefs.xml")

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
        const val SCREEN_TIMEOUT_MS = 15_000L
        const val SCREEN_POLL_MS = 50L

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

        /** Entropy 0x5a xor (37 i + 11), 24 bytes (generated with MnemonicCreator.fromEntropy). */
        const val EIGHTEEN_WORDS =
            "fabric expire sight menu twenty pretty piano eager weasel unit name submit buffalo " +
                "lemon drift prefer glide cat"
        const val RAW_SEED = "cf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
        const val JSON_PASSWORD = "Export-Password-1"

        /**
         * The Iroha Ed25519 DID key pair as SORA v1 stored it (`KeyPair.private/public.encoded`),
         * made by the same library (`Ed25519Sha3.generateKeypair(seed)`; `encoded` is the raw
         * 32-byte seed and public key). HARNESS: the library is a runtime-only dependency of
         * `common`, so it is called by reflection; v1 derived the seed with scrypt from its phrase
         * entropy, here it is a fixed value. 3.9.0 never derives anything from these keys.
         */
        val V1_DID_KEY_PAIR: KeyPair by lazy {
            val library = Class.forName("jp.co.soramitsu.crypto.ed25519.Ed25519Sha3")
            val ed25519 = library.getDeclaredConstructor().newInstance()
            library.getMethod("generateKeypair", ByteArray::class.java)
                .invoke(ed25519, ByteArray(32) { (it * 7 + 3).toByte() }) as KeyPair
        }
        val V1_DID_PUBLIC_KEY: ByteArray get() = V1_DID_KEY_PAIR.public.encoded
        const val V1_IROHA_ACCOUNT_ID = "did_sora_a0a1a2a3a4a5a6a7a8a9@sora"
        const val V1_DDO_JSON =
            "{\"@context\":\"https://www.w3.org/2019/did/v1\",\"id\":\"did:sora:a0a1a2a3a4a5a6a7a8a9\"," +
                "\"publicKey\":[{\"id\":\"did:sora:a0a1a2a3a4a5a6a7a8a9#keys-1\"," +
                "\"type\":\"Ed25519Sha3VerificationKey\"}]}"

        /** Valid Base64 of the right shape that no key decrypts (an authentication failure). */
        const val UNDECRYPTABLE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
