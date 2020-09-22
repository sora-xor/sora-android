/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class GsonSerializerTest {

    private lateinit var gsonSerializerImpl: GsonSerializerImpl

    private val gson = mock(Gson::class.java)

    private val inputValue = ""
    private val jsonValue = "{}"

    @Before fun setup() {
        gsonSerializerImpl = GsonSerializerImpl(gson)
    }

    @Test fun `serialize called`() {
        given(gson.toJson(inputValue)).willReturn(jsonValue)

        val actual = gsonSerializerImpl.serialize(inputValue)

        assertEquals(jsonValue, actual)
    }

    @Test fun `deserialize called`() {
        given(gson.fromJson(jsonValue, String::class.java)).willReturn(inputValue)

        val actual = gsonSerializerImpl.deserialize(jsonValue, String::class.java)

        assertEquals(actual, inputValue)
    }

    @Test fun `deserialize type called`() {
        given(gson.fromJson<String>(jsonValue, object : TypeToken<List<String>>() {}.type)).willReturn(inputValue)

        val actual = gsonSerializerImpl.deserialize<String>(jsonValue, object : TypeToken<List<String>>() {}.type)

        assertEquals(actual, inputValue)
    }
}