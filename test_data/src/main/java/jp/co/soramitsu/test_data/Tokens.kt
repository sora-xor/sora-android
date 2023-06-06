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

package jp.co.soramitsu.test_data

import jp.co.soramitsu.common.domain.Token

object TestTokens {

    val xorToken = Token(
        "0x0200000000000000000000000000000000000000000000000000000000000000",
        "Sora token",
        "XOR",
        18,
        true,
        null,
        null,
        null,
        null,
    )

    val valToken = Token(
        "0x0200040000000000000000000000000000000000000000000000000000000000",
        "validator",
        "VAL",
        18,
        true,
        null,
        null,
        null,
        null,
    )

    val pswapToken = Token(
        "0x0200050000000000000000000000000000000000000000000000000000000000",
        "pswap",
        "PSWAP",
        18,
        true,
        null,
        null,
        null,
        null,
    )

    val ethToken = Token(
        "0x0200060000000000000000000000000000000000000000000000000000000000",
        "ethereum",
        "ETH",
        18,
        true,
        null,
        null,
        null,
        null,
    )

    val xstusdToken = Token(
        "0x0200080000000000000000000000000000000000000000000000000000000000",
        "xst usd token",
        "XSTUSD",
        18,
        true,
        null,
        null,
        null,
        null,
    )

    val xstToken = Token(
        "0x0200090000000000000000000000000000000000000000000000000000000000",
        "SORA Synthetics",
        "XST",
        18,
        true,
        null,
        null,
        null,
        null,
    )

    val tbcdToken = Token(
        "0x0200090000000000000000000000000000000000000000000000000000000000",
        "SORA TBC Dollar",
        "TBCD",
        18,
        true,
        null,
        null,
        null,
        null,
    )

    val daiToken = Token(
        "0x020009",
        "dai stable",
        "DAI",
        18,
        true,
        null,
        null,
        null,
        null,
    )
}
