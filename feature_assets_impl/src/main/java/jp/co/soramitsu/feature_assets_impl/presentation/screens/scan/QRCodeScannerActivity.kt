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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.scan

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.format.retrieveString
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.feature_assets_impl.databinding.QrCodeScannerLayoutBinding
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.scan.QrCodeScannerScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QRCodeScannerActivity : AppCompatActivity() {

    private val viewModel: QrCodeScannerViewModel by viewModels()

    private val binding by lazy { QrCodeScannerLayoutBinding.inflate(layoutInflater) }

    private var capture: CaptureManager? = null
    private val recipientOnly: Boolean get() = intent.getBooleanExtra(RECIPIENT_SCAN_EXTRA, false)

    private val startForResultFromGallery: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { resultUri: Uri? ->
            resultUri?.let { selectedImageUri ->
                viewModel.decodeScannedQrCodeUri(selectedImageUri)
            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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
        // The scanner is always dark (camera under a dark scrim), whatever the app theme: draw
        // edge to edge on every API level with transparent bars and light status/navigation
        // icons. AppTheme would give dark icons in light mode (and a light bar strip before
        // Android 15), which are unreadable over the camera.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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

        capture = IrohaAwareCaptureManager(barcodeScannerView).apply {
            initializeFromIntent(intent, savedInstanceState)
            if (recipientOnly) setShowMissingCameraPermissionDialog(false)
            decode()
        }

        startListeningForUpdates()

        binding.screenContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // The scanner always uses the dark SORA Neon palette: its surface is the camera
                // under a dark scrim, where light-theme text would be unreadable.
                SoraAppTheme(darkTheme = true) {
                    QrCodeScannerScreen(
                        onNavIconClick = ::finish,
                        onUploadFromGalleryClick = ::selectQrFromGallery,
                        onShowUserQrClick = ::finish,
                        recipientOnly = recipientOnly,
                    )
                }
            }
        }
    }

    private fun startListeningForUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                viewModel.qrCodeDecodedSharedFlow.collectLatest {
                    if (openIrohaConnect(it)) {
                        finish()
                        return@collectLatest
                    }
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
                    if (state.screenStatus == ScreenStatus.ERROR) {
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
        val pickVisualMediaRequest =
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        startForResultFromGallery.launch(pickVisualMediaRequest)
    }

    private fun openIrohaConnect(value: String): Boolean {
        val uri = runCatching { value.toUri() }.getOrNull() ?: return false
        if (!shouldOpenScannedConnect(recipientOnly, uri.scheme, uri.host)) {
            return false
        }
        return try {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(packageName),
            )
            true
        } catch (_: ActivityNotFoundException) {
            // No IrohaConnect screen in this build: hand the text back like any other scan.
            false
        }
    }

    private inner class IrohaAwareCaptureManager(
        barcodeView: DecoratedBarcodeView,
    ) : CaptureManager(this@QRCodeScannerActivity, barcodeView) {
        override fun returnResult(rawResult: BarcodeResult) {
            if (openIrohaConnect(rawResult.text)) {
                finish()
            } else {
                super.returnResult(rawResult)
            }
        }
    }

}
