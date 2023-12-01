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

package jp.co.soramitsu.demeter.data

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.demeter.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.demeter.domain.DemeterFarmingPool
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.assetIdFromKey
import jp.co.soramitsu.sora.substrate.runtime.mapToToken
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId

interface DemeterFarmingRepository {
    suspend fun getFarmedPools(soraAccountAddress: String): List<DemeterFarmingPool>?
    suspend fun getFarmedBasicPools(): List<DemeterFarmingBasicPool>
    suspend fun getStakedFarmedAmountOfAsset(address: String, tokenId: String): BigDecimal
}

private class DemeterStorage(
    val base: String,
    val pool: String,
    val reward: String,
    val farm: Boolean,
    val amount: BigInteger,
    val rewardAmount: BigInteger,
)

private class DemeterBasicStorage(
    val base: String,
    val pool: String,
    val reward: String,
    val multiplier: BigInteger,
    val isCore: Boolean,
    val isFarm: Boolean,
    val isRemoved: Boolean,
    val depositFee: BigInteger,
    val totalTokensInPool: BigInteger,
    val rewards: BigInteger,
    val rewardsToBeDistributed: BigInteger,
)

private class DemeterRewardTokenStorage(
    val token: String,
    val account: String,
    val farmsTotalMultiplier: BigInteger,
    val stakingTotalMultiplier: BigInteger,
    val tokenPerBlock: BigInteger,
    val farmsAllocation: BigInteger,
    val stakingAllocation: BigInteger,
    val teamAllocation: BigInteger,
)

