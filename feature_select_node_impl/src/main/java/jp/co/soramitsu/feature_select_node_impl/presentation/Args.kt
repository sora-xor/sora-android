/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation

import android.os.Bundle

private const val NODE_NAME_KEY = "NODE_NAME_KEY"
var Bundle.nodeName: String?
    get() = this.getString(NODE_NAME_KEY)
    set(value) = this.putString(NODE_NAME_KEY, value)

private const val NODE_ADDRESS_KEY = "NODE_ADDRESS_KEY"
var Bundle.nodeAddress: String?
    get() = this.getString(NODE_ADDRESS_KEY)
    set(value) = this.putString(NODE_ADDRESS_KEY, value)

private const val PIN_CODE_CHECK_KEY = "PIN_CODE_CHECK_KEY"
var Bundle.pinCodeChecked: Boolean
    get() = this.getBoolean(PIN_CODE_CHECK_KEY, false)
    set(value) = this.putBoolean(PIN_CODE_CHECK_KEY, value)
