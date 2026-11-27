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


interface WalletRouter {

    fun showValTransferAmount(recipientId: String, assetId: String, initSendAmount: String? = null)

    fun popBackStackFragment()

    /**
     * Closes the legacy claim screen, and only that screen: nothing happens while another screen
     * is on top of it (for example the PIN lock shown after the app was in the background).
     */
    fun closeClaimScreen()

    fun showAssetSettings()

    fun openQrCodeFlow(shouldNavigateToScannerDirectly: Boolean = false)

    fun openEditCardsHub()

    /** The HASHI bridge screen, with [assetId] chosen, or with the token picker when null. */
    fun openHashiBridge(assetId: String? = null)

    /** The progress page of one bridge transfer. */
    fun openHashiBridgeTransfer(transferId: String)
}
