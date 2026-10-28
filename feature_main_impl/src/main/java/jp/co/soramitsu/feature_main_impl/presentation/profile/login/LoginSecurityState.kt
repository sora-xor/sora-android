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

package jp.co.soramitsu.feature_main_impl.presentation.profile.login

data class LoginSecurityState(
    val bioAvailable: Boolean,
    val bioEnabled: Boolean,
    /** Complete key sets on this phone that no account uses ("Wallets found on this phone"). */
    val retainedWallets: List<String> = emptyList(),
    val restoring: String? = null,
    val restored: List<String> = emptyList(),
    /** Addresses whose keys could not be proven to match; the phrase import is the way back. */
    val notRestorable: List<String> = emptyList(),
    /**
     * Key sets that were on the phone when every account was removed. The PIN that protected
     * them is gone, so they are not restored from here: importing their own recovery phrase,
     * raw seed or backup file brings them back (the stored keys are adopted unchanged).
     */
    val needRecoveryPhrase: List<String> = emptyList(),
)
