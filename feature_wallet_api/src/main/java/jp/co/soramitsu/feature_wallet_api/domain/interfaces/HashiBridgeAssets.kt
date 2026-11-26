package jp.co.soramitsu.feature_wallet_api.domain.interfaces

/** Which SORA assets the HASHI bridge (SORA ⇄ Ethereum) takes. */
interface HashiBridgeAssets {

    /** The bridge exists in this build: it is pinned to SORA and Ethereum mainnet. */
    val supported: Boolean

    /**
     * True when [assetId] is registered on the bridge. A failed read answers false; the bridge
     * screen itself explains what it cannot read.
     */
    suspend fun isRegistered(assetId: String): Boolean
}
