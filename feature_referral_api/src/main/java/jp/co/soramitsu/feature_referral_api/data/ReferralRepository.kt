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

package jp.co.soramitsu.feature_referral_api.data

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.flow.Flow

data class ReferralReward(
    val referral: String,
    val amount: String,
)

interface ReferralRepository {

    suspend fun updateReferralRewards(address: String)

    fun getReferralRewards(): Flow<List<ReferralReward>>

    suspend fun getSetReferrerFee(from: String, feeToken: Token): BigDecimal?

    fun observeMyReferrer(from: String): Flow<String>

    fun observerReferrals(from: String): Flow<String>

    fun observeReferrerBalance(from: String, feeToken: Token): Flow<BigDecimal?>

    suspend fun observeSetReferrer(
        keypair: Sr25519Keypair,
        from: String,
        referrer: String,
        feeToken: Token,
        activity: Sora2SubmissionActivity? = null,
    ): ExtrinsicSubmitStatus

    suspend fun observeUnbond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
        fee: BigDecimal,
        activity: Sora2SubmissionActivity? = null,
    ): ExtrinsicSubmitStatus

    suspend fun observeBond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
        fee: BigDecimal,
        activity: Sora2SubmissionActivity? = null,
    ): ExtrinsicSubmitStatus

    suspend fun calcBondFee(
        from: String,
        token: Token,
    ): BigDecimal?
}
