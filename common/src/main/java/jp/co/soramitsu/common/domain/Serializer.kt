/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import java.lang.reflect.Type

interface Serializer {

    fun serialize(input: Any): String

    fun <T> deserialize(input: String, classOfT: Class<T>): T?

    fun <T> deserialize(input: String, typeOfT: Type): T?
}