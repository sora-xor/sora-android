/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.R

class AssetHolder {

    companion object {
        private val knownAssets: Map<String, AssetDefault> = mapOf(
            // xor
            "0x0200000000000000000000000000000000000000000000000000000000000000" to
                AssetDefault(
                    true, false,
                    1, 4,
                    R.drawable.ic_xor_red_shadow
                ),
            // val
            "0x0200040000000000000000000000000000000000000000000000000000000000" to
                AssetDefault(
                    true, true,
                    2, 4,
                    R.drawable.ic_val_gold_shadow
                ),
            // pswap
            "0x0200050000000000000000000000000000000000000000000000000000000000" to
                AssetDefault(
                    true, true,
                    3, 4,
                    R.drawable.ic_polkaswap_shadow
                )
        )
        private val defaultAsset: AssetDefault = AssetDefault(
            isDisplay = true,
            isHidingAllowed = true,
            position = 4,
            roundingPrecision = 4,
            iconShadow = R.drawable.ic_asset_24
        )
    }

    private fun getAsset(id: String): AssetDefault = knownAssets[id] ?: defaultAsset
    fun isDisplay(id: String): Boolean = getAsset(id).isDisplay
    fun isHiding(id: String): Boolean = getAsset(id).isHidingAllowed
    fun position(id: String): Int = getAsset(id).position
    fun rounding(id: String): Int = getAsset(id).roundingPrecision
    fun iconShadow(id: String): Int = getAsset(id).iconShadow
    fun isKnownAsset(id: String): Boolean = knownAssets[id] != null

    internal data class AssetDefault(
        val isDisplay: Boolean,
        val isHidingAllowed: Boolean,
        val position: Int,
        val roundingPrecision: Int,
        val iconShadow: Int,
    )
}
