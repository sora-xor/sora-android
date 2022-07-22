/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_api.data

import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xnetworking.subquery.ReferrerReward
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface ReferralRepository {

    suspend fun updateReferralRewards(address: String)

    fun getReferralRewards(): Flow<List<ReferrerReward>>

    suspend fun getSetReferrerFee(from: String, feeToken: Token): BigDecimal

    fun observeMyReferrer(from: String): Flow<String>

    fun observerReferrals(from: String): Flow<String>

    fun observeReferrerBalance(from: String, feeToken: Token): Flow<BigDecimal?>

    suspend fun observeSetReferrer(
        keypair: Sr25519Keypair,
        from: String,
        referrer: String
    ): Boolean

    suspend fun observeUnbond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
    ): Boolean

    suspend fun observeBond(
        keypair: Sr25519Keypair,
        from: String,
        amount: BigDecimal,
        token: Token,
    ): Boolean

    suspend fun calcBondFee(
        from: String,
        token: Token,
    ): BigDecimal
}
