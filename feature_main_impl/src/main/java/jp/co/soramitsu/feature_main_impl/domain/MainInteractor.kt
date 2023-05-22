/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import kotlinx.coroutines.flow.Flow

@Singleton
class MainInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val selectNodeRepository: SelectNodeRepository
) {
    suspend fun getCurUserAddress(): String {
        return userRepository.getCurSoraAccount().substrateAddress
    }

    fun getAppVersion(): String = OptionsProvider.CURRENT_VERSION_NAME

    suspend fun getAvailableLanguagesWithSelected(): Pair<List<Language>, Int> {
        return userRepository.getAvailableLanguages()
    }

    suspend fun changeLanguage(language: String): String {
        return userRepository.changeLanguage(language)
    }

    suspend fun setBiometryEnabled(isEnabled: Boolean) {
        return userRepository.setBiometryEnabled(isEnabled)
    }

    suspend fun isBiometryEnabled(): Boolean {
        return userRepository.isBiometryEnabled()
    }

    suspend fun isBiometryAvailable(): Boolean {
        return userRepository.isBiometryAvailable()
    }

    suspend fun saveAccountName(accountName: String) {
        return userRepository.updateAccountName(userRepository.getCurSoraAccount(), accountName)
    }

    suspend fun getAccountName(): String {
        return userRepository.getCurSoraAccount().accountName
    }

    suspend fun getSoraAccountsList(): List<SoraAccount> =
        userRepository.soraAccountsList()

    suspend fun getSoraAccountsCount(): Int =
        userRepository.getSoraAccountsCount()

    fun flowCurSoraAccount(): Flow<SoraAccount> =
        userRepository.flowCurSoraAccount()

    suspend fun setCurSoraAccount(account: SoraAccount) {
        userRepository.setCurSoraAccount(account)
    }

    fun flowSelectedNode(): Flow<ChainNode?> =
        selectNodeRepository.getSelectedNode()
}
