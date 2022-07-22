/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class ResourceManagerTest {

    @Test
    fun `getting string`() {
        val expectedString = "some string"

        val context = mock(Context::class.java)
        given(context.getString(R.string.activity_project)).willReturn(expectedString)

        val resourceManager = ResourceManager(context)

        assertEquals(expectedString, resourceManager.getString(R.string.activity_project))
    }

    @Test
    fun `getting color`() {
        val expectedColor = Color.RED

        val context = mock(Context::class.java)

        val resources = mock(Resources::class.java)
        given(context.resources).willReturn(resources)
        given(resources.getColor(R.color.backgroundGrey)).willReturn(expectedColor)

        val resourceManager = ResourceManager(context)

        assertEquals(expectedColor, resourceManager.getColor(R.color.backgroundGrey))
    }

    @Test
    fun `getting quantity string`() {
        val quantity = 2
        val expectedString = "someString"

        val context = mock(Context::class.java)

        val resources = mock(Resources::class.java)
        given(context.resources).willReturn(resources)
        given(resources.getQuantityString(R.string.activity_project, quantity)).willReturn(expectedString)

        val resourceManager = ResourceManager(context)

        assertEquals(expectedString, resourceManager.getQuantityString(R.string.activity_project, quantity))
    }
}