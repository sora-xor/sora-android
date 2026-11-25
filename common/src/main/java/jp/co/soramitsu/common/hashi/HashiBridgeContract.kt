package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex

/**
 * The reviewed constants of the HASHI bridge (SORA mainnet `ethBridge` pallet ⇄ Ethereum
 * mainnet), checked against live mainnet on 2026-11-24 (runtime 142). The app signs nothing
 * on either chain unless the chain's own values equal these.
 */
object HashiBridgeContract {

    /** `ethBridge` network id of Ethereum mainnet on SORA. */
    const val NETWORK_ID = 0

    const val ETHEREUM_CHAIN_ID = 1L

    /** `ethBridge` pallet index and `transfer_to_sidechain` call index on runtime 142. */
    const val PALLET_INDEX = 31
    const val TRANSFER_TO_SIDECHAIN_CALL_INDEX = 3
    const val REQUEST_REGISTERED_EVENT_INDEX = 0

    /** The bridge contract on Ethereum (3 peers, the same set as on SORA). */
    const val BRIDGE_ADDRESS = "0x313416870A4da6F12505a550B67bB73c8E21D5d3"

    /** The VAL master contract; it mints VAL ([VAL_TOKEN_ADDRESS]) for `SidechainOwned` claims. */
    const val VAL_MASTER_ADDRESS = "0xD1EEB2f30016fffd746233Ee12C486e7CA8eFeF1"

    const val VAL_TOKEN_ADDRESS = "0xe88f8313e61A97cEc1871EE37fBbe2a8bf3ed1E4"

    /**
     * The decommissioned legacy XOR master. It is never called: SORA no longer approves claims
     * through it (the web app still points at it; that is a web bug).
     */
    const val LEGACY_XOR_MASTER_ADDRESS = "0xc08edf13Be9B9CC584c5Da8004CE7e6be63C1316"

    val bridge: ByteArray get() = EthHex.decodeFixed(BRIDGE_ADDRESS, EthAddress.SIZE)
    val valMaster: ByteArray get() = EthHex.decodeFixed(VAL_MASTER_ADDRESS, EthAddress.SIZE)
    val valToken: ByteArray get() = EthHex.decodeFixed(VAL_TOKEN_ADDRESS, EthAddress.SIZE)
    val legacyXorMaster: ByteArray get() = EthHex.decodeFixed(LEGACY_XOR_MASTER_ADDRESS, EthAddress.SIZE)

    /** Ether itself is registered on SORA as a `Sidechain` asset with the zero token address. */
    val NATIVE_ETHER: ByteArray get() = EthAddress.ZERO

    /** Ethereum JSON-RPC endpoints without API keys, tried in this order. */
    val ETHEREUM_RPC_ENDPOINTS = listOf(
        "https://ethereum-rpc.publicnode.com",
        "https://1rpc.io/eth",
        "https://eth.drpc.org",
    )

    // --- SORA storage ---------------------------------------------------------------------------

    /** Root's recovery latch: present (any value) ⇒ the pallet refuses every transfer at dispatch. */
    const val RECOVERY_HALT_KEY = "0x3a736f72612d6e657875733a7265636f766572793a6272696467652d68616c743a7631"

    /** Guardian halt of the bridge workers: present ⇒ peers stop signing. */
    const val GUARDIAN_HALT_KEY = "0x3a736f72612d6e657875733a6272696467652d68616c743a6c61746573743a7631"

    // --- Assets ---------------------------------------------------------------------------------

    const val XOR_ASSET_ID = "0x0200000000000000000000000000000000000000000000000000000000000000"
    const val VAL_ASSET_ID = "0x0200040000000000000000000000000000000000000000000000000000000000"
    const val ETH_ASSET_ID = "0x0200070000000000000000000000000000000000000000000000000000000000"

    /**
     * TBCD is denominated: the bridge multiplies an outgoing amount by the `Denomination` pallet's
     * factor and divides an incoming one by it. XOR's Ethereum registration uses factor 1.
     */
    const val TBCD_ASSET_ID = "0x02000a0000000000000000000000000000000000000000000000000000000000"

    // --- Gas ------------------------------------------------------------------------------------

    /** Fallback gas limits of the web app, used only for estimates shown before a claim exists. */
    val GAS_MINT_TOKENS_BY_PEERS: BigInteger = BigInteger.valueOf(211_000)
    val GAS_RECEIVE_BY_SIDECHAIN_ASSET_ID: BigInteger = BigInteger.valueOf(184_000)
    val GAS_RECEIVE_BY_ETHEREUM_ASSET_ADDRESS_ETH: BigInteger = BigInteger.valueOf(155_000)
    val GAS_RECEIVE_BY_ETHEREUM_ASSET_ADDRESS_TOKEN: BigInteger = BigInteger.valueOf(181_000)
    val GAS_APPROVE: BigInteger = BigInteger.valueOf(45_000)
    val GAS_SEND_ERC20_TO_SIDECHAIN: BigInteger = BigInteger.valueOf(53_000)
    val GAS_SEND_ETH_TO_SIDECHAIN: BigInteger = BigInteger.valueOf(26_093)

    /** The highest priority fee the wallet offers: 3 gwei. */
    val MAX_PRIORITY_FEE_PER_GAS: BigInteger = BigInteger.valueOf(3_000_000_000L)
}
