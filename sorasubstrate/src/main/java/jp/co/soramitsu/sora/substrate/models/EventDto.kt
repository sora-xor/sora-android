/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.models

import java.math.BigInteger

data class EventRecord(val phase: PhaseRecord, val event: InnerEventRecord)

data class InnerEventRecord(val moduleIndex: Int, val eventIndex: Int, var list: List<Any?>? = null)

sealed class PhaseRecord {

    class ApplyExtrinsic(val extrinsicId: BigInteger) : PhaseRecord()

    object Finalization : PhaseRecord()

    object Initialization : PhaseRecord()
}

data class BlockEvent(val module: Int, val event: Int, val number: Long?)
