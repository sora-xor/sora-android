/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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

    suspend fun getGenesis(): String = getConfig()?.genesis.orEmpty()

    suspend fun getInviteLink(): String = getConfig()?.joinUrl.orEmpty()

    suspend fun getSubstrateTypesUrl(): String = getConfig()?.substrateTypesUrl.orEmpty()

    private suspend fun getCurrencies(): List<SoraCurrency> =
        getConfig()?.currencies ?: listOf(default)

    suspend fun getSelectedCurrency(): SoraCurrency = getCurrencies().find {
        it.code == (
            soraPreferences.getString(SELECTED_CURRENCY).takeIf { pref -> pref.isNotEmpty() }
                ?: "USD"
            )
    } ?: default
}
