/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.scan

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.CaptureManager
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.feature_assets_impl.databinding.QrCodeScannerLayoutBinding
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.scan.QrCodeScannerScreen
import jp.co.soramitsu.feature_assets_impl.presentation.screens.receiverequest.QRCodeFlowViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QRCodeScannerActivity : AppCompatActivity() {

    private val binding by lazy { QrCodeScannerLayoutBinding.inflate(layoutInflater) }

    private var capture: CaptureManager? = null

    private val viewModel: QRCodeFlowViewModel by viewModels()

    private val startForResultFromGallery: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { selectedImageUri ->
                    viewModel.decodeScannedQrCodeUri(selectedImageUri)
                }
            }
        }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return binding.zxingBarcodeScanner.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture?.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initScreen(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        capture?.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture?.onDestroy()
    }

    private fun initScreen(savedInstanceState: Bundle?) {
        val barcodeScannerView = binding.zxingBarcodeScanner.apply {
            viewFinder?.setMaskColor(Color.TRANSPARENT)
            viewFinder?.setLaserVisibility(false)
        }

        capture = CaptureManager(this, barcodeScannerView).apply {
            initializeFromIntent(intent, savedInstanceState)
            decode()
        }

        startListeningForUpdates()

        binding.screenContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    QrCodeScannerScreen(
                        state = viewModel.qrCodeScannerScreenState,
                        onNavIconClick = ::finish,
                        onUploadFromGalleryClick = ::selectQrFromGallery,
                        onShowUserQrClick = ::finish
                    )
                }
            }
        }
    }

    private fun startListeningForUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                viewModel.qrCodeDecodedSharedFlow.collectLatest {
                    val resultIntent = Intent().apply {
                        putExtra(Intents.Scan.RESULT, it)
                    }

                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                snapshotFlow { viewModel.qrCodeScannerScreenState }.collectLatest { state ->
                    if (state.isErrorTextVisible) {
                        Toast.makeText(
                            this@QRCodeScannerActivity,
                            state.errorText.retrieveString(
                                context = this@QRCodeScannerActivity
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun selectQrFromGallery() {
        val intent = Intent().apply {
            type = QR_CODE_IMAGE_TYPE
            action = Intent.ACTION_GET_CONTENT
        }
        startForResultFromGallery.launch(intent)
    }

    private companion object {
        const val QR_CODE_IMAGE_TYPE = "image/*"
    }
}
