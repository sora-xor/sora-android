package jp.co.soramitsu.common.data.network.substrate

import android.annotation.SuppressLint
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine

@SuppressLint("CheckResult")
class WsConnectionManager(private val socket: SocketService, appStateProvider: AppStateProvider, private val healthChecker: HealthChecker) : ConnectionManager {

    init {
        appStateProvider.observeState()
            .distinctUntilChanged()
            .subscribe {
                if (it) {
                    start(SubstrateNetworkOptionsProvider.url)
                } else {
                    stop()
                }
            }
        socket.addStateObserver {
            when (it) {
                is SocketStateMachine.State.Connected -> healthChecker.connectionStable()
                else -> healthChecker.connectionErrorHandled()
            }
        }
    }

    override fun start(url: String) = socket.start(url)

    override fun isStarted(): Boolean = socket.started()

    override fun stop() = socket.stop()
}
