package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class HealthChecker(private val cm: ConnectionManager) {

    private val health = MutableStateFlow<Boolean?>(null)

    fun connectionErrorHandled() {
        health.value = false
    }

    fun connectionStable() {
        health.value = true
    }

    fun observeHealthState(): Flow<Boolean> = cm.connectionState()
}
