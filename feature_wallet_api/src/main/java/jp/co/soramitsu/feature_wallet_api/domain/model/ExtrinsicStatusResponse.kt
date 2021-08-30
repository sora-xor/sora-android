/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

sealed class ExtrinsicStatusResponse(val subscription: String) {
    class ExtrinsicStatusPending(subscription: String) : ExtrinsicStatusResponse(subscription)
    class ExtrinsicStatusFinalized(subscription: String, val inBlock: String) : ExtrinsicStatusResponse(subscription)
}
