/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import androidx.annotation.Keep

@Keep
data class BlockResponse(val justification: Any?, val block: BlockEntry)

@Keep
data class BlockEntry(val header: Any?, val extrinsics: List<String>)
