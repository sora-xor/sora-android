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

package jp.co.soramitsu.common.domain

import androidx.annotation.StringRes
import jp.co.soramitsu.common.R

const val ASSETS_HUB_NAME = "assets"
const val POOLS_HUB_NAME = "pools"
const val REFERRAL_SYSTEM_HUB_NAME = "referral system"
const val BACKUP_WALLET_HUB_NAME = "backup wallet"

val subscriptionKeys = listOf(ASSETS_HUB_NAME, POOLS_HUB_NAME)

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
