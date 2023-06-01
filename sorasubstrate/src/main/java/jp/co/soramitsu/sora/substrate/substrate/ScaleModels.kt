/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.compactInt
import jp.co.soramitsu.fearless_utils.scale.sizedByteArray
import jp.co.soramitsu.fearless_utils.scale.uint128
import jp.co.soramitsu.fearless_utils.scale.uint32
import jp.co.soramitsu.fearless_utils.scale.vector

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
    val claimedRewards by vector(jp.co.soramitsu.fearless_utils.scale.dataType.uint32)
}

object UnlockChunk : Schema<UnlockChunk>() {
    val value by compactInt()
    val era by compactInt()
}

object ActiveEraInfo : Schema<ActiveEraInfo>() {
    val index by uint32()
}
