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
    fun `a marked key set that became an account again is listed`() {
        // Remove all accounts marked it; the person then imported its phrase. Should the mark
        // have outlived that import, it must not hide the phrase of a live account.
        val facts = RecoveryFacts(
            phraseWalletIds = listOf("cnAdopted", "cnLeftover"),
            accountNames = mapOf("cnAdopted" to "Mine"),
            scopedPhraseWalletIds = setOf("cnAdopted", "cnLeftover"),
        )

        val listing = SafeModeVault.listing(facts, needOwnPhrase = setOf("cnAdopted", "cnLeftover"))

        assertEquals(listOf("cnAdopted"), listing.entries.map { it.id })
        assertEquals(true, listing.withheld)
        assertEquals(false, listing.marksUnreadable)
    }

    @Test
    fun `the 2-x owner stands for the release-era set and is hidden when that set is marked`() {
        val unsuffixed = jp.co.soramitsu.common.data.WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID
        // prefs_address_pure names the owner; its only phrase is the unsuffixed one.
        val ownerOnlyReleaseEra = RecoveryFacts(
            phraseWalletIds = listOf("cnOwner"),
            legacyOwner = "cnOwner",
            hasUnscopedPhraseOrSeed = true,
        )

        assertEquals(
            emptyList<String>(),
            SafeModeVault.entries(ownerOnlyReleaseEra, needOwnPhrase = setOf(unsuffixed)).map { it.id },
        )
        // Also when the owner is listed as an account: the phrase that would be shown is still
        // the marked set's.
        assertEquals(
            emptyList<String>(),
            SafeModeVault.entries(
                ownerOnlyReleaseEra.copy(accountNames = mapOf("cnOwner" to "Old")),
                needOwnPhrase = setOf(unsuffixed),
            ).map { it.id },
        )
        // Not marked: listed as before.
        assertEquals(
            listOf("cnOwner"),
            SafeModeVault.entries(ownerOnlyReleaseEra, needOwnPhrase = emptySet()).map { it.id },
        )
        // A phrase stored under the owner's own address is the owner's, whatever the
        // release-era set's mark says.
        assertEquals(
            listOf("cnOwner"),
            SafeModeVault.entries(
                ownerOnlyReleaseEra.copy(scopedPhraseWalletIds = setOf("cnOwner")),
                needOwnPhrase = setOf(unsuffixed),
            ).map { it.id },
        )
    }

    @Test
    fun `unreadable marks are reported, not shown as nothing saved`() {
        // In a recovery state the database is often unreadable, so no wallet is known to be an
        // account. The list is then empty for a reason the screen must be able to say.
        val facts = RecoveryFacts(phraseWalletIds = listOf("cnOne", "cnTwo"))

        val unreadable = SafeModeVault.listing(facts, needOwnPhrase = null)
        assertEquals(emptyList<PhraseEntry>(), unreadable.entries)
        assertEquals(true, unreadable.marksUnreadable)
        assertEquals(false, unreadable.withheld)

        val readable = SafeModeVault.listing(facts, needOwnPhrase = emptySet())
        assertEquals(listOf("cnOne", "cnTwo"), readable.entries.map { it.id })
        assertEquals(false, readable.marksUnreadable)
        assertEquals(false, readable.withheld)
    }

    @Test
    fun `a key set that needs its own recovery phrase is never listed for a reveal`() {
        // Left on the phone by "Remove all accounts": the PIN stored now never protected it.
        val facts = RecoveryFacts(
            phraseWalletIds = listOf("cnAccount", "cnLeftover"),
            accountNames = mapOf("cnAccount" to "Main"),
            hasUnscopedPhraseOrSeed = true,
        )

        assertEquals(
            listOf("cnAccount", SafeModeVault.OLDER_VERSION_ID),
            SafeModeVault.entries(facts, needOwnPhrase = setOf("cnLeftover")).map { it.id },
        )
        // The release-era unsuffixed set is hidden by its own id.
        assertEquals(
            listOf("cnAccount"),
            SafeModeVault.entries(
                facts,
                needOwnPhrase = setOf("cnLeftover", jp.co.soramitsu.common.data.WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID),
            ).map { it.id },
        )
        // When the marks cannot be read, only wallets that are accounts are listed.
        assertEquals(
            listOf("cnAccount"),
            SafeModeVault.entries(facts, needOwnPhrase = null).map { it.id },
        )
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
    fun `a phrase stored with commas shows as its words`() = runTest {
        val repository = mockk<CredentialsRepository>()
        val phrase =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        coEvery { repository.retrieveMnemonic(any()) } returns phrase.replace(" ", ",")
        val secret = SafeModeVault.reveal(
            PhraseEntry("cnA", "cnA", "Main", olderVersion = false),
            legacyOwner = null,
            credentialsRepository = repository,
            credentialsDatasource = mockk(),
        )
        assertEquals(RevealedSecret.Phrase(phrase.split(" ")), secret)
    }

    @Test
    fun `a phrase proven to belong to another wallet is never shown as confirmed`() = runTest {
        val repository = mockk<CredentialsRepository>()
        coEvery { repository.retrieveMnemonic(any()) } returns "alpha beta gamma"
        // No stored seed: the phrase is shown, marked as not confirmed for this wallet.
        coEvery { repository.retrieveStoredSeed(any()) } returns ""
        val entry = PhraseEntry("cnA", "cnA", "Main", olderVersion = false)
        val unconfirmed = SafeModeVault.reveal(entry, null, repository, mockk()) { secret ->
            if (secret is RevealedSecret.Phrase) "cnOther" else "cnA"
        }
        // Its own result: never "could not be confirmed", it is known to open another wallet.
        assertEquals(
            RevealedSecret.Phrase(listOf("alpha", "beta", "gamma"), checked = false, otherWallet = true),
            unconfirmed,
        )

        // A stored seed that derives this address is this wallet's backup and is shown instead.
        coEvery { repository.retrieveStoredSeed(any()) } returns "aa".repeat(32)
        val seed = SafeModeVault.reveal(entry, null, repository, mockk()) { secret ->
            if (secret is RevealedSecret.Phrase) "cnOther" else "cnA"
        }
        assertEquals(RevealedSecret.Seed("aa".repeat(32)), seed)

        // When the address cannot be derived at all, nothing is claimed either way.
        val unknown = SafeModeVault.reveal(entry, null, repository, mockk()) { null }
        assertEquals(RevealedSecret.Phrase(listOf("alpha", "beta", "gamma")), unknown)
    }

    @Test
    fun `a seed cached as 64 bytes shows the 32 bytes that derive the wallet, or is not confirmed`() =
        runTest {
            val front = "ab".repeat(32)
            val cached = front + "cd".repeat(32)
            coEvery { repository.retrieveMnemonic(any()) } throws WalletDecryptionException("damaged")
            coEvery { repository.retrieveStoredSeed(any()) } returns cached
            val entry = PhraseEntry("cnA", "cnA", null, false)

            // The first 32 bytes derive this wallet: they are its raw seed, in the form the
            // app's own import accepts (64 characters).
            val proven = SafeModeVault.reveal(entry, null, repository, datasource) { secret ->
                if ((secret as? RevealedSecret.Seed)?.value == front) "cnA" else null
            }
            assertEquals(RevealedSecret.Seed(front), proven)

            // Not proven (the address cannot be derived, or the front derives another wallet):
            // the 128 characters are never shown as a confirmed raw seed.
            assertEquals(
                RevealedSecret.Seed(cached, checked = false),
                SafeModeVault.reveal(entry, null, repository, datasource),
            )
            assertEquals(
                RevealedSecret.Seed(cached, checked = false),
                SafeModeVault.reveal(entry, null, repository, datasource) { "cnOther" },
            )
        }

    @Test
    fun `a seed stored with a line break or a prefix shows as its 64 hex characters`() = runTest {
        val seed = "0123456789abcdef".repeat(4)
        coEvery { repository.retrieveMnemonic(any()) } returns ""
        val entry = PhraseEntry("cnA", "cnA", null, false)
        listOf(
            seed.take(32) + "\n" + seed.drop(32),
            "0x" + seed.uppercase(),
            seed.chunked(8).joinToString(" "),
        ).forEach { stored ->
            coEvery { repository.retrieveStoredSeed(any()) } returns stored
            assertEquals(
                stored,
                RevealedSecret.Seed(seed),
                SafeModeVault.reveal(entry, null, repository, datasource) { "cnA" },
            )
        }
    }

    @Test
    fun `a seed proven to derive another wallet says so, and a value that is not a seed is not confirmed`() =
        runTest {
            val seed = "0123456789abcdef".repeat(4)
            coEvery { repository.retrieveMnemonic(any()) } returns ""
            coEvery { repository.retrieveStoredSeed(any()) } returns seed
            val entry = PhraseEntry("cnA", "cnA", null, false)
            assertEquals(
                RevealedSecret.Seed(seed, checked = false, otherWallet = true),
                SafeModeVault.reveal(entry, null, repository, datasource) { "cnOther" },
            )
            coEvery { repository.retrieveStoredSeed(any()) } returns "0xabc"
            assertEquals(
                RevealedSecret.Seed("0xabc", checked = false),
                SafeModeVault.reveal(entry, null, repository, datasource) { "cnA" },
            )
        }

    @Test
    fun `raw seed accounts show the stored seed, never a derived cache`() = runTest {
        val seed = "0123456789abcdef".repeat(4)
        coEvery { repository.retrieveMnemonic(any()) } returns ""
        coEvery { repository.retrieveStoredSeed(any()) } returns seed
        val secret = SafeModeVault.reveal(PhraseEntry("cnA", "cnA", null, false), null, repository, datasource)
        assertEquals(RevealedSecret.Seed(seed), secret)
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
        val seed = "0123456789abcdef".repeat(4)
        coEvery { repository.retrieveMnemonic(any()) } throws WalletDecryptionException("damaged")
        coEvery { repository.retrieveStoredSeed(any()) } returns seed
        assertEquals(
            RevealedSecret.Seed(seed),
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