internal class DemeterFarmingRepositoryImpl(
    private val substrateCalls: SubstrateCalls,
    private val runtimeManager: RuntimeManager,
    private val soraConfigManager: SoraConfigManager,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val db: AppDatabase,
    private val assetsRepository: AssetsRepository,
    private val polkaswapRepository: PolkaswapRepository,
) : DemeterFarmingRepository {

    companion object {
        private const val BLOCKS_PER_YEAR = 5256000
    }

    private var cachedFarmedPools: MutableMap<String, List<DemeterFarmingPool>> = mutableMapOf()
    private var cachedFarmedBasicPools: List<DemeterFarmingBasicPool>? = null

    override suspend fun getStakedFarmedAmountOfAsset(
        address: String,
        tokenId: String
    ): BigDecimal {
        val token = db.assetDao().getTokenLocal(tokenId)
        val amount = getDemeter(address)
            ?.filter {
                it.farm.not() && it.base == tokenId && it.pool == tokenId
            }
            ?.sumOf { it.amount }
        return amount?.let { mapBalance(it, token.precision) } ?: BigDecimal.ZERO
    }

    override suspend fun getFarmedPools(soraAccountAddress: String): List<DemeterFarmingPool>? {
        if (cachedFarmedPools.containsKey(soraAccountAddress)) return cachedFarmedPools[soraAccountAddress]
        val baseFarms = getFarmedBasicPools()
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        val calculated = getDemeter(soraAccountAddress)
            ?.filter { it.farm && it.amount.isZero().not() }
            ?.map {
                val base = baseFarms.first { base ->
                    StringTriple(
                        base.tokenBase.id,
                        base.tokenTarget.id,
                        base.tokenReward.id
                    ) == StringTriple(it.base, it.pool, it.reward)
                }
                val baseTokenMapped = assetLocalToAssetMapper.map(
                    tokenLocal = db.assetDao().getToken(it.base, selectedCurrency.code)
                )
                val poolTokenMapped = assetLocalToAssetMapper.map(
                    tokenLocal = db.assetDao().getToken(it.pool, selectedCurrency.code)
                )
                val rewardTokenMapped = assetLocalToAssetMapper.map(
                    tokenLocal = db.assetDao().getToken(it.reward, selectedCurrency.code)
                )
                DemeterFarmingPool(
                    tokenBase = baseTokenMapped,
                    tokenTarget = poolTokenMapped,
                    tokenReward = rewardTokenMapped,
                    apr = base.apr,
                    amount = mapBalance(it.amount, baseTokenMapped.precision),
                    amountReward = mapBalance(it.rewardAmount, rewardTokenMapped.precision),
                )
            } ?: return null
        return cachedFarmedPools.getOrPut(soraAccountAddress) { calculated }
    }

    override suspend fun getFarmedBasicPools(): List<DemeterFarmingBasicPool> {
        if (cachedFarmedBasicPools == null) {
            val selectedCurrency = soraConfigManager.getSelectedCurrency()
            val rewardTokens = getRewardTokens()
            cachedFarmedBasicPools = getAllFarms()
                .mapNotNull { basic ->
                    runCatching {
                        val baseTokenMapped = assetLocalToAssetMapper.map(
                            tokenLocal = db.assetDao().getToken(basic.base, selectedCurrency.code)
                        )
                        val poolTokenMapped = assetLocalToAssetMapper.map(
                            tokenLocal = db.assetDao().getToken(basic.pool, selectedCurrency.code)
                        )
                        val rewardTokenMapped = assetLocalToAssetMapper.map(
                            tokenLocal = db.assetDao().getToken(basic.reward, selectedCurrency.code)
                        )
                        val rewardToken = rewardTokens.find { it.token == basic.reward }
                        val emission = getEmission(basic, rewardToken, rewardTokenMapped.precision)
                        val total = mapBalance(basic.totalTokensInPool, poolTokenMapped.precision)
                        val poolTokenPrice = BigDecimal(poolTokenMapped.fiatPrice ?: 0.0)
                        val rewardTokenPrice = BigDecimal(rewardTokenMapped.fiatPrice ?: 0.0)
                        val tvl = if (basic.isFarm) {
                            polkaswapRepository.getBasicPool(basic.base, basic.pool)?.let { pool ->
                                val kf = pool.targetReserves.div(pool.totalIssuance)
                                kf.times(total).times(2.toBigDecimal()).times(poolTokenPrice)
                            } ?: BigDecimal.ZERO
                        } else {
                            total.times(poolTokenPrice)
                        }
                        val apr = if (tvl.isZero()) BigDecimal.ZERO else emission
                            .times(BLOCKS_PER_YEAR.toBigDecimal())
                            .times(rewardTokenPrice)
                            .div(tvl).times(100.toBigDecimal())

                        DemeterFarmingBasicPool(
                            tokenBase = baseTokenMapped,
                            tokenTarget = poolTokenMapped,
                            tokenReward = rewardTokenMapped,
                            apr = apr.toDouble(),
                            tvl = tvl,
                            fee = mapBalance(basic.depositFee, baseTokenMapped.precision).toDouble()
                                .times(100.0),
                        )
                    }.getOrNull()
                }
        }

        return cachedFarmedBasicPools ?: emptyList()
    }

    private fun getEmission(
        basic: DemeterBasicStorage,
        reward: DemeterRewardTokenStorage?,
        precision: Int
    ): BigDecimal {
        val tokenMultiplier =
            ((if (basic.isFarm) reward?.farmsTotalMultiplier else reward?.stakingTotalMultiplier))?.toBigDecimal(
                precision
            ) ?: BigDecimal.ZERO
        if (tokenMultiplier.isZero()) return BigDecimal.ZERO
        val multiplier = basic.multiplier.toBigDecimal(precision).div(tokenMultiplier)
        val allocation =
            mapBalance(
                (if (basic.isFarm) reward?.farmsAllocation else reward?.stakingAllocation)
                    ?: BigInteger.ZERO,
                precision
            )
        val tokenPerBlock = reward?.tokenPerBlock?.toBigDecimal(precision) ?: BigDecimal.ZERO
        return allocation.times(tokenPerBlock).times(multiplier)
    }

    private suspend fun getAllFarms(): List<DemeterBasicStorage> {
        val storage =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.DEMETER_FARMING.palletName)
                .storage(Storage.POOLS.storageName)
        val type = storage.type.value ?: return emptyList()
        val storageKey = storage.storageKey(
            runtimeManager.getRuntimeSnapshot(),
        )
        val farms = substrateCalls.getBulk(storageKey).mapNotNull { hex ->
            hex.value?.let { hexValue ->
                val decoded = type.fromHex(runtimeManager.getRuntimeSnapshot(), hexValue)
                decoded?.safeCast<List<*>>()
                    ?.filterIsInstance<Struct.Instance>()
                    ?.mapNotNull { struct ->
                        runCatching {
                            DemeterBasicStorage(
                                base = struct.mapToToken("baseAsset")!!,
                                pool = hex.key.assetIdFromKey(1),
                                reward = hex.key.assetIdFromKey(),
                                multiplier = struct.get<BigInteger>("multiplier")!!,
                                isCore = struct.get<Boolean>("isCore")!!,
                                isFarm = struct.get<Boolean>("isFarm")!!,
                                isRemoved = struct.get<Boolean>("isRemoved")!!,
                                depositFee = struct.get<BigInteger>("depositFee")!!,
                                totalTokensInPool = struct.get<BigInteger>("totalTokensInPool")!!,
                                rewards = struct.get<BigInteger>("rewards")!!,
                                rewardsToBeDistributed = struct.get<BigInteger>("rewardsToBeDistributed")!!,
                            )
                        }.getOrNull()
                    }
            }
        }.flatten().filter {
            it.isFarm &&
                it.isRemoved.not() &&
                assetsRepository.isWhitelistedToken(it.base) &&
                assetsRepository.isWhitelistedToken(it.pool) &&
                assetsRepository.isWhitelistedToken(it.reward)
        }
        return farms
    }

    private suspend fun getDemeter(address: String): List<DemeterStorage>? {
        val storage =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.DEMETER_FARMING.palletName)
                .storage(Storage.USER_INFOS.storageName)
        val storageKey = storage.storageKey(
            runtimeManager.getRuntimeSnapshot(),
            address.toAccountId(),
        )
        return substrateCalls.getStorageHex(storageKey)?.let { hex ->
            storage.type.value
                ?.fromHex(runtimeManager.getRuntimeSnapshot(), hex)
                ?.safeCast<List<*>>()
                ?.filterIsInstance<Struct.Instance>()
                ?.mapNotNull { instance ->
                    val baseToken = instance.mapToToken("baseAsset")
                    val poolToken = instance.mapToToken("poolAsset")
                    val rewardToken = instance.mapToToken("rewardAsset")
                    val isFarm = instance.get<Boolean>("isFarm")
                    val pooled = instance.get<BigInteger>("pooledTokens")
                    val rewards = instance.get<BigInteger>("rewards")
                    if (isFarm != null && baseToken != null && poolToken != null &&
                        rewardToken != null && pooled != null && rewards != null &&
                        assetsRepository.isWhitelistedToken(baseToken) &&
                        assetsRepository.isWhitelistedToken(poolToken) &&
                        assetsRepository.isWhitelistedToken(rewardToken)
                    ) {
                        DemeterStorage(
                            base = baseToken,
                            pool = poolToken,
                            reward = rewardToken,
                            farm = isFarm,
                            amount = pooled,
                            rewardAmount = rewards,
                        )
                    } else {
                        null
                    }
                }
        }
    }

    private suspend fun getRewardTokens(): List<DemeterRewardTokenStorage> {
        val storage =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.DEMETER_FARMING.palletName)
                .storage(Storage.TOKEN_INFOS.storageName)
        val type = storage.type.value ?: return emptyList()
        val storageKey = storage.storageKey(
            runtimeManager.getRuntimeSnapshot(),
        )
        return substrateCalls.getBulk(storageKey).mapNotNull { hex ->
            hex.value?.let { hexValue ->
                runCatching {
                    type.fromHex(runtimeManager.getRuntimeSnapshot(), hexValue)
                        ?.safeCast<Struct.Instance>()?.let { decoded ->
                            DemeterRewardTokenStorage(
                                token = hex.key.assetIdFromKey(),
                                account = runtimeManager.toSoraAddressOrNull(decoded["teamAccount"])
                                    .orEmpty(),
                                farmsTotalMultiplier = decoded.get<BigInteger>("farmsTotalMultiplier")!!,
                                stakingTotalMultiplier = decoded.get<BigInteger>("stakingTotalMultiplier")!!,
                                tokenPerBlock = decoded.get<BigInteger>("tokenPerBlock")!!,
                                farmsAllocation = decoded.get<BigInteger>("farmsAllocation")!!,
                                stakingAllocation = decoded.get<BigInteger>("stakingAllocation")!!,
                                teamAllocation = decoded.get<BigInteger>("teamAllocation")!!,
                            )
                        }
                }.getOrNull()
            }
        }
    }
}
