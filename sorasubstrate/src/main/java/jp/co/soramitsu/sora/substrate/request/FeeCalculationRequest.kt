/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.request

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class FeeCalculationRequest(submittableExtrinsic: String) : RuntimeRequest(
    method = "payment_queryInfo",
    params = listOf(submittableExtrinsic)
)

class FeeCalculationRequest2(extrinsicInHex: String) : RuntimeRequest(
    method = "payment_queryFeeDetails",
    params = listOf(extrinsicInHex)
)
