package jp.co.soramitsu.feature_ethereum_api

interface EthStatusPollingServiceStarter {

    fun startEthStatusPollingServiceService()

    fun stopEthStatusPollingServiceService()
}