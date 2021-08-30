package jp.co.soramitsu.feature_account_api.di

import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository

interface AccountFeatureApi {

    fun userRepository(): UserRepository
}
