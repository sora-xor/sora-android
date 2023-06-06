/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.request

import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest

class StateKeys(params: List<Any>) : RuntimeRequest("state_getKeys", params)
class StateKeysPaged(params: List<Any>) : RuntimeRequest("state_getKeysPaged", params)
class StateQueryStorageAt(params: List<Any>) : RuntimeRequest("state_queryStorageAt", params)
