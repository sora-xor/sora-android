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

package jp.co.soramitsu.sora.splash.presentation.recovery

import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.StoredSecretHex
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.core_db.RecoveryFacts
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import kotlinx.coroutines.CancellationException

/** One wallet whose recovery phrase or seed can be shown in Safe Mode. */
data class PhraseEntry(
    /** Stable key for the list. */
    val id: String,
    /** The SORA address, or null for a release-era phrase that no address owns. */
    val address: String?,
    val name: String?,
    /** A phrase saved without an address by an older SORA version. */
    val olderVersion: Boolean,
)

/** What "Show my recovery phrase" found for one entry. */
sealed interface RevealedSecret {
    /**
     * [checked] is false for a phrase an older SORA version saved without an address when SORA
     * could not confirm that it derives this wallet's address: it is shown, with that said.
     *
     * [otherWallet] is true when the value stored for this wallet is proven to derive a
     * different address. It is still shown (in this emergency screen it may be the only copy of
     * that other wallet's phrase), but never as this wallet's backup: the screen says plainly
     * that it opens a different wallet and that this wallet has no phrase on the phone.
     */
    data class Phrase(
        val words: List<String>,
        val checked: Boolean = true,
        val otherWallet: Boolean = false,
    ) : RevealedSecret

    /**
     * [value] is the 32-byte seed as 64 lower-case hex characters whenever the stored value
     * reads as 32 bytes, or holds the 32 bytes that derive this wallet's address in front (a
     * seed cached as 64 bytes by 3.6.0.0 to 3.8.2.0). That is the form Import account, Raw seed
     * accepts. Anything else is shown as stored with [checked] false.
     */
    data class Seed(
        val value: String,
        val checked: Boolean = true,
        val otherWallet: Boolean = false,
    ) : RevealedSecret

    /** Nothing is saved for this wallet on this phone. */
    data object None : RevealedSecret

    /** The value exists but could not be decrypted just now (for example a busy Keystore). */
    data object Unavailable : RevealedSecret

    /**
     * The value exists but this phone cannot decrypt it (its bytes are damaged, or the Keystore
     * key that encrypted it is not on this phone). Trying again will not help.
     */
    data object Unreadable : RevealedSecret
}

/**
 * Room-free, read-only access to the recovery phrases kept on this phone, for Safe Mode.
 *
 * - The list comes from [RecoveryFacts] (preference key names and account names gathered while
 *   the app started); nothing is decrypted to build it.
 * - Revealing reads values through the same decryption the app uses everywhere. Nothing is
 *   written, created or cached: no `put`, no derived-seed cache, no Room.
 */
object SafeModeVault {

    const val OLDER_VERSION_ID = "release-era-phrase"

    /** The wallets that may be listed, and what the list leaves out. */
    data class Listing(
        val entries: List<PhraseEntry>,
        /** A key set is not listed because only its own recovery phrase may bring it back. */
        val withheld: Boolean = false,
        /**
         * The marks could not be read just now, so a wallet that is not known to be an account
         * is not listed. Trying again may list it.
         */
        val marksUnreadable: Boolean = false,
    )

    /**
     * The wallets whose recovery phrase or seed can be shown.
     *
     * [needOwnPhrase] names the key sets that only their own recovery phrase may bring back
     * (`WalletPreferenceKeys.RETAINED_NEEDS_PHRASE`: they were on the phone before the PIN that
     * is stored now, for example left by "Remove all accounts"). Neither that PIN nor the
     * phone's screen lock shows their phrase, so they are not listed. Null means the marks
     * could not be read: then only wallets that are accounts are listed, and the listing says
     * that it may be incomplete.
     *
     * - A wallet that is an account is the person's own and is listed whatever a mark says
     *   (a mark is taken off when its wallet is imported; one that outlived that must not hide
     *   a live account's phrase).
     * - The 2.x owner whose only phrase or seed is the release-era (unsuffixed) one stands for
     *   that unsuffixed set: it is hidden exactly when that set is marked.
     */
    fun listing(
        facts: RecoveryFacts,
        needOwnPhrase: Set<String>? = emptySet(),
    ): Listing {
        val all = allEntries(facts)
        val unsuffixedMarked =
            needOwnPhrase == null || WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID in needOwnPhrase
        val listed = all.filter { entry ->
            val address = entry.address
            when {
                address == null -> !unsuffixedMarked
                // Its phrase is the release-era one (nothing is stored under the address).
                address == facts.legacyOwner && address !in facts.scopedPhraseWalletIds ->
                    !unsuffixedMarked
                address in facts.accountNames -> true
                needOwnPhrase == null -> false
                else -> address !in needOwnPhrase
            }
        }
        val leftOut = listed.size < all.size
        return Listing(
            entries = listed,
            withheld = leftOut && needOwnPhrase != null,
            marksUnreadable = leftOut && needOwnPhrase == null,
        )
    }

