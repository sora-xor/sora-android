package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import java.math.BigInteger
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.sora.substrate.substrate.transferToSidechain
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.Era
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * LIVE NETWORK TEST against SORA mainnet, read-only. Off by default; run with
 *
 *     RUN_LIVE_HASHI_TESTS=true ./gradlew :sorasubstrate:testProductionDebugUnitTest \
 *       --tests '*EthBridgeLiveFeeContractTest*' --rerun
 *
 * The live runtime's metadata must encode every pinned call as reviewed, `transfer_to_sidechain`
 * included, and the node must quote a fee for the exact bridge call. The fee extrinsic is built the
 * like the app builds every fee quote (ExtrinsicBuilderFactory.createForFee): signed by a fixed dummy
 * key that is nobody's account (the node does not check the signature for a fee query). Nothing is
 * submitted.
 */
class EthBridgeLiveFeeContractTest {

    private val client by lazy { Sora2BoundedRuntimeRpcClient(BoundedHttpTextClient(), Gson(), Sora2AddressCodec()) }

    @Before
    fun requireOptIn() {
        assumeTrue(System.getenv("RUN_LIVE_HASHI_TESTS") == "true")
    }

    /**
     * Every request here is a read, so a transport failure (a deadline on a loaded build machine,
     * a dropped connection) starts the reads again, up to three times. An answer is never asked
     * again: a node error, a mismatch or an unexpected fee fails the test as it is.
     */
    @Test
    fun `the live call table matches and transfer_to_sidechain gets a fee quote`() {
        var failure: BoundedHttpTextException? = null
        repeat(TRANSPORT_ATTEMPTS) {
            try {
                return readAndCheck()
            } catch (error: BoundedHttpTextException) {
                failure = error
            }
        }
        throw checkNotNull(failure)
    }

    private fun readAndCheck() = runBlocking {
        val identity = client.getFinalizedIdentity()
        val snapshot = TestRuntimeSnapshots.build(
            metadataHex = client.getMetadataAtFinalized(identity.finalizedHash),
            typesJson = TestRuntimeSnapshots.packagedTypes,
            runtimeVersion = identity.runtimeVersion.specVersion,
        )
        assertEquals(emptyList<String>(), Sora2CallTable.mismatches(snapshot))
        // A fixed dummy key that is nobody's account. Ed25519 (pure Java here; the sr25519 JNI
        // library is not on a desktop JVM) has the same 64-byte signature, so the same length fee.
        val dummy = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 1 }, emptyList())
        val from = dummy.publicKey.toAddress(69.toShort())
        val builder = ExtrinsicBuilder(
            runtime = snapshot,
            keypair = dummy,
            nonce = client.getAccountNextIndex(from),
            runtimeVersion = identity.runtimeVersion,
            genesisHash = identity.genesisHash.removePrefix("0x").fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = from.toAccountId(),
            blockHash = identity.finalizedHash.removePrefix("0x").fromHex(),
            era = Era.getEraFromBlockPeriod(identity.finalizedBlockNumber.toInt(), SubstrateOptionsProvider.mortalEraLength),
        )
        builder.transferToSidechain(
            HashiBridgeContract.XOR_ASSET_ID,
            ByteArray(20) { 0x11 },
            BigInteger.TEN.pow(18),
            HashiBridgeContract.NETWORK_ID,
        )
        val fee = client.queryPartialFee(builder.build())
        println("LIVE spec=${identity.runtimeVersion.specVersion} transferToSidechain partialFee=$fee")
        // About 1 XOR on runtime 142.
        assertTrue(fee > BigInteger.TEN.pow(17) && fee < BigInteger.TEN.pow(19))
    }

    private companion object {
        const val TRANSPORT_ATTEMPTS = 3
    }
}
