package jp.co.soramitsu.feature_wallet_impl.util

import jp.co.soramitsu.common.data.network.substrate.SubstrateNetworkOptionsProvider
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object SubmittableUtils {

    sealed class Era {
        object Immortal : Era()
        class Mortal(val period: Byte, val phase: Byte) : Era()
    }

    fun getMortalEraPeriodAndPhase(currentBlockNumber: Int, periodInBlocks: Int = SubstrateNetworkOptionsProvider.mortalEraLength): Pair<Int, Int> {
        var calPeriod = 2.0.pow(ceil(log2(periodInBlocks.toDouble()))).toInt()
        calPeriod = min(1 shl 16, max(calPeriod, 4))
        val phase = currentBlockNumber % calPeriod
        val quantizeFactor = max(1, calPeriod shr 12)
        val quantizePhase = phase / quantizeFactor * quantizeFactor
        return Pair(calPeriod, quantizePhase)
    }

    fun encodeMortalEra(period: Int, phase: Int): Pair<Byte, Byte> {
        val quantizeFactor = max(1, period shr 12)
        val trailingZeros = period.countTrailingZeroBits()
        val encoded = min(15, max(1, trailingZeros - 1)) + (((phase / quantizeFactor) shl 4))
        val first = encoded shr 8
        val second = encoded and 0xff
        return Pair(second.toByte(), first.toByte())
    }

    fun decodeMortalEra(v1: Int, v2: Int): Pair<Int, Int> {
        val encoded = v1 + (v2 shl 8)
        val period = 2 shl (encoded % (1 shl 4))
        val quantizeFactor = max(period shr 12, 1)
        val phase = (encoded shr 4) * quantizeFactor
        return Pair(period, phase)
    }

    fun getEraMortal(currentBlock: Int): Era.Mortal =
        getMortalEraPeriodAndPhase(currentBlock).let {
            encodeMortalEra(it.first, it.second).let { p ->
                Era.Mortal(p.first, p.second)
            }
        }
}
