/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import timber.log.Timber

class WsLogger : Logger {
    override fun log(message: String?) = Timber.d("socket log = $message")

    override fun log(throwable: Throwable?) =
        Timber.e(throwable, "socket log error")
}
