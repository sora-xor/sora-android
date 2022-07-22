/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import by.kirich1409.viewbindingdelegate.viewBinding
import com.github.florent37.runtimepermission.RuntimePermission.askPermission
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.setBalance
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.view.LoadMoreListener
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentAssetDetailsBinding
import jp.co.soramitsu.feature_wallet_impl.domain.HistoryState
import jp.co.soramitsu.feature_wallet_impl.presentation.util.ScanQrBottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.HistoryAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.LockBottomSheetBehavior
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AssetDetailsFragment : BaseFragment<AssetDetailsViewModel>(R.layout.fragment_asset_details) {

    companion object {
        private const val KEY_ASSET_ID = "assetId"

        fun createBundle(
            assetId: String
        ): Bundle {
            return Bundle().apply {
                putString(KEY_ASSET_ID, assetId)
            }
        }
    }

    private lateinit var progressDialog: SoraProgressDialog
    private var accountDetailsBottomSheet: AccountDetailsBottomSheet? = null
    private var assetIdBottomSheet: AssetIdBottomSheet? = null
    private var assetIcon: Int? = null
    private val balanceStyle = AssetBalanceStyle(
        intStyle = R.style.TextAppearance_Soramitsu_Neu_Regular_18,
        decStyle = R.style.TextAppearance_Soramitsu_Neu_Regular_14
    )

    private val processQrFromCameraContract = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.qrResultProcess(result.contents)
        }
    }
    private val scanOptions = ScanOptions()

    private val processQrFromGalleryContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) { result ->
            if (result != null) {
                viewModel.decodeTextFromBitmapQr(result)
            }
        }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var vmf: AssetDetailsViewModel.AssetDetailsViewModelFactory

    override val viewModel: AssetDetailsViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(requireArguments().getString(KEY_ASSET_ID, ""))
        }
    }

    private lateinit var bottomSheetBehavior: LockBottomSheetBehavior<View>

    private val viewBinding by viewBinding(FragmentAssetDetailsBinding::bind)

    private var adapter: HistoryAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        progressDialog = SoraProgressDialog(requireActivity())

        scanOptions.apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.contacts_scan))
            setBeepEnabled(false)
        }

        viewBinding.tbAssetDetails.setHomeButtonListener { viewModel.backClicked() }

        bottomSheetBehavior = LockBottomSheetBehavior.fromView(viewBinding.recentEventsBottomSheet)

        bottomSheetBehavior.expandedOffset = 30
        bottomSheetBehavior.isFitToContents = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        viewBinding.ibAssetDetailsSend.setDebouncedClickListener(debounceClickHandler) {
            viewModel.sendClicked()
        }

        viewBinding.ibAssetDetailsReceive.setDebouncedClickListener(debounceClickHandler) {
            viewModel.receiveClicked()
        }

        viewBinding.ibAssetDetailsScan.setDebouncedClickListener(debounceClickHandler) {
            ScanQrBottomSheetDialog(
                context = requireActivity(),
                uploadListener = { viewModel.openGallery() },
                cameraListener = { viewModel.openCamera() }
            ).show()
        }

        viewBinding.tbAssetDetails.setRightActionClickListener {
            viewModel.userIconClicked()
        }

        viewBinding.ivAssetDetailsIcon.setDebouncedClickListener(debounceClickHandler) {
            viewModel.titleClicked()
        }

        viewBinding.tvFrozenValue.setDebouncedClickListener(debounceClickHandler) {
            viewModel.frozenClicked()
        }

        viewBinding.tvFrozenTitle.setDebouncedClickListener(debounceClickHandler) {
            viewModel.frozenClicked()
        }

        adapter = HistoryAdapter(debounceClickHandler) {
            viewModel.eventClicked(it)
        }
        viewBinding.eventRecyclerView.adapter = adapter
        ContextCompat.getDrawable(
            viewBinding.eventRecyclerView.context,
            R.drawable.line_ver_divider
        )?.let {
            viewBinding.eventRecyclerView.addItemDecoration(
                DividerItemDecoration(
                    viewBinding.eventRecyclerView.context,
                    DividerItemDecoration.VERTICAL
                ).apply {
                    setDrawable(it)
                }
            )
        }

        initListeners()
    }

    private fun initListeners() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        viewModel.getProgressVisibility().observe {
            if (it) {
                progressDialog.show()
            } else {
                progressDialog.dismiss()
                viewBinding.contentContainer.doOnNextLayout {
                    runCatching {
                        val ratio: Float =
                            viewBinding.contentContainer.height.toFloat() / viewBinding.pageContainer.height.toFloat()
                        bottomSheetBehavior.halfExpandedRatio = if (ratio > 0.15 && ratio < 0.85)
                            1 - ratio
                        else
                            0.5f
                    }
                }
            }
        }

        viewModel.assetIcon.observe {
            assetIcon = it
            viewBinding.ivAssetDetailsIcon.setImageResource(it)
        }

        viewModel.userIcon.observe {
            viewBinding.tbAssetDetails.setRightIconDrawable(it, true)
        }

        viewModel.assetSymbolTitle.observe {
            viewBinding.tvAssetDetailsTicker.text = it
        }

        viewModel.assetNameTitle.observe {
            viewBinding.title.text = it
        }

        viewModel.totalBalanceLiveData.observe {
            viewBinding.tvTotalValue.setBalance(
                AssetBalanceData(
                    amount = it,
                    style = AssetBalanceStyle(
                        intStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_18,
                        decStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_14
                    )
                )
            )
        }

        viewModel.transferableBalanceLiveData.observe {
            viewBinding.tvTransferableTitle.show()
            viewBinding.tvTransferableValue.show()
            viewBinding.divider2.show()
            viewBinding.tvTransferableValue.setBalance(
                AssetBalanceData(
                    amount = it,
                    style = balanceStyle
                )
            )
        }

        viewModel.frozenBalanceLiveData.observe {
            viewBinding.tvFrozenTitle.show()
            viewBinding.tvFrozenValue.show()
            viewBinding.divider3.show()
            viewBinding.tvFrozenValue.setBalance(
                AssetBalanceData(
                    amount = it,
                    style = balanceStyle
                )
            )
        }

        viewModel.initiateGalleryChooserLiveData.observe {
            selectQrFromGallery()
        }

        viewModel.qrErrorLiveData.observe {
            showErrorFromResponse(it)
        }

        viewModel.initiateScannerLiveData.observe {
            initiateScan()
        }

        viewModel.accountDetailsLiveData.observe {
            accountDetailsBottomSheet =
                AccountDetailsBottomSheet(requireActivity(), it.second, it.third) {
                    viewModel.addressCopyClicked()
                }

            accountDetailsBottomSheet?.show()
        }

        viewModel.assetDetailsLiveData.observe {
            assetIdBottomSheet = AssetIdBottomSheet(requireActivity(), it, assetIcon) {
                viewModel.assetIdCopyClicked()
            }
            assetIdBottomSheet?.show()
        }

        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
            accountDetailsBottomSheet?.dismiss()
            assetIdBottomSheet?.dismiss()
        }

        viewModel.frozenBalanceDialogEvent.observe {
            BalanceDetailsBottomSheet(
                requireActivity(),
                it
            ).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyState.collectLatest {
                    when (it) {
                        HistoryState.Error -> {
                            viewBinding.grEmptyHistory.hide()
                            if (adapter?.itemsCount() == 0) {
                                viewBinding.transactionsUnavailablePlaceholder.container.show()
                            }
                        }
                        is HistoryState.History -> {
                            viewBinding.grEmptyHistory.hide()
                            viewBinding.transactionsUnavailablePlaceholder.container.hide()
                            val list = if (it.endReached) it.events else buildList {
                                addAll(it.events)
                                add(EventUiModel.EventUiLoading)
                            }
                            adapter?.update(list)
                            viewBinding.eventRecyclerView.clearOnScrollListeners()
                            if (it.endReached.not()) {
                                viewBinding.eventRecyclerView.addOnScrollListener(object :
                                        LoadMoreListener() {
                                        override fun onLoadMore(elements: Int) {
                                            viewModel.onMoreHistoryEventsRequested()
                                        }
                                    })
                            }
                        }
                        HistoryState.Loading -> {
                            viewBinding.grEmptyHistory.hide()
                        }
                        HistoryState.NoData -> {
                            adapter?.update(emptyList())
                            viewBinding.grEmptyHistory.show()
                            viewBinding.transactionsUnavailablePlaceholder.container.hide()
                        }
                    }
                }
            }
        }

//        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
//            viewModel.transactionsFlow.collectLatest {
//                adapter?.submitData(it)
//            }
//        }
//
//        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
//            adapter?.loadStateFlow?.collectLatest {
//                viewBinding.transactionsUnavailablePlaceholder.container.showOrHide(it.refresh is LoadState.Error && adapter?.itemCount == 0)
//                val emptyList =
//                    it.refresh is LoadState.NotLoading && it.refresh !is LoadState.Error && adapter?.itemCount == 0
//                viewBinding.grEmptyHistory.showOrHide(emptyList)
//            }
//        }
    }

    private fun initiateScan() {
        askPermission(this, Manifest.permission.CAMERA).onAccepted {
            processQrFromCameraContract.launch(scanOptions)
        }.ask()
    }

    private fun selectQrFromGallery() {
        processQrFromGalleryContract.launch("image/*")
    }
}
