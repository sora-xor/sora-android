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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.receiverequest

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.ScrollState
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.journeyapps.barcodescanner.ScanOptions
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.ShareUtil
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SelectSearchTokenScreen
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.QrCodeMainScreen
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.RequestTokenConfirmScreen
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QRCodeScannerActivity
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.ScanTextContract

@AndroidEntryPoint
class QRCodeFlowFragment : SoraBaseFragment<QRCodeFlowViewModel>() {

    override val viewModel: QRCodeFlowViewModel by viewModels()

    private val barcodeScanOptions by lazy {
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("")
            .setBeepEnabled(false)
            .setCaptureActivity(QRCodeScannerActivity::class.java)
    }

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> =
        registerForActivityResult(ScanTextContract()) { result ->
            result?.let {
                viewModel.onReceiveQRCodeScanUriResult(it)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        parseFragmentArguments()

        viewModel.shareQrCodeEvent.observe { (qrCodeFileUri, message) ->
            context?.let { context ->
                ShareUtil.shareImageFile(
                    context = context,
                    title = getString(R.string.common_share),
                    file = qrCodeFileUri,
                    description = message
                )
            }
        }
    }

    private fun parseFragmentArguments() =
        with(arguments) {
            if (this == null)
                return@with

            if (getBoolean(NAVIGATE_TO_SCANNER_DIRECTLY_KEY))
                barcodeLauncher.launch(barcodeScanOptions)
        }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(route = QRCodeFlowRoute.MainScreen.route) {
            val qrState = viewModel.receiveTokenByQrScreenState.collectAsStateWithLifecycle()
            val screenState = viewModel.requestTokenScreenState.collectAsStateWithLifecycle()
            QrCodeMainScreen(
                scrollState = scrollState,
                receiveTokenByQrScreenState = qrState.value,
                requestTokenScreenState = screenState.value,
                receiveToken_onUserAddressClick = viewModel::onUserAddressClickInReceiveScreen,
                receiveToken_onScanQrClick = {
                    PermissionX.init(this@QRCodeFlowFragment)
                        .permissions(Manifest.permission.CAMERA)
                        .request { allGranted, _, _ ->
                            if (allGranted) {
                                barcodeLauncher.launch(barcodeScanOptions)
                            }
                        }
                },
                receiveToken_onShareCodeClick = viewModel::onShareQrCodeInReceiveScreen,
                receiveToken_onTryAgainClick = viewModel::onLoadReceiveScreenDataAgainClick,
                requestToken_onUserAddressClick = viewModel::onUserAddressClickInReceiveScreen,
                requestToken_onAmountChanged = viewModel::onRequestAmountChange,
                requestToken_onTokenSelect = {
                    navController.navigate(QRCodeFlowRoute.SelectToken.route)
                },
                requestToken_onCreateRequestClick = {
                    viewModel.onLoadRequestConfirmScreenDataAgainClick()
                    navController.navigate(QRCodeFlowRoute.ConfirmRequestByQRCode.route)
                },
                requestToken_onTryAgainClick = viewModel::onLoadRequestScreenDataClick,
            )
        }

        composable(route = QRCodeFlowRoute.SelectToken.route) {
            val filter = viewModel.selectTokenScreenState.collectAsStateWithLifecycle()
            SelectSearchTokenScreen(
                scrollState = scrollState,
                onAssetSelect = {
                    viewModel.onSelectToken(it)
                    navController.popBackStack()
                },
                searchTokenFilter = filter.value,
            )
        }

        composable(route = QRCodeFlowRoute.ConfirmRequestByQRCode.route) {
            val state = viewModel.requestTokenConfirmScreenState.collectAsStateWithLifecycle()
            RequestTokenConfirmScreen(
                scrollState = scrollState,
                state = state.value,
                onUserAddressClick = viewModel::onUserAddressClickInRequestConfirmScreen,
                onShareCodeClick = viewModel::onShareQrCodeInRequestConfirmScreen,
                onTryAgainClick = viewModel::onLoadRequestConfirmScreenDataAgainClick,
            )
        }
    }

    companion object {

        const val NAVIGATE_TO_SCANNER_DIRECTLY_KEY = "navigate_to_scanner_activity"

        fun createBundle(
            shouldNavigateToScanner: Boolean = false,
        ) = Bundle().apply {
            putBoolean(NAVIGATE_TO_SCANNER_DIRECTLY_KEY, shouldNavigateToScanner)
        }
    }
}
