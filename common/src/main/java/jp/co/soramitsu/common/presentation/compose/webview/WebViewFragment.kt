/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.webview

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import javax.inject.Inject

@AndroidEntryPoint
class WebViewFragment : SoraBaseFragment<WebViewViewModel>() {

    @Inject
    lateinit var vmf: WebViewViewModel.WebViewViewModelFactory

    override val viewModel: WebViewViewModel by viewModels {
        WebViewViewModel.provideFactory(
            vmf,
            requireArguments().title,
            requireArguments().url
        )
    }

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        WebView(viewModel)
    }
}
