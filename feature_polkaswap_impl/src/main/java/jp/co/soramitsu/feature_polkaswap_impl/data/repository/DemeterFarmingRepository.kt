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

package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_polkaswap_impl.domain.DemeterFarmingPool
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

internal interface DemeterFarmingRepository {
    suspend fun getFarmedPools(soraAccountAddress: String): List<DemeterFarmingPool>?
}

internal class DemeterFarmingRepositoryImpl(
    private val substrateCalls: SubstrateCalls,
    private val runtimeManager: RuntimeManager,
    private val soraConfigManager: SoraConfigManager,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val db: AppDatabase,
) : DemeterFarmingRepository {

    override suspend fun getFarmedPools(soraAccountAddress: String): List<DemeterFarmingPool>? {
        val storage =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.DEMETER_FARMING.palletName)
                .storage(Storage.USER_INFOS.storageName)
        val storageKey = storage.storageKey(
            runtimeManager.getRuntimeSnapshot(),
            soraAccountAddress.toAccountId(),
        )
        return substrateCalls.getStorageHex(storageKey)?.let { hex ->
            val selectedCurrency = soraConfigManager.getSelectedCurrency()
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
                    if (isFarm == true && baseToken != null && poolToken != null && rewardToken != null && pooled != null) {
                        val baseTokenMapped = assetLocalToAssetMapper.map(
                            tokenLocal = db.assetDao().getToken(baseToken, selectedCurrency.code)
                        )
                        val poolTokenMapped = assetLocalToAssetMapper.map(
                            tokenLocal = db.assetDao().getToken(poolToken, selectedCurrency.code)
                        )
                        val rewardTokenMapped = assetLocalToAssetMapper.map(
                            tokenLocal = db.assetDao().getToken(rewardToken, selectedCurrency.code)
                        )
                        DemeterFarmingPool(
                            tokenBase = baseTokenMapped,
                            tokenTarget = poolTokenMapped,
                            tokenReward = rewardTokenMapped,
                            amount = mapBalance(pooled, baseTokenMapped.precision),
                        )
                    } else {
                        null
                    }
                }
        }
    }
}
