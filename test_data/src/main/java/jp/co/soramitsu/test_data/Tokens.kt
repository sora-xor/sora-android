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
        0
    )

    val valToken = Token(
        "0x0004",
        "validator",
        "VAL",
        18,
        true,
        0
    )

    val pswapToken = Token(
        "0x0005",
        "pswap",
        "PSWAP",
        18,
        true,
        0
    )

    val ethToken = Token(
        "0x0006",
        "ethereum",
        "ETH",
        18,
        true,
        0
    )

    val xstToken = Token(
        "0x0008",
        "xst usd token",
        "XSTUSD",
        18,
        true,
        0
    )

    val daiToken = Token(
        "0x0009",
        "dai stable",
        "DAI",
        18,
        true,
        0
    )
}
