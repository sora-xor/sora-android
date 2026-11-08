package jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.author

import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest

private const val METHOD = "author_pendingExtrinsics"

class PendingExtrinsicsRequest : RuntimeRequest(METHOD, listOf())
