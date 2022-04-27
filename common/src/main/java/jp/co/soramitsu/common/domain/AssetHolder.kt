/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.data.network.substrate.OptionsProvider

class AssetHolder {

    companion object {
        const val DEFAULT_WHITE_LIST_NAME = "tokens_white_list"
        const val ROUNDING = 8
        val emptyToken: Token = Token(
            id = "",
            name = "",
            symbol = "???",
            precision = 18,
            isHidable = true,
            icon = OptionsProvider.DEFAULT_ICON
        )
        private val knownAssets: Map<String, AssetDefault> = mapOf(
            // xor
            "0x0200000000000000000000000000000000000000000000000000000000000000" to
                AssetDefault(
                    "SORA", "XOR",
                    true, false,
                    1,
                ),
            // val
            "0x0200040000000000000000000000000000000000000000000000000000000000" to
                AssetDefault(
                    "SORA Validator Token", "VAL",
                    true, true,
                    2,
                ),
            // pswap
            "0x0200050000000000000000000000000000000000000000000000000000000000" to
                AssetDefault(
                    "Polkaswap", "PSWAP",
                    true, true,
                    3,
                ),
            // xstusd
            "0x0200080000000000000000000000000000000000000000000000000000000000" to
                AssetDefault(
                    "SORA Synthetic USD", "XSTUSD",
                    true, true,
                    4,
                )
        )
        private val defaultAsset: AssetDefault = AssetDefault(
            name = "",
            symbol = "",
            isDisplay = false,
            isHidingAllowed = true,
            position = 4,
        )
    }

    private fun getAsset(id: String): AssetDefault = knownAssets[id] ?: defaultAsset
    fun isDisplay(id: String): Boolean = getAsset(id).isDisplay
    fun isHiding(id: String): Boolean = getAsset(id).isHidingAllowed
    fun position(id: String): Int = getAsset(id).position
    fun isKnownAsset(id: String): Boolean = knownAssets[id] != null
    fun getIds(): List<String> = knownAssets.keys.toList()
    fun getName(id: String): String = getAsset(id).name
    fun getSymbol(id: String): String = getAsset(id).symbol

    internal data class AssetDefault(
        val name: String,
        val symbol: String,
        val isDisplay: Boolean,
        val isHidingAllowed: Boolean,
        val position: Int,
    )
}
