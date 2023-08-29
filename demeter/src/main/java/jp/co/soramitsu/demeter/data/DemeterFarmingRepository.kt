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
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.demeter.domain.DemeterFarmingPool
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls

interface DemeterFarmingRepository {
    suspend fun getFarmedPools(soraAccountAddress: String): List<DemeterFarmingPool>?
    suspend fun getStakedFarmedAmountOfAsset(address: String, tokenId: String): BigDecimal
}

private class DemeterStorage(
    val base: String,
    val pool: String,
    val reward: String,
    val farm: Boolean,
    val amount: BigInteger,
)

internal class DemeterFarmingRepositoryImpl(
    private val substrateCalls: SubstrateCalls,
    private val runtimeManager: RuntimeManager,
    private val soraConfigManager: SoraConfigManager,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val db: AppDatabase,
) : DemeterFarmingRepository {

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
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return getDemeter(soraAccountAddress)
            ?.filter { it.farm }
            ?.map {
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
                    amount = mapBalance(it.amount, baseTokenMapped.precision),
                )
            }
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
                    val baseToken = instance.get<Struct.Instance>("baseAsset")
                        ?.get<List<*>>("code")?.map {
                            (it as BigInteger).toByte()
                        }?.toByteArray()?.toHexString(true)
                    val poolToken = instance.get<Struct.Instance>("poolAsset")
                        ?.get<List<*>>("code")?.map {
                            (it as BigInteger).toByte()
                        }?.toByteArray()?.toHexString(true)
                    val rewardToken = instance.get<Struct.Instance>("rewardAsset")
                        ?.get<List<*>>("code")?.map {
                            (it as BigInteger).toByte()
                        }?.toByteArray()?.toHexString(true)
                    val isFarm = instance.get<Boolean>("isFarm")
                    val pooled = instance.get<BigInteger>("pooledTokens")
                    if (isFarm != null && baseToken != null && poolToken != null && rewardToken != null && pooled != null) {
                        DemeterStorage(
                            base = baseToken,
                            pool = poolToken,
                            reward = rewardToken,
                            farm = isFarm,
                            amount = pooled,
                        )
                    } else {
                        null
                    }
                }
        }
    }
}
