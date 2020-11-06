package jp.co.soramitsu.common.domain

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

class AssetHolder {

    companion object {
        val SORA_XOR = Asset("xor#sora", "Sora", "XOR", true, false, 0, Asset.State.NORMAL, 2, null)
        val SORA_VAL = Asset("val#sora", "Sora", "VAL", true, false, 0, Asset.State.NORMAL, 2, null)
        val ETHER_ETH = Asset("ether#ethereum", "Ether", "ETH", true, true, 1, Asset.State.UNKNOWN, 18, null)
        val SORA_XOR_ERC_20 = Asset("xor_erc20#sora", "Sora", "XOR_ERC_20", false, true, 0, Asset.State.UNKNOWN, 2, null)
        val SORA_VAL_ERC_20 = Asset("val_erc20#sora", "Sora", "VAL_ERC_20", false, true, 0, Asset.State.UNKNOWN, 2, null)

        private val assets = mutableListOf(SORA_VAL, ETHER_ETH, SORA_VAL_ERC_20)
    }

    fun getAssets(): List<Asset> {
        return assets
    }
}