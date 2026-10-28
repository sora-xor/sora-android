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

package jp.co.soramitsu.test_data

import jp.co.soramitsu.common.domain.Token

object TestTokens {

    val kxorToken = Token(
        "0x02000e0000000000000000000000000000000000000000000000000000000000",
        "Kensetsu xor token",
        "KXOR",
        18,
        true,
        null,
        null,
        null,
        null,
    )

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
        "0x0200070000000000000000000000000000000000000000000000000000000000",
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

    val xstxau = Token(
        "0x0300458ad89700876ec191924c6f5a8d01e10000000000000000000000000000",
        "Gold",
        "XSTXAU",
        18,
        true,
        null,
        null,
        null,
        null,
    )

    val daiToken = Token(
        "0x0200060000000000000000000000000000000000000000000000000000000000",
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
