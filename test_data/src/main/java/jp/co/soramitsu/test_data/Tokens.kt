/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
