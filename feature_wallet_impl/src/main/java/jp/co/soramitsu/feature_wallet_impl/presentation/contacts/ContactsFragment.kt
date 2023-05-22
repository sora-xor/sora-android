/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.github.florent37.runtimepermission.RuntimePermission
import com.google.accompanist.navigation.animation.composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.args.addressOrEmpty
import jp.co.soramitsu.common.presentation.args.tokenId
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
@SuppressLint("CheckResult")
class ContactsFragment : SoraBaseFragment<ContactsViewModel>() {

    private val scanOptions = ScanOptions()
    private val processQrFromGalleryContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) { result ->
            if (result != null) {
                viewModel.decodeTextFromBitmapQr(result)
            }
        }
    private val processQrFromCameraContract = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.qrResultProcess(result.contents)
        }
    }
    override val viewModel: ContactsViewModel by viewModels()

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            var qrSelection by remember { mutableStateOf(false) }
            val onQrClick: () -> Unit = {
                qrSelection = true
            }
            val onContactSelect: (String) -> Unit = {
                viewModel.onContactClick(
                    accountId = it,
                    tokenId = requireArguments().tokenId
                )
            }
            if (qrSelection) {
                ModalQrSelectionDialog(
                    onFromGallery = {
                        qrSelection = false
                        processQrFromGalleryContract.launch("image/*")
                    },
                    onFromCamera = {
                        qrSelection = false
                        RuntimePermission.askPermission(
                            this@ContactsFragment,
                            Manifest.permission.CAMERA
                        ).onAccepted {
                            processQrFromCameraContract.launch(scanOptions)
                        }.ask()
                    },
                    onDismiss = { qrSelection = false },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2)
            ) {
                val state = viewModel.state
                ContactsScreen(
                    inputTextState = state.input,
                    onValueChanged = viewModel::search,
                    hint = state.hint,
                    isMyAddress = state.myAddress,
                    accounts = state.accounts,
                    isSearchEntered = state.isSearchEntered,
                    onScanClick = onQrClick,
                    onCloseSearchClick = viewModel::onCloseSearchClicked,
                    onAccountClick = onContactSelect,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        scanOptions.apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.contacts_scan))
            setBeepEnabled(false)
        }

        if (requireArguments().addressOrEmpty.isNotEmpty()) {
            viewModel.search(TextFieldValue(text = requireArguments().addressOrEmpty))
        }
    }
}
