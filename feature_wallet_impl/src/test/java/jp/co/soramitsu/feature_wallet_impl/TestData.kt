/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl

import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal

object TestData {

    val GLOBAL_CARD_HUB_LOCAL = listOf(
        GlobalCardHubLocal(
            cardId = CardHubType.ASSETS.hubName,
            visibility = true,
            sortOrder = CardHubType.ASSETS.order,
            collapsed = false
        ),
        GlobalCardHubLocal(
            cardId = CardHubType.POOLS.hubName,
            visibility = true,
            sortOrder = CardHubType.POOLS.order,
            collapsed = false
        )
    )
}
