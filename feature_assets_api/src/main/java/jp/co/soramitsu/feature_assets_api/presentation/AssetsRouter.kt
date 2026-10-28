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

package jp.co.soramitsu.feature_assets_api.presentation

/**
 * The screens that send a transaction and then open its page. [replacedByPage] says whether the
 * page takes the place of the screen (and of the steps that led to it, back to the wallet hub)
 * or is put on top of it.
 */
enum class TransactionScreen(val replacedByPage: Boolean) {
    SEND(true),
    SWAP(true),
    ADD_LIQUIDITY(true),
    REMOVE_LIQUIDITY(true),
    DEMETER_CLAIM(true),
    DEMETER_STAKE(true),
    REFERRAL(false),
}

interface AssetsRouter {

    /** Opens a transaction's page on top of the current screen. For a tap on a list entry. */
    fun showTxDetails(txHash: String)

    /**
     * Opens the page of the transaction that [from] has just sent, but only while [from] is the
     * screen in front. The result of a transaction can arrive long after the tap, when another
     * screen is on top; after a pause that screen is the PIN lock, and it must stay.
     *
     * Returns true when the page is open. Returns false, having changed nothing, when [from] is
     * not in front or the page could not be opened now; the caller keeps the result and asks
     * again when its screen is in front.
     */
    fun showTxResult(txHash: String, from: TransactionScreen): Boolean

    fun showTxList(assetId: String)

    fun showContacts(tokenId: String)

    fun showFullAssetsSettings()

    fun showAssetDetails(assetId: String)

    fun popBackStackFragment()
}
