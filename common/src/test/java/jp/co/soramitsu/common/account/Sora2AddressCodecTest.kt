package jp.co.soramitsu.common.account

import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Sora2AddressCodecTest {

    private val codec = Sora2AddressCodec()

    @Test
    fun `pinned production public key round trips through the existing Sora2 address`() {
        val publicKey = PINNED_PUBLIC_KEY.fromHex()

        assertEquals(PINNED_ADDRESS, codec.toSoraAddressOrNull(publicKey))
        assertArrayEquals(publicKey, codec.soraPublicKeyOrNull(PINNED_ADDRESS))
    }

    @Test
    fun `cross network prefix and malformed public key fail closed`() {
        val publicKey = PINNED_PUBLIC_KEY.fromHex()
        val otherNetworkAddress = publicKey.toAddress(42.toShort())

        assertNull(codec.soraPublicKeyOrNull(otherNetworkAddress))
        assertNull(codec.soraPublicKeyOrNull("cnMalformedWallet"))
        assertNull(codec.toSoraAddressOrNull(ByteArray(31)))
        assertNull(codec.toSoraAddressOrNull(null))
    }

    private companion object {
        const val PINNED_PUBLIC_KEY =
            "66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972"
        const val PINNED_ADDRESS =
            "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
    }
}
