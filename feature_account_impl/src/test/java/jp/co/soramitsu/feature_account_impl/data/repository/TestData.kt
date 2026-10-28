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

package jp.co.soramitsu.feature_account_impl.data.repository

import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal

object TestData {

    val CARD_HUB_LOCAL = listOf(
        CardHubLocal(
            cardId = CardHubType.BACKUP.hubName,
            accountAddress = "accountAddress",
            visibility = true,
            sortOrder = CardHubType.BACKUP.order,
            collapsed = false
        ),
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
            cardId = CardHubType.REFERRAL_SYSTEM.hubName,
            visibility = true,
            sortOrder = CardHubType.REFERRAL_SYSTEM.order,
            collapsed = false,
        )
    )
}
