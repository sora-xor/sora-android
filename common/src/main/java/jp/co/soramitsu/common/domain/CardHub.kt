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

package jp.co.soramitsu.common.domain

import androidx.annotation.StringRes
import jp.co.soramitsu.common.R

const val ASSETS_HUB_NAME = "assets"
const val POOLS_HUB_NAME = "pools"
const val GET_SORA_CARD_HUB_NAME = "get sora card"
const val BUY_XOR_TOKEN_HUB_NAME = "buy xor token"
const val REFERRAL_SYSTEM_HUB_NAME = "referral system"
const val BACKUP_WALLET_HUB_NAME = "backup wallet"

data class CardHub(
    val cardType: CardHubType,
    val visibility: Boolean,
    val sortOrder: Int,
    val collapsed: Boolean,
)

enum class CardHubType(
    val hubName: String,
    val order: Int,
    val boundToAccount: Boolean,
    @StringRes val userName: Int
) {
    GET_SORA_CARD(
        GET_SORA_CARD_HUB_NAME,
        order = 0,
        boundToAccount = false,
        R.string.more_menu_sora_card_title,
    ),
    BUY_XOR_TOKEN(
        BUY_XOR_TOKEN_HUB_NAME,
        order = 1,
        boundToAccount = false,
        R.string.common_buy_xor,
    ),
    REFERRAL_SYSTEM(
        REFERRAL_SYSTEM_HUB_NAME,
        order = 2,
        boundToAccount = false,
        R.string.referral_toolbar_title,
    ),

    BACKUP(BACKUP_WALLET_HUB_NAME, order = -1, boundToAccount = true, R.string.wallet_backup),
    ASSETS(ASSETS_HUB_NAME, order = 0, boundToAccount = true, R.string.liquid_assets),
    POOLS(POOLS_HUB_NAME, order = 1, boundToAccount = true, R.string.pooled_assets),
}
