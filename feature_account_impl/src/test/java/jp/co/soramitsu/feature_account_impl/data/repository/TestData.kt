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
            cardId = CardHubType.GET_SORA_CARD.hubName,
            visibility = true,
            sortOrder = CardHubType.GET_SORA_CARD.order,
            collapsed = false,
        ),
        GlobalCardHubLocal(
            cardId = CardHubType.BUY_XOR_TOKEN.hubName,
            visibility = true,
            sortOrder = CardHubType.BUY_XOR_TOKEN.order,
            collapsed = false,
        ),
        GlobalCardHubLocal(
            cardId = CardHubType.REFERRAL_SYSTEM.hubName,
            visibility = true,
            sortOrder = CardHubType.REFERRAL_SYSTEM.order,
            collapsed = false,
        )
    )
}
