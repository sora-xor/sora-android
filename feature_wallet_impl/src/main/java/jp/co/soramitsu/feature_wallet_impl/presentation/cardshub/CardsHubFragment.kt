/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsList
import jp.co.soramitsu.common_wallet.presentation.compose.states.BuyXorState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoriteAssetsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.SoraCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.ModalQrSelectionDialog
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.oauth.base.sdk.signin.SoraCardSignInContract
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class CardsHubFragment : SoraBaseFragment<CardsHubViewModel>() {

    override val viewModel: CardsHubViewModel by viewModels()

    private val soraCardSignIn = registerForActivityResult(
        SoraCardSignInContract()
    ) { result ->
        when (result) {
            is SoraCardResult.Failure -> {
            }
            is SoraCardResult.Success -> {
                viewModel.updateSoraCardInfo(
                    result.accessToken,
                    result.refreshToken,
                    result.accessTokenExpirationTime,
                    result.status.toString(),
                )
            }
            is SoraCardResult.Canceled -> {
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()
        scanOptions.apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.contacts_scan))
            setBeepEnabled(false)
        }

        viewModel.launchSoraCardSignIn.observe { contractData ->
            soraCardSignIn.launch(contractData)
        }
    }

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
            if (qrSelection) {
                ModalQrSelectionDialog(
                    onFromGallery = {
                        qrSelection = false
                        processQrFromGalleryContract.launch("image/*")
                    },
                    onFromCamera = {
                        qrSelection = false
                        RuntimePermission.askPermission(
                            this@CardsHubFragment,
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
            ) {
                val state = viewModel.state
                TopBar(
                    account = state.curAccount,
                    onAccountClick = viewModel::onAccountClick,
                    onQrClick = onQrClick,
                )
                Spacer(modifier = Modifier.size(size = 16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = Dimens.x2)
                ) {
                    state.cards.forEach { cardState ->
                        when (cardState) {
                            is TitledAmountCardState -> {
                                CommonHubCard(
                                    title = cardState.title,
                                    amount = cardState.amount,
                                    onExpandClick = cardState.onExpandClick,
                                    collapseState = cardState.collapsedState,
                                    onCollapseClick = cardState.onCollapseClick
                                ) {
                                    when (cardState.state) {
                                        is FavoriteAssetsCardState -> AssetsCard(
                                            cardState.state as FavoriteAssetsCardState,
                                            viewModel::onAssetClick,
                                        )
                                        is FavoritePoolsCardState -> PoolsList(
                                            (cardState.state as FavoritePoolsCardState).state,
                                            viewModel::onPoolClick,
                                        )
                                    }
                                }
                            }

                            is SoraCardState -> {
                                AnimatedVisibility(
                                    visible = cardState.visible
                                ) {
                                    SoraCard(
                                        state = cardState,
                                        onCardStateClicked = viewModel::onCardStateClicked,
                                        onCloseClicked = viewModel::onRemoveSoraCard
                                    )
                                }
                            }

                            is BuyXorState -> {
                                BuyXorCard(
                                    visible = cardState.visible,
                                    onBuyXorClicked = viewModel::onBuyCrypto,
                                    onCloseCard = viewModel::onRemoveBuyXorToken
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(size = 16.dp))
                    }
                }
            }
        }
    }
}
