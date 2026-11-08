package jp.co.soramitsu.xsubstrate.wsrpc.exception

class ConnectionClosedException : Exception() {
    override fun toString(): String = javaClass.simpleName
}
