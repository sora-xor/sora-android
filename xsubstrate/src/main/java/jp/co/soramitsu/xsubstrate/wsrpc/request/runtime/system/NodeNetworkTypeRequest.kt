package jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.system

import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest

private const val METHOD = "system_chain"

class NodeNetworkTypeRequest : RuntimeRequest(METHOD, listOf())
