/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.presentation.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.presentation.compose.webview.WebViewViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class WebViewViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    private lateinit var viewModel: WebViewViewModel

    @Before
    fun setUp() {
        viewModel = WebViewViewModel(
            title = "Title",
            url = "Url"
        )
    }

    @Test
    fun `init viewModel EXPECT set up title to state`() {
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals("Title", s.basic.title)
    }

    @Test
    fun `init viewModel EXPECT set up url to state`() {
        assertEquals(viewModel.state.url, "Url")
    }

    @Test
    fun `init viewModel EXPECT loading is true`() {
        assertTrue(viewModel.state.loading)
    }

    @Test
    fun `init viewModel EXPECT javaScriptEnabled is true`() {
        assertTrue(viewModel.state.javaScriptEnabled)
    }

    @Test
    fun `page finished EXPECT loading is false`() {
        viewModel.onPageFinished()

        assertFalse(viewModel.state.loading)
    }
}
