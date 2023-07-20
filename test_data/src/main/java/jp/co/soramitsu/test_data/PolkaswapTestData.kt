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

package jp.co.soramitsu.test_data

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.UserPoolData

object PolkaswapTestData {

    val XOR_ASSET = TestAssets.xorAsset(BigDecimal.ONE)

    val XOR_ASSET_ZERO_BALANCE = TestAssets.xorAsset(BigDecimal.ZERO)

    val VAL_ASSET = TestAssets.valAsset(BigDecimal.ONE)

    val XSTXAU_ASSET = TestAssets.xstxauAsset(BigDecimal.ONE)

    val LIQUIDITY_DATA = LiquidityData(
        firstReserves = BigDecimal.ONE,
        secondReserves = BigDecimal.ONE,
        secondPooled = BigDecimal.ONE
    )

    val POOL_DATA = UserPoolData(
        BasicPoolData(
            XOR_ASSET.token,
            VAL_ASSET.token,
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.TEN,
            "",
        ),
        BigDecimal.ONE,
        BigDecimal.ONE,
        1.0,
        10.0,
        BigDecimal.TEN,
        true,
        2,
    )

    val NETWORK_FEE = BigDecimal(0.007)
    const val SLIPPAGE_TOLERANCE = 0.5f
    private val SHARE_OF_POOL = BigDecimal("0.34678")
    const val STRATEGIC_BONUS_APY = 0.234

    val LIQUIDITY_DETAILS = LiquidityDetails(
        baseAmount = BigDecimal.ONE,
        targetAmount = BigDecimal.ONE,
        perFirst = BigDecimal.ONE,
        perSecond = BigDecimal.ONE,
        networkFee = BigDecimal.ZERO,
        shareOfPool = SHARE_OF_POOL,
        pairEnabled = true,
        pairPresented = true
    )
}
