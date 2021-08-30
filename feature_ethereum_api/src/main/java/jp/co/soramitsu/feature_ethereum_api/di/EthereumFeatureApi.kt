package jp.co.soramitsu.feature_ethereum_api.di

import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository

interface EthereumFeatureApi {

    fun providesEthereumRepository(): EthereumRepository

    fun provideEthereumInteractor(): EthereumInteractor
}
