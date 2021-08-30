/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data

import com.google.gson.Gson
import jp.co.soramitsu.common.domain.Serializer
import java.lang.reflect.Type

class GsonSerializerImpl(
    private val gson: Gson
) : Serializer {

    override fun serialize(input: Any): String {
        return gson.toJson(input)
    }

    override fun <T> deserialize(input: String, classOfT: Class<T>): T {
        return gson.fromJson<T>(input, classOfT)
    }

    override fun <T> deserialize(input: String, typeOfT: Type): T? {
        return gson.fromJson<T>(input, typeOfT)
    }
}
