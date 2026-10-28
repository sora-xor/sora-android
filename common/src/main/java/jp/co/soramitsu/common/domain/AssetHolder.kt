/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.domain

object AssetHolder {

    const val XOR_ASSET_ID = "0x0200000000000000000000000000000000000000000000000000000000000000"
    const val DEFAULT_WHITE_LIST_NAME = "tokens_white_list"
    const val ACTIVITY_LIST_ROUNDING = 3
    const val ROUNDING = 8
    val emptyToken: Token = Token(
        id = "",
        name = "",
        symbol = "???",
        precision = OptionsProvider.defaultScale,
        isHidable = true,
        iconFile = DEFAULT_ICON_URI,
        fiatPrice = null,
        fiatPriceChange = null,
        fiatSymbol = OptionsProvider.fiatSymbol,
    )
    private val knownAssets: Map<String, AssetDefault> = mapOf(
        // xor
        XOR_ASSET_ID to
            AssetDefault(
                "XOR", "XOR",
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
        // dai
        "0x0200060000000000000000000000000000000000000000000000000000000000" to
            AssetDefault(
                "Dai", "DAI",
                true, true,
                4,
            ),
        // eth
        "0x0200070000000000000000000000000000000000000000000000000000000000" to
            AssetDefault(
                "Ether", "ETH",
                true, true,
                5,
            ),
    )
    private val defaultAsset: AssetDefault = AssetDefault(
        name = "",
        symbol = "",
        isDisplay = false,
        isHidingAllowed = true,
        position = knownCount() + 1,
    )

    private fun getAsset(id: String): AssetDefault = knownAssets[id] ?: defaultAsset
    fun isDisplay(id: String): Boolean = getAsset(id).isDisplay
    fun isHiding(id: String): Boolean = getAsset(id).isHidingAllowed
    fun position(id: String): Int = getAsset(id).position
    fun isKnownAsset(id: String): Boolean = knownAssets[id] != null
    fun getIds(): List<String> = knownAssets.keys.toList()
    fun getName(id: String): String = getAsset(id).name
    fun getSymbol(id: String): String = getAsset(id).symbol
    fun displayName(id: String, name: String): String = if (id == XOR_ASSET_ID) "XOR" else name
    fun displaySymbol(id: String, symbol: String): String = if (id == XOR_ASSET_ID) "XOR" else symbol
    fun knownCount(): Int = knownAssets.size

    private data class AssetDefault(
        val name: String,
        val symbol: String,
        val isDisplay: Boolean,
        val isHidingAllowed: Boolean,
        val position: Int,
    )
}
