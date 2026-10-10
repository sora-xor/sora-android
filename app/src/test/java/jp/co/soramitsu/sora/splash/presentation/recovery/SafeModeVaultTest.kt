package jp.co.soramitsu.sora.splash.presentation.recovery

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import jp.co.soramitsu.common.account.SoraAccount
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
    fun `release era owner falls back to the unsuffixed phrase`() = runTest {
        coEvery { repository.retrieveMnemonic(any()) } returns ""
        coEvery { repository.retrieveStoredSeed(any()) } returns ""
        coEvery { datasource.retrieveMnemonic("") } returns "legacy words here"
        val secret = SafeModeVault.reveal(PhraseEntry("cnOwner", "cnOwner", null, false), "cnOwner", repository, datasource)
        assertEquals(RevealedSecret.Phrase(listOf("legacy", "words", "here")), secret)
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
        val attempts = SafeModePinAttempts(File(dir, SafeModePinAttempts.FILE_NAME)) { now }
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
    fun `a damaged attempts file reads as no attempts`() {
        val dir = Files.createTempDirectory("safe-mode").toFile()
        File(dir, SafeModePinAttempts.FILE_NAME).writeText("garbage")
        val attempts = SafeModePinAttempts(File(dir, SafeModePinAttempts.FILE_NAME))
        assertEquals(SafeModePinAttempts.State(0, 0), attempts.read())
    }
}
