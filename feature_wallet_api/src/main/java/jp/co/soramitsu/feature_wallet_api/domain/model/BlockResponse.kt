/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

class BlockResponse(val justification: Any?, val block: BlockEntry)

class BlockEntry(val header: Any?, val extrinsics: List<String>)
