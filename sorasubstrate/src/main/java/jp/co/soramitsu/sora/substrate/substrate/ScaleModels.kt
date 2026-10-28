/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.xsubstrate.scale.Schema
import jp.co.soramitsu.xsubstrate.scale.compactInt
import jp.co.soramitsu.xsubstrate.scale.sizedByteArray
import jp.co.soramitsu.xsubstrate.scale.uint128
import jp.co.soramitsu.xsubstrate.scale.uint32
import jp.co.soramitsu.xsubstrate.scale.vector

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
    val claimedRewards by vector(jp.co.soramitsu.xsubstrate.scale.datatype.uint32)
}

object UnlockChunk : Schema<UnlockChunk>() {
    val value by compactInt()
    val era by compactInt()
}

object ActiveEraInfo : Schema<ActiveEraInfo>() {
    val index by uint32()
}
