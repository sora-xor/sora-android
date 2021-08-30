package jp.co.soramitsu.common.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CoroutineManager {
    val applicationScope = CoroutineScope(SupervisorJob())
    val io = Dispatchers.IO
}
