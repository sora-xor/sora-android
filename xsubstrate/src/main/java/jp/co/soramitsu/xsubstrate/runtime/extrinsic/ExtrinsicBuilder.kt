package jp.co.soramitsu.xsubstrate.runtime.extrinsic

import java.math.BigInteger
import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xcrypto.hash.blake2.blake2b256
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.SignatureWrapper
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.Type
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.AdditionalExtras
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.Era
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.ExtrinsicPayloadExtrasInstance
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.new
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.instances.SignatureInstanceConstructor
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.toHex
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.xsubstrate.runtime.metadata.call
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion

private val DEFAULT_TIP = BigInteger.ZERO

private const val PAYLOAD_HASH_THRESHOLD = 256

class ExtrinsicBuilder(
    val runtime: RuntimeSnapshot,
    private val keypair: Keypair,
    private val nonce: BigInteger,
    private val runtimeVersion: RuntimeVersion,
    private val genesisHash: ByteArray,
    private val multiChainEncryption: MultiChainEncryption,
    private val accountIdentifier: Any,
    private val blockHash: ByteArray = genesisHash,
    private val era: Era = Era.Immortal,
    private val tip: BigInteger = DEFAULT_TIP,
    private val signatureConstructor: Type.InstanceConstructor<SignatureWrapper> = SignatureInstanceConstructor,
) {
    private val calls = mutableListOf<GenericCall.Instance>()

    fun call(
        moduleIndex: Int,
        callIndex: Int,
        args: Map<String, Any?>,
    ): ExtrinsicBuilder {
        val module = runtime.metadata.module(moduleIndex)
        val function = module.call(callIndex)

        calls.add(GenericCall.Instance(module, function, args))

        return this
    }

    fun call(
        moduleName: String,
        callName: String,
        arguments: Map<String, Any?>,
    ): ExtrinsicBuilder {
        val module = runtime.metadata.module(moduleName)
        val function = module.call(callName)

        calls.add(GenericCall.Instance(module, function, arguments))

        return this
    }

    fun build(useBatchAll: Boolean = false): String {
        val call = maybeWrapInBatch(useBatchAll)
        val multiSignature = buildSignature(call)
        val signedExtras = buildSignedExtras()

        val extrinsic =
            Extrinsic.Instance(
                signature =
                Extrinsic.Signature.new(
                    accountIdentifier = accountIdentifier,
                    signature = multiSignature,
                    signedExtras = signedExtras,
                ),
                call = call,
            )

        return Extrinsic.toHex(runtime, extrinsic)
    }

    private fun maybeWrapInBatch(useBatchAll: Boolean): GenericCall.Instance {
        return if (calls.size == 1) {
            calls.first()
        } else {
            wrapInBatch(useBatchAll)
        }
    }

    private fun buildSignature(call: GenericCall.Instance): Any? {
        val signedExtrasInstance =
            mapOf(
                // CheckMortality
                SignedExtras.ERA to era,
                // CheckNonce
                SignedExtras.NONCE to nonce,
                // ChargeTransactionPayment
                SignedExtras.TIP to tip,
                // ChargeAssetTxPayment
                SignedExtras.ASSET_TX_PAYMENT to listOf(DEFAULT_TIP, null),
            )

        val additionalExtrasInstance =
            mapOf(
                // CheckMortality
                AdditionalExtras.BLOCK_HASH to blockHash,
                // CheckGenesis
                AdditionalExtras.GENESIS to genesisHash,
                // CheckSpecVersion
                AdditionalExtras.SPEC_VERSION to runtimeVersion.specVersion.toBigInteger(),
                // CheckTxVersion
                AdditionalExtras.TX_VERSION to runtimeVersion.transactionVersion.toBigInteger(),
            )

        val payloadBytes =
            useScaleWriter {
                GenericCall.encode(this, runtime, call)
                SignedExtras.encode(this, runtime, signedExtrasInstance)
                AdditionalExtras.encode(this, runtime, additionalExtrasInstance)
            }

        val messageToSign =
            if (payloadBytes.size > PAYLOAD_HASH_THRESHOLD) {
                payloadBytes.blake2b256()
            } else {
                payloadBytes
            }

        val signatureWrapper = SignWrapper.sign(multiChainEncryption, messageToSign, keypair)

        return signatureConstructor.constructInstance(runtime.typeRegistry, signatureWrapper)
    }

    private fun wrapInBatch(useBatchAll: Boolean): GenericCall.Instance {
        val batchModule = runtime.metadata.module("Utility")
        val batchFunctionName = if (useBatchAll) "batch_all" else "batch"
        val batchFunction = batchModule.call(batchFunctionName)

        return GenericCall.Instance(
            module = batchModule,
            function = batchFunction,
            arguments =
            mapOf(
                "calls" to calls,
            ),
        )
    }

    private fun buildSignedExtras(): ExtrinsicPayloadExtrasInstance =
        mapOf(
            // CheckMortality
            SignedExtras.ERA to era,
            // ChargeTransactionPayment
            SignedExtras.TIP to tip,
            // ChargeAssetTxPayment
            SignedExtras.ASSET_TX_PAYMENT to listOf(DEFAULT_TIP, null),
            // CheckNonce
            SignedExtras.NONCE to nonce,
        )
}
