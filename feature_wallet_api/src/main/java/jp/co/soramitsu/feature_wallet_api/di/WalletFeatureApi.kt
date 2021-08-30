package jp.co.soramitsu.feature_wallet_api.di

import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface WalletFeatureApi {

    fun providesWalletRepository(): WalletRepository

    fun providesWalletInteractor(): WalletInteractor
}
