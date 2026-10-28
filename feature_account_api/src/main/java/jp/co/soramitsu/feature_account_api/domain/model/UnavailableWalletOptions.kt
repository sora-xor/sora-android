/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_account_api.domain.model

/**
 * Forward paths for one account that is read-only on this phone (its keys are missing,
 * incomplete or unreadable). The account keeps showing balances; nothing here deletes data.
 */
data class UnavailableWalletOptions(
    val walletId: String,
    /**
     * Importing this account's recovery phrase or raw seed repairs its stored keys in place:
     * missing values are added. When a stored value differs or cannot be read, the import
     * offers a confirmed repair that keeps a copy of the old values on the device and writes the
     * proven ones. The account can sign again afterwards; it never has to be removed first.
     */
    val canRepairWithPhrase: Boolean,
    /**
     * The person may still choose to remove this account (confirmed). It is never the repair:
     * [canRepairWithPhrase] keeps the account. A copy of the old preference files is kept on the
     * device first. Offered only when removal loses no readable secret, or when that secret is a
     * phrase or seed shown first ([showPhraseBeforeRemoval]). A release-era copy
     * ([hasReleaseEraCopy]) is never removed.
     */
    val canRemoveAndImport: Boolean,
    /**
     * A stored recovery phrase or seed for this account decrypts and can be shown after PIN
     * (the account's own value, or a release-era value whose keys derive this address).
     */
    val canShowPhraseAfterPin: Boolean,
    /** Balances and history stay visible; only signing is off for this account. */
    val canReadOnly: Boolean = true,
    /**
     * Removal would delete a readable phrase or seed of this account: show it (after PIN) and
     * ask the person to confirm they have written it down before removing the account.
     */
    val showPhraseBeforeRemoval: Boolean = false,
    /**
     * Release-era keys that derive this address are stored on the phone. Removing the account
     * keeps them. While another account stays on the phone (and with it the PIN), "Wallets
     * found on this phone" (`adoptRetainedWallet`) can make the account usable again from them
     * without the phrase. When the removed account is the only one, the PIN goes with it and
     * that copy is marked: afterwards only the wallet's own recovery phrase, raw seed or backup
     * file brings it back (`WalletPreferenceKeys.RETAINED_NEEDS_PHRASE`).
     */
    val hasReleaseEraCopy: Boolean = false,
    /**
     * A stored value of this account cannot be read, so the repair with its phrase will replace
     * stored values (after confirmation, keeping a copy) instead of only adding missing ones.
     */
    val repairReplacesStoredValues: Boolean = false,
    /** Why the account cannot sign, as far as the stored values show. */
    val reason: UnavailableWalletReason = UnavailableWalletReason.KEY_NOT_USABLE,
)

/**
 * Why an account is read-only on this phone. Only what the stored values of its signing key
 * show; a stored recovery phrase or seed never decides this.
 */
enum class UnavailableWalletReason {
    /** No signing key is stored for this account on this phone. */
    KEY_MISSING,

    /** Part of the signing key (private key, public key or nonce) is stored, part is missing. */
    KEY_INCOMPLETE,

    /** A value of the signing key is stored but cannot be read on this phone. */
    KEY_UNREADABLE,

    /**
     * A complete signing key is stored and readable, but it does not prove this account's
     * address (it is another address's key, or it fails the sign-and-verify proof).
     */
    KEY_NOT_USABLE,

    /** The account is marked watch-only: it never had a key on this phone. */
    WATCH_ONLY,

    /**
     * A complete, readable signing key is stored, and nothing has judged it yet: the account's
     * identity was never recorded as unavailable (for example the row an upgrade created before
     * the first check finished). Nothing is claimed about the key. The check at the next start,
     * or restoring the keys, settles it.
     */
    NOT_CHECKED,
}

/**
 * A complete key set stored on this phone that no account uses (for example after "Set up this
 * device again", or left by "Remove all accounts").
 */
data class RetainedWallet(
    val address: String,
    /**
     * Only this wallet's own recovery phrase, raw seed or backup file brings it back: it was on
     * the phone when every account was removed, and the PIN that protected it was removed with
     * them. Importing adopts the stored keys unchanged. `adoptRetainedWallet` refuses it.
     */
    val needsRecoveryPhrase: Boolean,
)
