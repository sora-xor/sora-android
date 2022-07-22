/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.runtime

import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.common.util.ext.addHexPrefix
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import java.math.BigInteger

object SubstrateOptionsProvider {
    const val dexId: Int = 0
    const val url: String = FlavorOptionsProvider.wsHostUrl
    const val hash: String = FlavorOptionsProvider.genesisHash
    const val typesFilePath: String = FlavorOptionsProvider.typesFilePath
    const val mortalEraLength = 64
    val encryptionType = EncryptionType.SR25519
    val existentialDeposit: BigInteger = BigInteger.ZERO
    const val feeAssetId = "0x0200000000000000000000000000000000000000000000000000000000000000"
    const val xstTokenId = "0x0200080000000000000000000000000000000000000000000000000000000000"
    val xstPoolTokens = arrayListOf(
        "0x0200040000000000000000000000000000000000000000000000000000000000",
        "0x0200050000000000000000000000000000000000000000000000000000000000",
        "0x0200060000000000000000000000000000000000000000000000000000000000",
        "0x0200070000000000000000000000000000000000000000000000000000000000"
    )
}

fun String.assetIdFromKey() = this.takeLast(64).addHexPrefix()
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

fun RuntimeSnapshot.reservesKey(runtime: RuntimeManager, tokenId: ByteArray): String =
    this.metadata.module(Pallete.POOL_XYK.palletName)
        .storage(Storage.RESERVES.storageName)
        .storageKey(
            this,
            if (runtime.getMetadataVersion() < 14) SubstrateOptionsProvider.feeAssetId.fromHex() else
                Struct.Instance(
                    mapOf(
                        "code" to SubstrateOptionsProvider.feeAssetId.fromHex().toList()
                            .map { it.toInt().toBigInteger() }
                    )
                ),
            if (runtime.getMetadataVersion() < 14) tokenId else
                Struct.Instance(
                    mapOf(
                        "code" to tokenId.toList().map { it.toInt().toBigInteger() }
                    )
                )
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
}

enum class Storage(val storageName: String) {
    ASSET_INFOS("AssetInfos"),
    ACCOUNT("Account"),
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
}

enum class Events(val eventName: String) {
    EXTRINSIC_SUCCESS("ExtrinsicSuccess"),
    EXTRINSIC_FAILED("ExtrinsicFailed"),
}

enum class Constants(val constantName: String) {
    SS58Prefix("SS58Prefix")
}
