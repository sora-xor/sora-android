/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.data.model

import java.security.KeyPair

data class RetrieveDdoCompleteRequest(
    val userDid: String,
    val keys: KeyPair
)