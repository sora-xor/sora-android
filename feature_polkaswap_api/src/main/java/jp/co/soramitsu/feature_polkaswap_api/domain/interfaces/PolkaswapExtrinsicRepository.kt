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

package jp.co.soramitsu.feature_polkaswap_api.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TokenPrecision
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

interface PolkaswapExtrinsicRepository {

    suspend fun calcSwapNetworkFee(
        tokenId1: Token,
        address: String,
    ): BigDecimal?

    suspend fun observeRemoveLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        activity: Sora2SubmissionActivity? = null,
    ): ExtrinsicSubmitStatus

    suspend fun observeSwap(
        tokenId1: Token,
        tokenId2: Token,
        keypair: Sr25519Keypair,
        address: String,
        markets: List<Market>,
        swapVariant: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
        dexId: Int,
        activity: Sora2SubmissionActivity? = null,
    ): ExtrinsicSubmitStatus

    suspend fun observeAddLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double,
        activity: Sora2SubmissionActivity? = null,
    ): ExtrinsicSubmitStatus

    suspend fun calcAddLiquidityNetworkFee(
        address: String,
        tokenFrom: TokenPrecision,
        tokenTo: TokenPrecision,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal?

    suspend fun calcRemoveLiquidityNetworkFee(
        tokenId1: TokenPrecision,
        tokenId2: TokenPrecision,
        address: String,
    ): BigDecimal?
}
