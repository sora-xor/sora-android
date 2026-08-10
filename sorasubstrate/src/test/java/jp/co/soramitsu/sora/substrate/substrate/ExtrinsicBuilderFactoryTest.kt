package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2MutationRuntimeContext
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtrinsicBuilderFactoryTest {

    @Test
    fun `signing builder retains the exact bounded RPC nonce as BigInteger`() = runTest {
        val runtimeRpcClient = mockk<Sora2BoundedRuntimeRpcClient>()
        val keypair = mockk<Sr25519Keypair>()
        val accountAddress = requireNotNull(
            Sora2AddressCodec().toSoraAddressOrNull(
                ByteArray(32) { index -> (index + 1).toByte() }
            )
        )
        val exactNonce = BigInteger("4294967295")
        val runtimeContext = Sora2MutationRuntimeContext(
            snapshot = mockk<RuntimeSnapshot>(relaxed = true),
            runtimeVersion = mockk<RuntimeVersion>(relaxed = true),
            genesisHash = "0x${"aa".repeat(32)}",
            finalizedHash = "0x${"bb".repeat(32)}",
            metadataSha256 = "c".repeat(64),
            typesSha256 = "d".repeat(64),
            finalizedBlockNumber = 1L,
        )
        coEvery {
            runtimeRpcClient.getAccountNextIndex(accountAddress)
        } returns exactNonce

        val result = ExtrinsicBuilderFactory(
            runtimeRpcClient = runtimeRpcClient,
        ).createForSigning(
            from = accountAddress,
            keypair = keypair,
            runtimeContext = runtimeContext,
        )

        assertEquals(exactNonce, retainedNonce(result.builder))
        coVerify(exactly = 1) {
            runtimeRpcClient.getAccountNextIndex(accountAddress)
        }
    }

    /**
     * xsubstrate deliberately keeps the signing fields private. This test observes the pinned
     * dependency's retained constructor value so a future Int/Double conversion at the factory
     * boundary cannot be hidden by a successful RPC-client test.
     */
    private fun retainedNonce(builder: ExtrinsicBuilder): BigInteger {
        var type: Class<*>? = builder.javaClass
        while (type != null) {
            val nonceField = type.declaredFields.firstOrNull { it.name == "nonce" }
            if (nonceField != null) {
                nonceField.isAccessible = true
                return nonceField.get(builder) as BigInteger
            }
            type = type.superclass
        }
        error("Pinned xsubstrate ExtrinsicBuilder no longer retains a nonce field")
    }
}
