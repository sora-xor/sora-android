/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import java.util.TimeZone

data class DeviceParamsProvider(
    val screenWidth: Int,
    val screenHeight: Int,
    val model: String,
    val osVersion: String,
    val language: String,
    val country: String,
    val timezone: TimeZone
) {

    fun getCurrentTimeMillis() = System.currentTimeMillis()
}