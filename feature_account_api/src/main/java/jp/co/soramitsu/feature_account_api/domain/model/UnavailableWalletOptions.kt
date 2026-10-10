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

package jp.co.soramitsu.feature_account_api.domain.model

/**
 * Forward paths for one account that is read-only on this phone (its keys are missing,
 * incomplete or unreadable). The account keeps showing balances; nothing here deletes data.
 */
data class UnavailableWalletOptions(
    val walletId: String,
    /**
     * Importing this account's recovery phrase or raw seed completes its stored keys in place:
     * only missing values are added, and every value already stored must match. The account
     * can sign again afterwards.
     */
    val canRepairWithPhrase: Boolean,
    /**
     * Removing this account (after the person confirms) and importing its phrase again. Used when
     * a stored value cannot be read, so the account cannot be completed in place. A copy of the
     * old preference files is kept on the device first. Offered only when removal loses no
     * readable secret, or when that secret is a phrase or seed shown first
     * ([showPhraseBeforeRemoval]). A release-era copy ([hasReleaseEraCopy]) is never removed.
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
     * keeps them, and "Restore wallets found on this phone" (`adoptRetainedWallet`) makes the
     * account usable again from them without the phrase.
     */
    val hasReleaseEraCopy: Boolean = false,
)
