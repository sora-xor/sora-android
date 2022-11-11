/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
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
        assertEquals(viewModel.toolbarState.value?.title, "Title")
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