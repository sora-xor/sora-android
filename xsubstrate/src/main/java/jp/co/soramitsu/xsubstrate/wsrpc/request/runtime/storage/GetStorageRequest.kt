package jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage

import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest

open class GetStorageRequest(keys: List<String>) : RuntimeRequest(
    method = "state_getStorage",
    keys,
)
