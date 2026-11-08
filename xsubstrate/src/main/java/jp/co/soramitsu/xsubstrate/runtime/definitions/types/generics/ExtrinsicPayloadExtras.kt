package jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.Type
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.TypeReference
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Option
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.Compact
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.u32

typealias ExtrinsicPayloadExtrasInstance = Map<String, Any?>

private const val MORTALITY_KEY = "CheckMortality"
private const val NONCE_KEY = "CheckNonce"
private const val TIP_KEY = "ChargeTransactionPayment"
private const val ASSET_TX_PAYMENT_KEY = "ChargeAssetTxPayment"

object SignedExtras : ExtrinsicPayloadExtras(
    name = "SignedExtras",
    extrasMapping =
    mapOf(
        MORTALITY_KEY to EraType,
        NONCE_KEY to Compact("Compact<Index>"),
        TIP_KEY to Compact("Compact<u32>"),
        ASSET_TX_PAYMENT_KEY to
            Tuple(
                ASSET_TX_PAYMENT_KEY,
                listOf(TypeReference(Compact("u32")), TypeReference(Option("asset_id", TypeReference(u32)))),
            ),
    ),
) {
    const val ERA = MORTALITY_KEY
    const val NONCE = NONCE_KEY
    const val TIP = TIP_KEY
    const val ASSET_TX_PAYMENT = ASSET_TX_PAYMENT_KEY
}

private const val GENESIS_KEY = "CheckGenesis"
private const val SPEC_VERSION_KEY = "CheckSpecVersion"
private const val TX_VERSION_KEY = "CheckTxVersion"

object AdditionalExtras : ExtrinsicPayloadExtras(
    name = "AdditionalExtras",
    extrasMapping =
    mapOf(
        MORTALITY_KEY to H256,
        GENESIS_KEY to H256,
        SPEC_VERSION_KEY to u32,
        TX_VERSION_KEY to u32,
    ),
) {
    const val BLOCK_HASH = MORTALITY_KEY
    const val GENESIS = GENESIS_KEY
    const val SPEC_VERSION = SPEC_VERSION_KEY
    const val TX_VERSION = TX_VERSION_KEY
}

open class ExtrinsicPayloadExtras(
    name: String,
    private val extrasMapping: Map<String, Type<*>>,
) : Type<ExtrinsicPayloadExtrasInstance>(name) {
    override val isFullyResolved: Boolean = true

    override fun decode(
        scaleCodecReader: ScaleCodecReader,
        runtime: RuntimeSnapshot,
    ): ExtrinsicPayloadExtrasInstance {
        val enabledSignedExtras = runtime.metadata.extrinsic.signedExtensions

        return enabledSignedExtras.associateWith { name ->
            extrasMapping[name]?.decode(scaleCodecReader, runtime)
        }
    }

    override fun encode(
        scaleCodecWriter: ScaleCodecWriter,
        runtime: RuntimeSnapshot,
        value: ExtrinsicPayloadExtrasInstance,
    ) {
        val enabledSignedExtras = runtime.metadata.extrinsic.signedExtensions

        return enabledSignedExtras.forEach { name ->
            extrasMapping[name]?.encodeUnsafe(scaleCodecWriter, runtime, value[name])
        }
    }

    override fun isValidInstance(instance: Any?): Boolean {
        return instance is Map<*, *> && instance.keys.all { it is String }
    }
}
