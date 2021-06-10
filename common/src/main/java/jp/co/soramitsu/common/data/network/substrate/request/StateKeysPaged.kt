package jp.co.soramitsu.common.data.network.substrate.request

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class StateKeysPaged(params: List<Any>) : RuntimeRequest("state_getKeysPaged", params)
class StateQueryStorageAt(params: List<Any>) : RuntimeRequest("state_queryStorageAt", params)
