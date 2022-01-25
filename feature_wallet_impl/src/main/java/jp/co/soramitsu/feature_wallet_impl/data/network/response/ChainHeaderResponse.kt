/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.response

import androidx.annotation.Keep

@Keep
class ChainHeaderResponse(val parentHash: String, val number: String, val stateRoot: String, val extrinsicsRoot: String)
