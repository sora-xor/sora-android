/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.did.network.model

import java.security.KeyPair

data class RetrieveDdoCompleteRequest(
    val userDid: String,
    val keys: KeyPair
)