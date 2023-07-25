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

package jp.co.soramitsu.sora.substrate.blockexplorer

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraConfig
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraConfigNode
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraCurrency
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigBuilder

@Singleton
class SoraConfigManager @Inject constructor(
    private val remoteConfigBuilder: SoraRemoteConfigBuilder,
    private val soraPreferences: SoraPreferences,
) {

    companion object {
        private const val SELECTED_CURRENCY = "selected_currency"
        private val default = SoraCurrency("USD", "United States Dollar", "$")
    }

    private suspend fun getConfig(): SoraConfig? = remoteConfigBuilder.getConfig()

    suspend fun getNodes(): List<SoraConfigNode> {
        return getConfig()?.nodes ?: emptyList()
    }

    suspend fun getSoraCard(): Boolean =
        getConfig()?.soracard ?: false

    suspend fun getGenesis(): String = getConfig()?.genesis.orEmpty()

    suspend fun getInviteLink(): String = getConfig()?.joinUrl.orEmpty()

    suspend fun getSubstrateTypesUrl(): String = getConfig()?.substrateTypesUrl.orEmpty()

    private suspend fun getCurrencies(): List<SoraCurrency> =
        getConfig()?.currencies ?: listOf(default)

    private var selectedCurrency: SoraCurrency? = null

    suspend fun getSelectedCurrency() = selectedCurrency ?: getSelectedCurrencyInternal().also {
        selectedCurrency = it
    }

    private suspend fun getSelectedCurrencyInternal(): SoraCurrency = getCurrencies().find {
        it.code == (
            soraPreferences.getString(SELECTED_CURRENCY).takeIf { pref -> pref.isNotEmpty() }
                ?: "USD"
            )
    } ?: default
}
