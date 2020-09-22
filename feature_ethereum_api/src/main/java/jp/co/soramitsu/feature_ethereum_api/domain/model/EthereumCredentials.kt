/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_api.domain.model

import java.math.BigInteger

data class EthereumCredentials(
    val privateKey: BigInteger
)