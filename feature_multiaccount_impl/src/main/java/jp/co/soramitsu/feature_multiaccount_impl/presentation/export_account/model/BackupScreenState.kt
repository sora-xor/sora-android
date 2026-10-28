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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

data class BackupScreenState(
    val mnemonicWords: List<String> = emptyList(),
    val seedString: String = "",
    val isCreatingFlow: Boolean = false,
    val isViaGoogleDrive: Boolean = false,
    /**
     * Nothing stored on this phone for this account re-derives its address, so no phrase or
     * seed is shown; the encrypted JSON export is offered instead.
     */
    val noMatchingSecret: Boolean = false,
    /**
     * With [noMatchingSecret]: the account's stored key signs, so the encrypted JSON export
     * works and is offered. A read-only account is pointed to importing its phrase instead.
     */
    val canExportJson: Boolean = false,
)
