package jp.co.soramitsu.xsubstrate.wsrpc.request

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

typealias SendAction = () -> Unit

class RequestExecutor(private val executor: ExecutorService = Executors.newSingleThreadExecutor()) {
    private val futures = mutableListOf<Future<*>>()

    fun execute(action: SendAction) {
        var future: Future<*>? = null

        future =
            executor.submit {
                action()

                futures.remove(future)
            }

        futures += future
    }

    fun reset() {
        val currentFutures = ArrayList(futures)
        futures.clear()
        currentFutures.forEach { it.cancel(true) }
        currentFutures.clear()
    }
}
