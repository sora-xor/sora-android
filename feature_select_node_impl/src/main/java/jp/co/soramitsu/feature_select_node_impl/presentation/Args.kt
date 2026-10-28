/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
