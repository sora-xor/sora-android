package jp.co.soramitsu.sora.splash.presentation.recovery

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.core_db.RecoveryFacts
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModeVaultTest {

    private val repository = mockk<CredentialsRepository>()
    private val datasource = mockk<CredentialsDatasource>()

    @Test
    fun `entries come from key names and account names, release era phrase listed when unowned`() {
        val facts = RecoveryFacts(
            phraseWalletIds = listOf("cnA", "cnB"),
            accountNames = mapOf("cnA" to "Main", "cnB" to " "),
            hasUnscopedPhraseOrSeed = true,
        )
        val entries = SafeModeVault.entries(facts)
        assertEquals(listOf("cnA", "cnB", SafeModeVault.OLDER_VERSION_ID), entries.map { it.id })
        assertEquals("Main", entries[0].name)
        assertEquals(null, entries[1].name)
        assertTrue(entries[2].olderVersion)

        val owned = SafeModeVault.entries(facts.copy(legacyOwner = "cnA"))
        assertFalse(owned.any { it.olderVersion })
    }

    @Test
    fun `reveal reads the phrase and writes nothing`() = runTest {
        coEvery { repository.retrieveMnemonic(any()) } returns " one two  three "
        val secret = SafeModeVault.reveal(PhraseEntry("cnA", "cnA", "Main", false), null, repository, datasource)
        assertEquals(RevealedSecret.Phrase(listOf("one", "two", "three")), secret)
        coVerify(exactly = 1) { repository.retrieveMnemonic(SoraAccount("cnA", "Main")) }
        confirmVerified(repository, datasource)
    }

    @Test
    fun `raw seed accounts show the stored seed, never a derived cache`() = runTest {
        coEvery { repository.retrieveMnemonic(any()) } returns ""
        coEvery { repository.retrieveStoredSeed(any()) } returns "0xabc"
        val secret = SafeModeVault.reveal(PhraseEntry("cnA", "cnA", null, false), null, repository, datasource)
        assertEquals(RevealedSecret.Seed("0xabc"), secret)
        coVerify(exactly = 1) { repository.retrieveMnemonic(any()) }
        coVerify(exactly = 1) { repository.retrieveStoredSeed(any()) }
        coVerify(exactly = 0) { repository.retrieveSeed(any()) }
        confirmVerified(repository, datasource)
    }

    @Test
    fun `release era owner falls back to the unsuffixed phrase only as checked when it derives the address`() =
        runTest {
            coEvery { repository.retrieveMnemonic(any()) } returns ""
            coEvery { repository.retrieveStoredSeed(any()) } returns ""
            coEvery { datasource.retrieveMnemonic("") } returns "legacy words here"
            val owner = PhraseEntry("cnOwner", "cnOwner", null, false)
            assertEquals(
                RevealedSecret.Phrase(listOf("legacy", "words", "here"), checked = true),
                SafeModeVault.reveal(owner, "cnOwner", repository, datasource) { "cnOwner" },
            )
            // Derives another address, or cannot be derived: shown, but not as this wallet's.
            assertEquals(
                RevealedSecret.Phrase(listOf("legacy", "words", "here"), checked = false),
                SafeModeVault.reveal(owner, "cnOwner", repository, datasource) { "cnSomeoneElse" },
            )
            assertEquals(
                RevealedSecret.Phrase(listOf("legacy", "words", "here"), checked = false),
                SafeModeVault.reveal(owner, "cnOwner", repository, datasource) { error("not a phrase") },
            )
        }

    @Test
    fun `an unreadable phrase never hides a readable seed`() = runTest {
        coEvery { repository.retrieveMnemonic(any()) } throws WalletDecryptionException("damaged")
        coEvery { repository.retrieveStoredSeed(any()) } returns "0xabc"
        assertEquals(
            RevealedSecret.Seed("0xabc"),
            SafeModeVault.reveal(PhraseEntry("cnA", "cnA", null, false), null, repository, datasource),
        )
    }

    @Test
    fun `a value this phone cannot decrypt is reported as permanent, a busy keystore as passing`() = runTest {
        coEvery { repository.retrieveMnemonic(any()) } throws WalletDecryptionException("damaged")
        coEvery { repository.retrieveStoredSeed(any()) } returns ""
        assertEquals(
            RevealedSecret.Unreadable,
            SafeModeVault.reveal(PhraseEntry("cnA", "cnA", null, false), null, repository, datasource),
        )
        coEvery { repository.retrieveMnemonic(any()) } throws
            KeyMaterialUnavailableException("alias missing", transient = false)
        assertEquals(
            RevealedSecret.Unreadable,
            SafeModeVault.reveal(PhraseEntry("cnA", "cnA", null, false), null, repository, datasource),
        )
        coEvery { repository.retrieveMnemonic(any()) } throws
            KeyMaterialUnavailableException("busy", transient = true)
        assertEquals(
            RevealedSecret.Unavailable,
            SafeModeVault.reveal(PhraseEntry("cnA", "cnA", null, false), null, repository, datasource),
        )
    }

    @Test
    fun `nothing stored and unreadable values are reported, not thrown`() = runTest {
        coEvery { repository.retrieveMnemonic(any()) } returns ""
        coEvery { repository.retrieveStoredSeed(any()) } returns ""
        assertEquals(
            RevealedSecret.None,
            SafeModeVault.reveal(PhraseEntry("cnA", "cnA", null, false), "cnOther", repository, datasource),
        )
        coEvery { repository.retrieveMnemonic(any()) } throws IllegalStateException("busy keystore")
        assertEquals(
            RevealedSecret.Unavailable,
            SafeModeVault.reveal(PhraseEntry("cnA", "cnA", null, false), null, repository, datasource),
        )
    }

    @Test
    fun `pin comparison`() {
        assertTrue(SafeModeVault.pinMatches("123456", "123456"))
        assertFalse(SafeModeVault.pinMatches("123456", "123457"))
        assertFalse(SafeModeVault.pinMatches("", ""))
        assertFalse(SafeModeVault.pinMatches("1234", "123456"))
    }

    @Test
    fun `pin attempts lock like the pin screen and live outside the wallet preferences`() {
        val dir = Files.createTempDirectory("safe-mode").toFile()
        var now = 1_000_000L
        val attempts = SafeModePinAttempts(File(dir, SafeModePinAttempts.FILE_NAME), { now }, { now })
        assertEquals(0L, attempts.lockRemainingMillis())
        attempts.recordFailure()
        attempts.recordFailure()
        assertEquals(0L, attempts.lockRemainingMillis())
        attempts.recordFailure()
        assertEquals(60_000L, attempts.lockRemainingMillis())
        now += 60_000L
        assertEquals(0L, attempts.lockRemainingMillis())
        attempts.recordFailure()
        assertEquals(5 * 60_000L, attempts.lockRemainingMillis())
        attempts.reset()
        assertEquals(SafeModePinAttempts.State(0, 0), attempts.read())
        assertEquals(listOf(SafeModePinAttempts.FILE_NAME), dir.list()!!.toList())
    }

    @Test
    fun `a clock set back after a lock never latches it`() {
        val dir = Files.createTempDirectory("safe-mode").toFile()
        var now = 1_900_000_000_000L
        var uptime = 50_000L
        val file = File(dir, SafeModePinAttempts.FILE_NAME)
        repeat(3) { SafeModePinAttempts(file, { now }, { uptime }).recordFailure() }
        // The phone restarts (uptime starts again) and the clock is corrected by four years.
        now -= 4L * 365 * 24 * 60 * 60_000L
        uptime = 1_000L
        val afterRestart = SafeModePinAttempts(file, { now }, { uptime })
        assertEquals(60_000L, afterRestart.lockRemainingMillis())
        uptime += 60_000L
        now += 60_000L
        assertEquals(0L, afterRestart.lockRemainingMillis())
    }

    @Test
    fun `moving the clock forward does not lift a lock in the same boot`() {
        val dir = Files.createTempDirectory("safe-mode").toFile()
        var now = 1_000_000L
        var uptime = 10_000L
        val attempts = SafeModePinAttempts(File(dir, SafeModePinAttempts.FILE_NAME), { now }, { uptime })
        repeat(3) { attempts.recordFailure() }
        now += 24 * 60 * 60_000L
        assertEquals(60_000L, attempts.lockRemainingMillis())
        uptime += 60_000L
        assertEquals(0L, attempts.lockRemainingMillis())
    }

    @Test
    fun `wrong pins still lock when the attempts file cannot be written`() {
        val dir = Files.createTempDirectory("safe-mode").toFile()
        // The parent is a regular file, so nothing can be written below it (like a full disk).
        val blocked = File(dir, "not-a-directory").apply { writeText("x") }
        val attempts = SafeModePinAttempts(File(blocked, SafeModePinAttempts.FILE_NAME), { 1_000L }, { 5_000L })
        repeat(3) { attempts.recordFailure() }
        assertEquals(60_000L, attempts.lockRemainingMillis())
        attempts.recordFailure()
        assertEquals(5 * 60_000L, attempts.lockRemainingMillis())
    }

    @Test
    fun `a damaged attempts file reads as no attempts`() {
        val dir = Files.createTempDirectory("safe-mode").toFile()
        File(dir, SafeModePinAttempts.FILE_NAME).writeText("garbage")
        val attempts = SafeModePinAttempts(File(dir, SafeModePinAttempts.FILE_NAME))
        assertEquals(SafeModePinAttempts.State(0, 0), attempts.read())
    }
}
