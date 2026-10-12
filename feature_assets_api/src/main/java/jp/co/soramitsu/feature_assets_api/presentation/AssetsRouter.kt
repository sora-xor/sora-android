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
