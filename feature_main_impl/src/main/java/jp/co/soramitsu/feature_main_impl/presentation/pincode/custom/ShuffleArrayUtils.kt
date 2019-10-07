/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.custom

import java.util.Random

object ShuffleArrayUtils {

    fun shuffle(array: IntArray): IntArray {
        val length = array.size
        val random = Random()
        random.nextInt()

        for (i in 0 until length) {
            val change = i + random.nextInt(length - i)
            swap(array, i, change)
        }
        return array
    }

    private fun swap(array: IntArray, index: Int, change: Int) {
        val temp = array[index]
        array[index] = array[change]
        array[change] = temp
    }
}