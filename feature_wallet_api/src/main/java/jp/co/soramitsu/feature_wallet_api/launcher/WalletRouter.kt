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

package jp.co.soramitsu.feature_wallet_api.launcher

import jp.co.soramitsu.common.domain.Token

interface WalletRouter {

    fun showValTransferAmount(recipientId: String, assetId: String, initSendAmount: String? = null)

    fun returnToHubFragment()

    fun popBackStackFragment()

    /**
     * Closes the legacy claim screen, and only that screen: nothing happens while another screen
     * is on top of it (for example the PIN lock shown after the app was in the background).
     */
    fun closeClaimScreen()

    fun showContactsFilled(tokenId: String, address: String)

    fun returnToAddLiquidity(tokenFrom: Token? = null, tokenTo: Token? = null)

    fun openQrCodeFlow(shouldNavigateToScannerDirectly: Boolean = false)

    fun openEditCardsHub()
}
