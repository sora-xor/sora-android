/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.substrate

import jp.co.soramitsu.common.data.network.dto.EventRecord
import jp.co.soramitsu.common.data.network.dto.PoolDataDto
import jp.co.soramitsu.common.data.network.dto.SwapFeeDto
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.data.network.dto.XorBalanceDto
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

interface SubstrateApi {

    fun observeStorage(key: String): Flow<String>

    suspend fun fetchBalance(accountId: String, assetId: String): BigInteger
    suspend fun fetchAssetsList(runtime: RuntimeSnapshot): List<TokenInfoDto>
    suspend fun needsMigration(irohaAddress: String): Boolean
    suspend fun transfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot
    ): String

    suspend fun observeTransfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun calcFee(
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot
    ): BigInteger

    suspend fun checkEvents(runtime: RuntimeSnapshot, blockHash: String): List<EventRecord>
    suspend fun getBlock(blockHash: String): BlockResponse
    suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Keypair,
        runtime: RuntimeSnapshot
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun fetchBalances(runtime: RuntimeSnapshot, accountId: String): BigInteger
    suspend fun isUpgradedToDualRefCount(runtime: RuntimeSnapshot): Boolean
    suspend fun fetchXORBalances(runtime: RuntimeSnapshot, accountId: String): XorBalanceDto

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean
    suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): List<String>
    suspend fun getSwapFees(tokenId1: String, tokenId2: String, amount: BigInteger, swapVariant: String, market: List<String>, filter: String): SwapFeeDto?
    fun observeSwap(
        keypair: Keypair,
        from: String,
        runtime: RuntimeSnapshot,
        inputAssetId: String,
        outputAssetId: String,
        filter: String,
        markets: List<String>,
        desired: WithDesired,
        amount: BigInteger,
        limit: BigInteger,
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun calcSwapNetworkFee(
        from: String,
        runtime: RuntimeSnapshot,
        inputAssetId: String,
        outputAssetId: String,
        filter: String,
        markets: List<String>,
        desired: WithDesired,
        amount: BigInteger,
        limit: BigInteger,
    ): BigInteger

    suspend fun getPoolReserveAccount(runtime: RuntimeSnapshot, tokenId: ByteArray): ByteArray

    suspend fun getUserPoolsData(
        runtime: RuntimeSnapshot,
        address: String,
        tokensId: List<ByteArray>
    ): List<PoolDataDto>

    suspend fun getUserPoolsTokenIds(
        runtime: RuntimeSnapshot,
        address: String
    ): List<ByteArray>
}
