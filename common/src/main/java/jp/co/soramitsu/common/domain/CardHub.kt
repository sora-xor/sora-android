/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

const val ASSETS_HUB_NAME = "assets"
const val POOLS_HUB_NAME = "pools"
const val GET_SORA_CARD_HUB_NAME = "get sora card"
const val BUY_XOR_TOKEN_HUB_NAME = "buy xor token"

data class CardHub(
    val cardType: CardHubType,
    val visibility: Boolean,
    val sortOrder: Int,
    val collapsed: Boolean,
)

enum class CardHubType(val hubName: String, val order: Int, val boundToAccount: Boolean) {
    GET_SORA_CARD(GET_SORA_CARD_HUB_NAME, order = 0, boundToAccount = false),
    BUY_XOR_TOKEN(BUY_XOR_TOKEN_HUB_NAME, order = 1, boundToAccount = false),

    ASSETS(ASSETS_HUB_NAME, order = 0, boundToAccount = true),
    POOLS(POOLS_HUB_NAME, order = 1, boundToAccount = true),
}
