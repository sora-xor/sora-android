/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal

object CardsHubMapper {
    fun map(card: CardHubLocal): CardHub? {
        val type = CardHubType.values().find { it.hubName == card.cardId } ?: return null
        return CardHub(
            cardType = type,
            visibility = card.visibility,
            sortOrder = card.sortOrder,
            collapsed = card.collapsed,
        )
    }

    fun map(card: GlobalCardHubLocal): CardHub? {
        val type = CardHubType.values().find { it.hubName == card.cardId } ?: return null
        return CardHub(
            cardType = type,
            visibility = card.visibility,
            sortOrder = card.sortOrder,
            collapsed = card.collapsed,
        )
    }
}
