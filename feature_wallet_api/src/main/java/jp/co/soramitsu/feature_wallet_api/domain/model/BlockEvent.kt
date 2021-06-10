package jp.co.soramitsu.feature_wallet_api.domain.model

data class BlockEvent(val module: Int, val event: Int, val number: Long?)
