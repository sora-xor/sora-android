/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.compactInt
import jp.co.soramitsu.shared_utils.scale.sizedByteArray
import jp.co.soramitsu.shared_utils.scale.uint128
import jp.co.soramitsu.shared_utils.scale.uint32
import jp.co.soramitsu.shared_utils.scale.vector

object ReservesResponse : Schema<ReservesResponse>() {
    val first by uint128()
    val second by uint128()
}

object PoolPropertiesResponse : Schema<PoolPropertiesResponse>() {
    val first by sizedByteArray(32)
    val second by sizedByteArray(32)
}

object TotalIssuance : Schema<TotalIssuance>() {
    val totalIssuance by uint128()
}

object PoolProviders : Schema<PoolProviders>() {
    val poolProviders by uint128()
}

object StakingLedger : Schema<StakingLedger>() {
    val stash by sizedByteArray(32)
    val total by compactInt()
    val active by compactInt()
    val unlocking by vector(UnlockChunk)
    val claimedRewards by vector(jp.co.soramitsu.shared_utils.scale.dataType.uint32)
}

object UnlockChunk : Schema<UnlockChunk>() {
    val value by compactInt()
    val era by compactInt()
}

object ActiveEraInfo : Schema<ActiveEraInfo>() {
    val index by uint32()
}
