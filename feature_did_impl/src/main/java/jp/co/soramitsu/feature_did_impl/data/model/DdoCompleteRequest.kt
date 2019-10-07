/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.data.model

import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import okhttp3.RequestBody
import java.security.KeyPair

data class DdoCompleteRequest(
    val requestBody: RequestBody,
    val userDdoSigned: DDO,
    val keys: KeyPair
)