    fun entries(
        facts: RecoveryFacts,
        needOwnPhrase: Set<String>? = emptySet(),
    ): List<PhraseEntry> = listing(facts, needOwnPhrase).entries

    private fun allEntries(facts: RecoveryFacts): List<PhraseEntry> = buildList {
        facts.phraseWalletIds.distinct().forEach { address ->
            add(
                PhraseEntry(
                    id = address,
                    address = address,
                    name = facts.accountNames[address]?.takeIf(String::isNotBlank),
                    olderVersion = false,
                ),
            )
        }
        // A release-era phrase with no recorded owner (for example a SORA v1 phrase) is never
        // hidden: it is listed on its own.
        if (facts.hasUnscopedPhraseOrSeed && facts.legacyOwner.isNullOrBlank()) {
            add(PhraseEntry(OLDER_VERSION_ID, address = null, name = null, olderVersion = true))
        }
    }

    /** One stored value: its text (empty when nothing is stored), or why it could not be read. */
    private sealed interface Read {
        data class Value(val text: String) : Read
        data class Failed(val permanent: Boolean) : Read
    }

    private suspend fun read(block: suspend () -> String): Read = try {
        Read.Value(block().trim())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Read.Failed(permanent = isPermanent(error))
    }

    /**
     * Decryption of these exact bytes failed, or this phone has no Keystore key for them: the
     * same result every time. Anything else (a busy Keystore, I/O) may pass on a later try.
     */
    private fun isPermanent(error: Throwable): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 8) {
            when (current) {
                is KeyMaterialUnavailableException -> return !current.transient
                is WalletDecryptionException -> return true
            }
            current = current.cause
            depth += 1
        }
        return false
    }

    /**
     * Reads the phrase and, separately, the stored seed: an unreadable phrase never hides a seed
     * that can be read. [addressOf] derives the SORA address of a phrase or seed in memory (no
     * writes); it is used only for the release-era value shown for the 2.x owner, which is
     * marked as not checked unless it derives exactly that address.
     */
    suspend fun reveal(
        entry: PhraseEntry,
        legacyOwner: String?,
        credentialsRepository: CredentialsRepository,
        credentialsDatasource: CredentialsDatasource,
        addressOf: suspend (RevealedSecret) -> String? = { null },
    ): RevealedSecret {
        val address = entry.address ?: return releaseEra(credentialsDatasource)
        val account = SoraAccount(address, entry.name.orEmpty())
        val phrase = read { credentialsRepository.retrieveMnemonic(account) }
        if (phrase is Read.Value && phrase.text.isNotEmpty()) {
            val shown = RevealedSecret.Phrase(phraseWords(phrase.text))
            if (!derivesAnotherWallet(shown, address, addressOf)) return shown
            // The stored phrase is proven to belong to another wallet. A stored seed that
            // derives this address is this wallet's backup; otherwise the phrase is shown as
            // what it is: the phrase of a different wallet, not a backup of this one.
            val otherSeed = read { credentialsRepository.retrieveStoredSeed(account) }
            if (otherSeed is Read.Value && otherSeed.text.isNotEmpty()) {
                val seedShown = storedSeed(otherSeed.text, address, addressOf)
                if (seedShown.checked && derives(seedShown, address, addressOf)) return seedShown
            }
            if (address == legacyOwner) {
                // The 2.x owner's unsuffixed value is this wallet's backup when it derives this
                // address, whatever is stored under the address itself.
                when (val older = releaseEra(credentialsDatasource)) {
                    is RevealedSecret.Phrase -> if (derives(older, address, addressOf)) return older
                    is RevealedSecret.Seed -> if (derives(older, address, addressOf)) return older
                    else -> Unit
                }
            }
            return shown.copy(checked = false, otherWallet = true)
        }
        // The seed is read only when no phrase is stored.
        val seed = read { credentialsRepository.retrieveStoredSeed(account) }
        val storedSeed = (seed as? Read.Value)?.text?.takeIf(String::isNotEmpty)
        if (storedSeed != null) return storedSeed(storedSeed, address, addressOf)
        if (address == legacyOwner) {
            // The 2.x owner's unsuffixed value. The repository already returns it above when the
            // unsuffixed keys derive this address; here they are missing, unreadable or derive
            // another address, so it is checked against the address before it is shown as this
            // wallet's phrase.
            when (val older = releaseEra(credentialsDatasource)) {
                is RevealedSecret.Phrase -> return older.copy(checked = derives(older, address, addressOf))
                is RevealedSecret.Seed -> return older.copy(checked = derives(older, address, addressOf))
                else -> Unit
            }
        }
        val failures = listOf(phrase, seed).filterIsInstance<Read.Failed>()
        return when {
            failures.isEmpty() -> RevealedSecret.None
            failures.all { it.permanent } -> RevealedSecret.Unreadable
            else -> RevealedSecret.Unavailable
        }
    }

    /**
     * The seed stored for [address] as it may be shown.
     * - 32 bytes: shown as 64 lower-case hex characters (spaces, line breaks and a prefix in the
     *   stored text are not part of the seed). It is marked as another wallet's only when it is
     *   proven to derive a different address.
     * - Longer, with the 32 bytes that derive this address in front (the 64-byte value cached by
     *   3.6.0.0 to 3.8.2.0): those 32 bytes are shown. They are this wallet's raw seed, proven
     *   by deriving its address, and they are what Import account, Raw seed accepts.
     * - Anything else (another length, not hex, or 64 bytes whose first half does not derive
     *   this address): shown as stored and marked as not confirmed. It is never presented as a
     *   confirmed raw seed, because the app's own import would not accept it.
     */
    private suspend fun storedSeed(
        text: String,
        address: String,
        addressOf: suspend (RevealedSecret) -> String?,
    ): RevealedSecret.Seed {
        val bytes = StoredSecretHex.decodeOrNull(text)
            ?: return RevealedSecret.Seed(text, checked = false)
        try {
            if (bytes.size == RecoveryPhrase.MINI_SEED_BYTES) {
                val shown = RevealedSecret.Seed(StoredSecretHex.encode(bytes))
                val other = derivesAnotherWallet(shown, address, addressOf)
                return shown.copy(checked = !other, otherWallet = other)
            }
            if (bytes.size > RecoveryPhrase.MINI_SEED_BYTES) {
                val front = bytes.copyOfRange(0, RecoveryPhrase.MINI_SEED_BYTES)
                try {
                    val shown = RevealedSecret.Seed(StoredSecretHex.encode(front))
                    if (derives(shown, address, addressOf)) return shown
                } finally {
                    front.fill(0)
                }
            }
            return RevealedSecret.Seed(text, checked = false)
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * The words as the library parser reads them (a phrase stored with commas by an old version
     * shows as its words); the stored text split on spaces when it is not a valid phrase.
     */
    private fun phraseWords(text: String): List<String> =
        RecoveryPhrase.parseOrNull(text)?.words ?: text.split(Regex("\\s+"))

    /** True only when the derivation succeeded and gave a different address. */
    private suspend fun derivesAnotherWallet(
        secret: RevealedSecret,
        address: String,
        addressOf: suspend (RevealedSecret) -> String?,
    ): Boolean = try {
        addressOf(secret)?.let { it != address } ?: false
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }

    private suspend fun derives(
        secret: RevealedSecret,
        address: String,
        addressOf: suspend (RevealedSecret) -> String?,
    ): Boolean = try {
        addressOf(secret) == address
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }

    private suspend fun releaseEra(datasource: CredentialsDatasource): RevealedSecret {
        val phrase = read { datasource.retrieveMnemonic("") }
        if (phrase is Read.Value && phrase.text.isNotEmpty()) {
            return RevealedSecret.Phrase(phraseWords(phrase.text))
        }
        val seed = read { datasource.retrieveSeed("") }
        if (seed is Read.Value && seed.text.isNotEmpty()) return RevealedSecret.Seed(seed.text)
        val failures = listOf(phrase, seed).filterIsInstance<Read.Failed>()
        return when {
            failures.isEmpty() -> RevealedSecret.None
            failures.all { it.permanent } -> RevealedSecret.Unreadable
            else -> RevealedSecret.Unavailable
        }
    }

    /** Constant-time PIN comparison. */
    fun pinMatches(stored: String, entered: String): Boolean =
        stored.isNotEmpty() &&
            MessageDigest.isEqual(stored.toByteArray(Charsets.UTF_8), entered.toByteArray(Charsets.UTF_8))
}

/**
 * Safe Mode PIN attempts, kept in `no_backup/safe-mode-pin-attempts` and never in the wallet
 * preferences (in a recovery state the wallet preference file must not be rewritten). The
 * lockout steps match the PIN screen: 3 wrong tries lock for 1 minute, then 5, 15 and 30.
 *
 * - The count and the lock are also kept in memory for the life of the process, so they hold
 *   when the file cannot be written (for example on a full disk).
 * - A lock is timed with the phone's uptime clock, which changing the date or time does not
 *   move. After a restart of the phone the wall clock is used, and a lock never lasts longer
 *   than its step: a clock that was set back starts the step again once, it does not latch.
 */
class SafeModePinAttempts(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
) {
    /** [lockedAtElapsed] is the uptime when the lock started, or -1 when unknown. */
    data class State(val failures: Int, val lockedUntilMillis: Long, val lockedAtElapsed: Long = -1)

    private var memoryFailures = 0
    private var memoryLockUntilElapsed = 0L

    fun read(): State = runCatching {
        val parts = file.readText().trim().split(',')
        State(
            failures = parts[0].toInt().coerceAtLeast(0),
            lockedUntilMillis = parts[1].toLong().coerceAtLeast(0),
            lockedAtElapsed = parts.getOrNull(2)?.toLong() ?: -1,
        )
    }.getOrDefault(State(0, 0))

    @Synchronized
    fun lockRemainingMillis(): Long {
        val nowElapsed = elapsed()
        val memory = (memoryLockUntilElapsed - nowElapsed).coerceAtLeast(0)
        return maxOf(memory, storedRemaining(read(), nowElapsed))
    }

    private fun storedRemaining(stored: State, nowElapsed: Long): Long {
        val step = lockFor(stored.failures)
        if (stored.lockedUntilMillis <= 0 || step <= 0) return 0
        if (stored.lockedAtElapsed in 0..nowElapsed) {
            // The same boot: the uptime clock decides.
            return (stored.lockedAtElapsed + step - nowElapsed).coerceIn(0, step)
        }
        val remaining = stored.lockedUntilMillis - now()
        if (remaining > step) {
            // The clock was set back after the lock: start this step again from now, once.
            memoryLockUntilElapsed = maxOf(memoryLockUntilElapsed, nowElapsed + step)
            write(stored.copy(lockedUntilMillis = now() + step, lockedAtElapsed = nowElapsed))
            return step
        }
        return remaining.coerceAtLeast(0)
    }

    @Synchronized
    fun recordFailure(): State {
        val failures = maxOf(read().failures, memoryFailures) + 1
        val lock = lockFor(failures)
        val nowElapsed = elapsed()
        val state = State(
            failures = failures,
            lockedUntilMillis = if (lock > 0) now() + lock else 0,
            lockedAtElapsed = if (lock > 0) nowElapsed else -1,
        )
        memoryFailures = failures
        if (lock > 0) memoryLockUntilElapsed = nowElapsed + lock
        write(state)
        return state
    }

    @Synchronized
    fun reset() {
        memoryFailures = 0
        memoryLockUntilElapsed = 0
        if (file.exists()) write(State(0, 0))
    }

    private fun write(state: State) {
        runCatching {
            file.parentFile?.mkdirs()
            val text = "${state.failures},${state.lockedUntilMillis},${state.lockedAtElapsed}"
            val staging = File(file.parentFile, ".${file.name}.tmp")
            staging.writeText(text)
            if (!staging.renameTo(file)) {
                file.writeText(text)
                staging.delete()
            }
        }
    }

    companion object {
        const val FILE_NAME = "safe-mode-pin-attempts"
        private const val MINUTE = 60_000L
        private val shared = HashMap<String, SafeModePinAttempts>()

        /** One instance per file for the whole process, so the in-memory count is shared. */
        fun shared(file: File): SafeModePinAttempts = synchronized(shared) {
            shared.getOrPut(file.absolutePath) { SafeModePinAttempts(file) }
        }

        fun lockFor(failures: Int): Long = when {
            failures >= 6 -> 30 * MINUTE
            failures == 5 -> 15 * MINUTE
            failures == 4 -> 5 * MINUTE
            failures == 3 -> MINUTE
            else -> 0
        }
    }
}
