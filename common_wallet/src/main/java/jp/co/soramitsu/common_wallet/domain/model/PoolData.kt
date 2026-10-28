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

package jp.co.soramitsu.common_wallet.domain.model

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token

enum class WithDesired(val backString: String) {
    INPUT("WithDesiredInput"),
    OUTPUT("WithDesiredOutput")
}

data class BasicPoolData(
    val baseToken: Token,
    val targetToken: Token,
    val baseReserves: BigDecimal,
    val targetReserves: BigDecimal,
    val totalIssuance: BigDecimal,
    val reserveAccount: String,
    val sbapy: Double?,
) {
    val fiatSymbol = baseToken.fiatSymbol
    val tvl: BigDecimal?
        get() = baseToken.fiatPrice?.times(2)?.toBigDecimal()?.multiply(baseReserves)
}

val List<BasicPoolData>.fiatSymbol: String
    get() {
        return getOrNull(0)?.fiatSymbol ?: OptionsProvider.fiatSymbol
    }
