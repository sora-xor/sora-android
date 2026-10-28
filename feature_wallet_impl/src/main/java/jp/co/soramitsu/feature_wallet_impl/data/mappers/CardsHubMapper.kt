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

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal

object CardsHubMapper {
    fun map(card: CardHubLocal): CardHub? {
        val type = CardHubType.values()
            .filter { it.boundToAccount }
            .find { it.hubName == card.cardId } ?: return null
        return CardHub(
            cardType = type,
            visibility = card.visibility,
            sortOrder = card.sortOrder,
            collapsed = card.collapsed,
        )
    }

    fun map(card: GlobalCardHubLocal): CardHub? {
        val type = CardHubType.values()
            .filter { it.boundToAccount.not() }
            .find { it.hubName == card.cardId } ?: return null
        return CardHub(
            cardType = type,
            visibility = card.visibility,
            sortOrder = card.sortOrder,
            collapsed = card.collapsed,
        )
    }
}
