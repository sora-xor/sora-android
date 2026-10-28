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
