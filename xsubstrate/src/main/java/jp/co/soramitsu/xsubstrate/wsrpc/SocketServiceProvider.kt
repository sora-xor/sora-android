package jp.co.soramitsu.xsubstrate.wsrpc

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.xsubstrate.wsrpc.logging.Logger
import jp.co.soramitsu.xsubstrate.wsrpc.recovery.Reconnector
import jp.co.soramitsu.xsubstrate.wsrpc.request.RequestExecutor

object SocketServiceProvider {
    private val gson by lazy { Gson() }

    fun getSocketService(logger: Logger = getEmptyLogger()): SocketService {
        return SocketService(
            gson,
            logger,
            WebSocketFactory(),
            Reconnector(),
            RequestExecutor(),
        )
    }

    private fun getEmptyLogger(): Logger {
        return object : Logger {
            override fun log(message: String?) = Unit

            override fun log(throwable: Throwable?) {
                throwable?.printStackTrace()
            }
        }
    }
}
