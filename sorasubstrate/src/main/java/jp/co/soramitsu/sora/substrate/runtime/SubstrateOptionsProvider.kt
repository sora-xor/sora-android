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

package jp.co.soramitsu.sora.substrate.runtime

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.util.ext.addHexPrefix
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider.syntheticTokenRegex
import jp.co.soramitsu.sora.substrate.substrate.fromHex
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.scale.datatype.uint32
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId

object SubstrateOptionsProvider {
    const val mortalEraLength = 64
    val syntheticTokenRegex = "0[xX]03[0-9a-fA-F]+".toRegex()
    val encryptionType = EncryptionType.SR25519
    val existentialDeposit: BigInteger = BigInteger.ZERO
    const val feeAssetId = "0x0200000000000000000000000000000000000000000000000000000000000000"
    const val pswapAssetId = "0x0200050000000000000000000000000000000000000000000000000000000000"
    const val xstTokenId = "0x0200090000000000000000000000000000000000000000000000000000000000"
    const val xstusdTokenId = "0x0200080000000000000000000000000000000000000000000000000000000000"
    const val ethTokenId = "0x0200070000000000000000000000000000000000000000000000000000000000"
    const val kxorTokenId = "0x02000e0000000000000000000000000000000000000000000000000000000000"
}

fun String.isSynthetic(): Boolean = this.matches(syntheticTokenRegex)

fun Struct.Instance.getTokenId() = get<List<*>>("code")
    ?.map { (it as BigInteger).toByte() }
    ?.toByteArray()

fun Struct.Instance.mapToToken(field: String) =
    this.get<Struct.Instance>(field)?.getTokenId()?.toHexString(true)

fun String.mapCodeToken() = Struct.Instance(
    mapOf("code" to this.mapAssetId())
)

fun ByteArray.mapCodeToken() = Struct.Instance(
    mapOf("code" to this.mapAssetId())
)

fun String.mapAssetId() = this.fromHex().mapAssetId()
fun ByteArray.mapAssetId() = this.toList().map { it.toInt().toBigInteger() }

fun String.assetIdFromKey() = this.takeLast(64).addHexPrefix()
fun String.assetIdFromKey(pos: Int): String =
    this.substring(0, this.length - (64 * pos)).assetIdFromKey()

fun String.takeInt32() = uint32.fromHex(this.takeLast(8)).toInt()
fun Any.createAsset(id: String): TokenInfoDto? =
    (this as? List<*>)?.let {
        val s = (it[0] as? ByteArray)?.toString(Charsets.UTF_8)
        val n = (it[1] as? ByteArray)?.toString(Charsets.UTF_8)
        val p = (it[2] as? BigInteger)?.toInt()
        val m = it[3] as? Boolean
        if (s != null && n != null && p != null && m != null)
            TokenInfoDto(id, n, s, p, m) else null
    }

fun RuntimeSnapshot.accountPoolsKey(address: String): String =
    this.metadata.module(Pallete.POOL_XYK.palletName)
        .storage(Storage.ACCOUNT_POOLS.storageName)
        .storageKey(this, address.toAccountId())

fun RuntimeSnapshot.poolTBCReserves(tokenId: ByteArray): String =
    this.metadata.module(Pallete.POOL_TBC.palletName)
        .storage(Storage.RESERVES_COLLATERAL.storageName)
        .storageKey(
            this,
            tokenId.mapCodeToken(),
        )

fun RuntimeSnapshot.reservesKey(baseTokenId: String, tokenId: ByteArray): String =
    this.metadata.module(Pallete.POOL_XYK.palletName)
        .storage(Storage.RESERVES.storageName)
        .storageKey(
            this,
            baseTokenId.mapCodeToken(),
            tokenId.mapCodeToken(),
        )

fun RuntimeSnapshot.reservesKeyToken(baseTokenId: String): String =
    this.metadata.module(Pallete.POOL_XYK.palletName)
        .storage(Storage.RESERVES.storageName)
        .storageKey(
            this,
            baseTokenId.mapCodeToken(),
        )

enum class Pallete(val palletName: String) {
    ASSETS("Assets"),
    IROHA_MIGRATION("IrohaMigration"),
    SYSTEM("System"),
    LIQUIDITY_PROXY("LiquidityProxy"),
    POOL_XYK("PoolXYK"),
    POOL_TBC("MulticollateralBondingCurvePool"),
    STAKING("Staking"),
    TRADING_PAIR("TradingPair"),
    UTILITY("Utility"),
    FAUCET("Faucet"),
    Referrals("Referrals"),
    DEX_MANAGER("DEXManager"),
    XSTPool("XSTPool"),
    TOKENS("Tokens"),
    DEMETER_FARMING("DemeterFarmingPlatform"),
    ETH_BRIDGE("EthBridge"),
}

enum class Storage(val storageName: String) {
    ASSET_INFOS("AssetInfos"),
    ACCOUNT("Account"),
    ACCOUNTS("Accounts"),
    PARENT_HASH("ParentHash"),
    RESERVES("Reserves"),
    RESERVES_COLLATERAL("CollateralReserves"),
    LEDGER("Ledger"),
    ACTIVE_ERA("ActiveEra"),
    BONDED("Bonded"),
    UPGRADED_TO_DUAL_REF_COUNT("UpgradedToDualRefCount"),
    ACCOUNT_POOLS("AccountPools"),
    PROPERTIES("Properties"),
    TOTAL_ISSUANCES("TotalIssuances"),
    POOL_PROVIDERS("PoolProviders"),
    REFERRER_BALANCE("ReferrerBalances"),
    REFERRERS("Referrers"),
    REFERRALS("Referrals"),
    DEX_INFOS("DEXInfos"),
    BASE_FEE("BaseFee"),
    USER_INFOS("UserInfos"),
    TOKEN_INFOS("TokenInfos"),
    POOLS("Pools"),
}

enum class Method(val methodName: String) {
    TRANSFER("transfer"),
    MIGRATE("migrate"),
    SWAP("swap"),
    REGISTER("register"),
    INITIALIZE_POOL("initialize_pool"),
    DEPOSIT_LIQUIDITY("deposit_liquidity"),
    WITHDRAW_LIQUIDITY("withdraw_liquidity"),
    BATCH_ALL("batch_all"),
    BATCH("batch"),
    SET_REFERRER("set_referrer"),
    UNRESERVE("unreserve"),
    RESERVE("reserve"),
    TRANSFER_TO_SIDECHAIN("transfer_to_sidechain"),
    DEMETER_REWARDS("get_rewards"),
    DEMETER_STAKE("deposit"),
    DEMETER_UNSTAKE("withdraw"),
    SWAP_TRANSFER_BATCH("swap_transfer_batch"),
}

enum class Events(val eventName: String) {
    EXTRINSIC_SUCCESS("ExtrinsicSuccess"),
    EXTRINSIC_FAILED("ExtrinsicFailed"),
}

enum class Constants(val constantName: String) {
    SS58Prefix("SS58Prefix")
}
