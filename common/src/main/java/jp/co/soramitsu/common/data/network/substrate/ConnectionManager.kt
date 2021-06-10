package jp.co.soramitsu.common.data.network.substrate

interface ConnectionManager {

    fun start(url: String)

    fun isStarted(): Boolean

    fun stop()
}
