/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.di

import android.content.Context
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DidProvider
import jp.co.soramitsu.common.util.MnemonicProvider
import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder

interface DidFeatureDependencies {

    fun context(): Context

    fun prefsUtil(): PrefsUtil

    fun authHolder(): AuthHolder

    fun networkApiCreator(): NetworkApiCreator

    fun mnemonicProvider(): MnemonicProvider

    fun cryptoAssistsant(): CryptoAssistant

    fun didProvider(): DidProvider
}