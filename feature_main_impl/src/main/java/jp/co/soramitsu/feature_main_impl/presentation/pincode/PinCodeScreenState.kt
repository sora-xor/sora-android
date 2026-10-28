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

package jp.co.soramitsu.feature_main_impl.presentation.pincode

data class PinCodeScreenState(
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
    /**
     * How many of [retainedKeySets] the PIN can still restore now. This removal takes the PIN
     * away and leaves them restorable by their own recovery phrase only, so it needs its own
     * acknowledgement ([understoodFoundWallets]).
     */
    val restorableKeySets: Int = 0,
    /** The phrase or seed is drawn right now. Veiled again whenever the screen is left. */
    val revealed: Boolean = false,
    /** The phrase or seed was drawn at least once, so it can have been written down. */
    val shownOnce: Boolean = false,
    /** "I have written it down", for the accounts whose backup is shown. */
    val writtenDown: Boolean = false,
    /** "I have the recovery phrases of those wallets, or I don't need them". */
    val understoodFoundWallets: Boolean = false,
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

    /** Found wallets the PIN can restore now would need their own phrase after this removal. */
    val strandsFoundWallets: Boolean get() = removesEverything && restorableKeySets > 0

    /** The acknowledgements this removal needs are all given. */
    val acknowledged: Boolean
        get() = (!showsBackup || (shownOnce && writtenDown)) &&
            (!hasAccountWithoutBackup || understoodNoBackup) &&
            (!strandsFoundWallets || understoodFoundWallets)

    val canRemove: Boolean get() = acknowledged && !working
}
