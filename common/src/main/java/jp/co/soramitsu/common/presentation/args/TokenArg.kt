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

package jp.co.soramitsu.common.presentation.args

import android.os.Bundle
import jp.co.soramitsu.common.domain.Token

private const val TOKEN_FROM_KEY = "TOKEN_FROM"
var Bundle.tokenFromNullable: Token?
    get() = getParcelableKey(TOKEN_FROM_KEY)
    set(value) = this.putParcelable(TOKEN_FROM_KEY, value)

var Bundle.tokenFrom: Token
    get() = requireNotNull(getParcelableKey(TOKEN_FROM_KEY))
    set(value) = this.putParcelable(TOKEN_FROM_KEY, value)

private const val TOKEN_TO_KEY = "TOKEN_TO"
var Bundle.tokenToNullable: Token?
    get() = getParcelableKey(TOKEN_TO_KEY)
    set(value) = this.putParcelable(TOKEN_TO_KEY, value)

var Bundle.tokenTo: Token
    get() = requireNotNull(getParcelableKey(TOKEN_TO_KEY))
    set(value) = this.putParcelable(TOKEN_TO_KEY, value)

private const val TOKEN_FROM_ID = "TOKEN_FROM_ID"
var Bundle.tokenFromId: String
    get() = this.requireString(TOKEN_FROM_ID)
    set(value) = this.putString(TOKEN_FROM_ID, value)

private const val TOKEN_TO_ID = "TOKEN_TO_ID"
var Bundle.tokenToId: String
    get() = this.requireString(TOKEN_TO_ID)
    set(value) = this.putString(TOKEN_TO_ID, value)

private const val TOKEN_ID = "TOKEN_ID"
var Bundle.tokenId: String
    get() = this.requireString(TOKEN_ID)
    set(value) = this.putString(TOKEN_ID, value)
