package jp.co.soramitsu.common.account

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress

/**
 * Stable SORA2 identity codec used while qualifying an installed wallet.
 *
 * Wallet migration must not depend on a mutable or partially loaded runtime snapshot when it
 * proves the address that an existing secret has always controlled. SORA2 production wallets use
 * SS58 prefix 69; changing that value would create a different visible identity and is therefore
 * a separate, explicit migration rather than a metadata fallback.
 */
@Singleton
class Sora2AddressCodec @Inject constructor() {

    fun soraPublicKeyOrNull(address: String): ByteArray? =
        runCatching {
            address.toAccountId().takeIf { publicKey ->
                publicKey.size == PUBLIC_KEY_SIZE &&
                    SS58Encoder.extractAddressByte(address) == SORA2_PREFIX
            }
        }.getOrNull()

    fun toSoraAddressOrNull(publicKey: ByteArray?): String? =
        runCatching {
            publicKey
                ?.takeIf { it.size == PUBLIC_KEY_SIZE }
                ?.toAddress(SORA2_PREFIX)
        }.getOrNull()

    private companion object {
        const val PUBLIC_KEY_SIZE = 32
        const val SORA2_PREFIX: Short = 69
    }
}
