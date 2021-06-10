/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

fun <K, V> Map<K, V>.inverseMap() = map { Pair(it.value, it.key) }.toMap()
