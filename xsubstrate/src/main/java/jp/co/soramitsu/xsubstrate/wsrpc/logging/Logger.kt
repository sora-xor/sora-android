package jp.co.soramitsu.xsubstrate.wsrpc.logging

interface Logger {
    fun log(message: String?)

    fun log(throwable: Throwable?)
}
