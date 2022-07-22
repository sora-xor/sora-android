/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.response

import androidx.annotation.Keep

@Keep
class ChainHeaderResponse(val parentHash: String, val number: String, val stateRoot: String, val extrinsicsRoot: String)
