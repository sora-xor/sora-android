/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal

object TestData {

    val CARD_HUB_LOCAL = listOf(
        CardHubLocal(
            cardId = CardHubType.ASSETS.hubName,
            accountAddress = "accountAddress",
            visibility = true,
            sortOrder = CardHubType.ASSETS.order,
            collapsed = false
        ),
        CardHubLocal(
            cardId = CardHubType.POOLS.hubName,
            accountAddress = "accountAddress",
            visibility = true,
            sortOrder = CardHubType.POOLS.order,
            collapsed = false
        )
    )

    val DEFAULT_GLOBAL_CARDS = listOf(
        GlobalCardHubLocal(
            cardId = CardHubType.GET_SORA_CARD.hubName,
            visibility = true,
            sortOrder = CardHubType.GET_SORA_CARD.order,
            collapsed = false
        ),
        GlobalCardHubLocal(
            cardId = CardHubType.BUY_XOR_TOKEN.hubName,
            visibility = true,
            sortOrder = CardHubType.BUY_XOR_TOKEN.order,
            collapsed = false
        )
    )
}
