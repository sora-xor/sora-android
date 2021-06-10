package jp.co.soramitsu.feature_wallet_impl.data.network.substrate

import jp.co.soramitsu.common.data.network.substrate.Method
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.util.ext.removeHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.uint128
import jp.co.soramitsu.fearless_utils.scale.uint32
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import java.math.BigInteger

fun String.blake2b256String() = this.fromHex().blake2b256().toHexString(true)

object AccountData : Schema<AccountData>() {
    val free by uint128()
    val reserved by uint128()
    val miscFrozen by uint128()
    val feeFrozen by uint128()
}

object AccountInfo : Schema<AccountInfo>() {
    val nonce by uint32()
    val consumers by uint32()
    val providers by uint32()
    val data by schema(AccountData)
}

fun ExtrinsicBuilder.transfer(assetId: String, to: String, amount: BigInteger) =
    this.call(
        Pallete.ASSETS.palleteName,
        Method.TRANSFER.methodName,
        mapOf(
            "asset_id" to assetId.removeHexPrefix()
                .fromHex(),
            "to" to to.toAccountId(),
            "amount" to amount
        )
    )

fun ExtrinsicBuilder.migrate(irohaAddress: String, irohaPublicKey: String, signature: String) =
    this.call(
        Pallete.IROHA_MIGRATION.palleteName,
        Method.MIGRATE.methodName,
        mapOf(
            "iroha_address" to irohaAddress.toByteArray(charset("UTF-8")),
            "iroha_public_key" to irohaPublicKey.toByteArray(charset("UTF-8")),
            "iroha_signature" to signature.toByteArray(charset("UTF-8"))
        )
    )
