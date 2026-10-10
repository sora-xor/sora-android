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

import java.io.File
import java.security.MessageDigest
import jp.co.soramitsu.common.account.SoraAccount
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
    data class Phrase(val words: List<String>) : RevealedSecret
    data class Seed(val value: String) : RevealedSecret

    /** Nothing is saved for this wallet on this phone. */
    data object None : RevealedSecret

    /** The value exists but could not be decrypted right now (busy or missing Keystore key). */
    data object Unavailable : RevealedSecret
}

/**
 * Room-free, read-only access to the recovery phrases kept on this phone, for Safe Mode.
 *
 * - The list comes from [RecoveryFacts] (preference key names and account names gathered while
 *   the app started); nothing is decrypted to build it.
 * - Revealing reads one value through the same decryption the app uses everywhere. Nothing is
 *   written, created or cached: no `put`, no derived-seed cache, no Room.
 */
object SafeModeVault {

    const val OLDER_VERSION_ID = "release-era-phrase"

    fun entries(facts: RecoveryFacts): List<PhraseEntry> = buildList {
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

    suspend fun reveal(
        entry: PhraseEntry,
        legacyOwner: String?,
        credentialsRepository: CredentialsRepository,
        credentialsDatasource: CredentialsDatasource,
    ): RevealedSecret = try {
        val address = entry.address
        if (address == null) {
            releaseEra(credentialsDatasource)
        } else {
            val account = SoraAccount(address, entry.name.orEmpty())
            val phrase = credentialsRepository.retrieveMnemonic(account).trim()
            val seed = if (phrase.isEmpty()) credentialsRepository.retrieveStoredSeed(account).trim() else ""
            when {
                phrase.isNotEmpty() -> RevealedSecret.Phrase(phrase.split(Regex("\\s+")))
                seed.isNotEmpty() -> RevealedSecret.Seed(seed)
                // The 2.x owner's unsuffixed value, even when its keys no longer derive this
                // address: a decryptable phrase is never hidden.
                address == legacyOwner -> releaseEra(credentialsDatasource)
                else -> RevealedSecret.None
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        RevealedSecret.Unavailable
    }

    private suspend fun releaseEra(datasource: CredentialsDatasource): RevealedSecret {
        val phrase = datasource.retrieveMnemonic("").trim()
        if (phrase.isNotEmpty()) return RevealedSecret.Phrase(phrase.split(Regex("\\s+")))
        val seed = datasource.retrieveSeed("").trim()
        return if (seed.isNotEmpty()) RevealedSecret.Seed(seed) else RevealedSecret.None
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
 */
class SafeModePinAttempts(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class State(val failures: Int, val lockedUntilMillis: Long)

    fun read(): State = runCatching {
        val parts = file.readText().trim().split(',')
        State(parts[0].toInt().coerceAtLeast(0), parts[1].toLong().coerceAtLeast(0))
    }.getOrDefault(State(0, 0))

    fun lockRemainingMillis(): Long = (read().lockedUntilMillis - now()).coerceAtLeast(0)

    fun recordFailure(): State {
        val failures = read().failures + 1
        val lock = lockFor(failures)
        val state = State(failures, if (lock > 0) now() + lock else 0)
        write(state)
        return state
    }

    fun reset() {
        if (file.exists()) write(State(0, 0))
    }

    private fun write(state: State) {
        runCatching {
            file.parentFile?.mkdirs()
            val staging = File(file.parentFile, ".${file.name}.tmp")
            staging.writeText("${state.failures},${state.lockedUntilMillis}")
            if (!staging.renameTo(file)) {
                file.writeText("${state.failures},${state.lockedUntilMillis}")
                staging.delete()
            }
        }
    }

    companion object {
        const val FILE_NAME = "safe-mode-pin-attempts"
        private const val MINUTE = 60_000L

        fun lockFor(failures: Int): Long = when {
            failures >= 6 -> 30 * MINUTE
            failures == 5 -> 15 * MINUTE
            failures == 4 -> 5 * MINUTE
            failures == 3 -> MINUTE
            else -> 0
        }
    }
}
