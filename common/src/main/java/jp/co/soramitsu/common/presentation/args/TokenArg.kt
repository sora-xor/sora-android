/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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

private const val IS_LAUNCHED_FROM_SORA_CARD = "IS_LAUNCHED_FROM_SORA_CARD"
var Bundle.isLaunchedFromSoraCard: Boolean
    get() = this.requireBoolean(IS_LAUNCHED_FROM_SORA_CARD)
    set(value) = this.putBoolean(IS_LAUNCHED_FROM_SORA_CARD, value)

private const val TOKEN_ID = "TOKEN_ID"
var Bundle.tokenId: String
    get() = this.requireString(TOKEN_ID)
    set(value) = this.putString(TOKEN_ID, value)
