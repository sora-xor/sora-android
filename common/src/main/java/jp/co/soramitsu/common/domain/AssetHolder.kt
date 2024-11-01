/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.domain

object AssetHolder {

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
    fun knownCount(): Int = knownAssets.size

    private data class AssetDefault(
        val name: String,
        val symbol: String,
        val isDisplay: Boolean,
        val isHidingAllowed: Boolean,
        val position: Int,
    )
}
