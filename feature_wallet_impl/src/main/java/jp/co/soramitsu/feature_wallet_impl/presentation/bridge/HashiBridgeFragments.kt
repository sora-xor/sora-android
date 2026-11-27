package jp.co.soramitsu.feature_wallet_impl.presentation.bridge

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.feature_main_api.domain.model.PIN_CONFIRMED_ACTION_KEY
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction

/** Hands a PIN confirmation of [PinCodeAction.CONFIRM_BRIDGE] to [onConfirmed], once. */
private fun androidx.fragment.app.Fragment.observeBridgePin(onConfirmed: () -> Unit) {
    findNavController().currentBackStackEntry?.savedStateHandle?.let { handle ->
        handle.getLiveData<String?>(PIN_CONFIRMED_ACTION_KEY).observe(viewLifecycleOwner) { action ->
            if (action == PinCodeAction.CONFIRM_BRIDGE.name) {
                handle[PIN_CONFIRMED_ACTION_KEY] = null
                onConfirmed()
            }
        }
    }
}

@AndroidEntryPoint
class HashiBridgeFragment : SoraBaseFragment<HashiBridgeViewModel>() {

    override val viewModel: HashiBridgeViewModel by viewModels()

    override val explainsReadOnlyAccount: Boolean = true

    override fun NavGraphBuilder.content(scrollState: ScrollState, navController: NavHostController) {
        composable(route = theOnlyRoute) {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            androidx.compose.foundation.layout.Box(modifier = Modifier.verticalScroll(scrollState)) {
                HashiBridgeScreen(
                    state = state,
                    onRefresh = viewModel::refreshAvailability,
                    onDirection = viewModel::onDirection,
                    onOpenPicker = viewModel::onOpenPicker,
                    onClosePicker = viewModel::onClosePicker,
                    onSelectAsset = viewModel::onSelectAsset,
                    onAmount = viewModel::onAmount,
                    onMax = viewModel::onMax,
                    onRecipient = viewModel::onRecipient,
                    onUseOwnAddress = viewModel::onUseOwnAddress,
                    onCopyAddress = viewModel::onCopyAddress,
                    onContinue = viewModel::onContinue,
                    onConfirm = viewModel::onConfirm,
                    onDismissConfirm = viewModel::onDismissConfirm,
                    onHistoryItem = viewModel::onHistoryItem,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        observeBridgePin(viewModel::onPinConfirmed)
    }

    companion object {
        fun createBundle(assetId: String?) = Bundle().apply {
            if (assetId != null) putString(HashiBridgeViewModel.ARG_ASSET_ID, assetId)
        }
    }
}

@AndroidEntryPoint
class HashiTransferFragment : SoraBaseFragment<HashiTransferViewModel>() {

    override val viewModel: HashiTransferViewModel by viewModels()

    override fun NavGraphBuilder.content(scrollState: ScrollState, navController: NavHostController) {
        composable(route = theOnlyRoute) {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            androidx.compose.foundation.layout.Box(modifier = Modifier.verticalScroll(scrollState)) {
                HashiTransferScreen(
                    state = state,
                    onAction = viewModel::onPrimaryAction,
                    onSpeedUp = viewModel::onSpeedUp,
                    onCopy = viewModel::onCopy,
                    onConfirm = viewModel::onConfirm,
                    onDismissConfirm = viewModel::onDismissConfirm,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        observeBridgePin(viewModel::onPinConfirmed)
    }

    companion object {
        fun createBundle(transferId: String) = Bundle().apply {
            putString(HashiTransferViewModel.ARG_TRANSFER_ID, transferId)
        }
    }
}
