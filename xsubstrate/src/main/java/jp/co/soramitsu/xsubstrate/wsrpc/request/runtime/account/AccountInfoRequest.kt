package jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.account

import jp.co.soramitsu.xsubstrate.runtime.Module
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.GetStorageRequest

class AccountInfoRequest(publicKey: ByteArray) : GetStorageRequest(
    listOf(
        Module.System.Account.storageKey(publicKey),
    ),
)
