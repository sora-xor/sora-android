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

package jp.co.soramitsu.feature_main_impl.presentation.pincode

internal data class PinCodeScreenState(
    val checkedDotsCount: Int = 0,
    val maxDotsCount: Int = 6,
    val isBackButtonVisible: Boolean = false,
    val isBiometricButtonVisible: Boolean = false,
    val toolbarTitleString: String,
    val enableShakeAnimation: Boolean = false,
    val isLengthInfoAlertVisible: Boolean = false,
    val migrating: Boolean = false,
    /** The running account check needs the network (an unchecked 15-word legacy phrase). */
    val checkNeedsNetwork: Boolean = false,
    val isConnected: Boolean = true,
)

/**
 * "Remove this account from this phone", shown after the PIN or biometrics and before anything
 * is removed. The backup that re-derives each account is shown first; the account is removed
 * only after the person acknowledged it and confirmed.
 */
internal data class AccountRemovalState(
    val accounts: List<Account>,
    /** Every account is removed: the PIN and custom nodes go too, and the app starts again. */
    val removesEverything: Boolean,
    /** Wallet key sets no account uses that stay on the phone (only when everything goes). */
    val retainedKeySets: Int = 0,
    /** The person tapped the veil: the phrase or seed is drawn. */
    val revealed: Boolean = false,
    /** "I have written it down", for the accounts whose backup is shown. */
    val writtenDown: Boolean = false,
    /** "I understand that this phone can't bring it back", for accounts without a backup. */
    val understoodNoBackup: Boolean = false,
    val working: Boolean = false,
) {
    data class Account(
        val address: String,
        val name: String,
        val phraseWords: List<String> = emptyList(),
        val seed: String = "",
        val canExportJson: Boolean = false,
    ) {
        val hasBackupToShow: Boolean get() = phraseWords.isNotEmpty() || seed.isNotEmpty()
    }

    /** At least one account has a matching phrase or seed, which is shown first. */
    val showsBackup: Boolean get() = accounts.any { it.hasBackupToShow }

    /** At least one account has nothing on this phone that re-derives it. */
    val hasAccountWithoutBackup: Boolean get() = accounts.any { !it.hasBackupToShow }

    /** The acknowledgements this removal needs are all given. */
    val acknowledged: Boolean
        get() = (!showsBackup || (revealed && writtenDown)) &&
            (!hasAccountWithoutBackup || understoodNoBackup)

    val canRemove: Boolean get() = acknowledged && !working
}
