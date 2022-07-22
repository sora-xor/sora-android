/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.github.florent37.runtimepermission.RuntimePermission.askPermission
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.view.LoadMoreListener
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentWalletBinding
import jp.co.soramitsu.feature_wallet_impl.domain.HistoryState
import jp.co.soramitsu.feature_wallet_impl.presentation.util.ScanQrBottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.asset.AssetAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.HistoryAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.LockBottomSheetBehavior
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WalletFragment : BaseFragment<WalletViewModel>(R.layout.fragment_wallet) {

    companion object {
        private const val HALF_MINUTE_IN_MS = 30000L
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var bottomSheetBehavior: LockBottomSheetBehavior<View>

    private var bottomSheetCollapsedOrHalfExpanded = true

    private val viewBinding by viewBinding(FragmentWalletBinding::bind)

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

    private val vm: WalletViewModel by viewModels()
    override val viewModel: WalletViewModel
        get() = vm

    private lateinit var historyAdapter: HistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as BottomBarController).showBottomBar()

        scanOptions.apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.contacts_scan))
            setBeepEnabled(false)
        }

        viewBinding.ibWalletSend.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.sendButtonClicked()
            }
        )

        viewBinding.ibWalletReceive.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.receiveButtonClicked()
            }
        )

        bottomSheetBehavior = LockBottomSheetBehavior.fromView(viewBinding.recentEventsBottomSheet)

        bottomSheetBehavior.setBottomSheetCallback(object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    bottomSheetCollapsedOrHalfExpanded =
                        BottomSheetBehavior.STATE_COLLAPSED == newState || BottomSheetBehavior.STATE_HALF_EXPANDED == newState
                    if (isAdded) viewBinding.swipeLayout.isEnabled = bottomSheetCollapsedOrHalfExpanded
                }
            })

        viewBinding.swipeLayout.setOnRefreshListener {
            viewModel.refreshAssets()
        }

        viewBinding.recentEventsBottomSheet.doOnLayout {
            bottomSheetBehavior.peekHeight =
                (viewBinding.bottomSheetPin.height + viewBinding.bottomSheetPin.top) * 4
        }
        bottomSheetBehavior.expandedOffset = 30
        bottomSheetBehavior.isFitToContents = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        historyAdapter = HistoryAdapter(debounceClickHandler) {
            viewModel.eventClicked(it)
        }
        viewBinding.eventRecyclerView.adapter = historyAdapter
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

        viewBinding.ibWalletMore.setDebouncedClickListener(debounceClickHandler) {
            viewModel.assetSettingsClicked()
        }

        viewBinding.ibWalletScan.setDebouncedClickListener(debounceClickHandler) {
            ScanQrBottomSheetDialog(
                context = requireActivity(),
                uploadListener = { viewModel.openGallery() },
                cameraListener = { viewModel.openCamera() }
            ).show()
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.curSoraAccount.observe {
            viewModel.refreshAssets()
        }

        viewModel.initiateGalleryChooserLiveData.observe {
            selectQrFromGallery()
        }

        viewModel.initiateScannerLiveData.observe {
            initiateScan()
        }

        viewModel.assetsLiveData.observe {
            if (viewBinding.assetsRv.adapter == null) {
                viewBinding.assetsRv.layoutManager = LinearLayoutManager(requireContext())
                viewBinding.assetsRv.adapter =
                    AssetAdapter(debounceClickHandler) { asset -> viewModel.assetClicked(asset) }
            }

            (viewBinding.assetsRv.adapter as AssetAdapter).submitList(it)
            viewBinding.contentContainer.doOnNextLayout {
                runCatching {
                    val ratio: Float =
                        (
                            viewBinding.assetsRv.height + viewBinding.title.height + resources.getDimension(
                                R.dimen.x3
                            )
                            ) / viewBinding.pageContainer.height.toFloat()
                    bottomSheetBehavior.halfExpandedRatio =
                        if (ratio > 0.15 && ratio < 0.85) 1 - ratio else 0.5f
                    viewBinding.assetsRv.adapter?.let { adapter ->
                        if (adapter.itemCount > 0) {
                            if (viewBinding.assetsRv.height >= viewBinding.pageContainer.height - viewBinding.assetsRv.getChildAt(
                                    0
                                ).height * 1.4
                            ) {
                                viewBinding.assetsRv.updatePadding(
                                    bottom = (
                                        viewBinding.assetsRv.getChildAt(
                                            0
                                        ).height * 1.4
                                        ).toInt()
                                )
                            }
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyState.collectLatest {
                    when (it) {
                        HistoryState.Error -> {
                            viewBinding.swipeLayout.isRefreshing = false
                            if (historyAdapter.itemsCount() == 0) {
                                viewBinding.transactionsUnavailablePlaceholder.container.show()
                            }
                        }
                        HistoryState.Loading -> {
                            viewBinding.swipeLayout.isRefreshing = true
                        }
                        HistoryState.NoData -> {
                            viewBinding.swipeLayout.isRefreshing = false
                            viewBinding.grEmptyHistoryWallet.show()
                            viewBinding.transactionsUnavailablePlaceholder.container.hide()
                            historyAdapter.update(emptyList())
                        }
                        is HistoryState.History -> {
                            viewBinding.swipeLayout.isRefreshing = false
                            viewBinding.grEmptyHistoryWallet.hide()
                            viewBinding.transactionsUnavailablePlaceholder.container.hide()
                            val list = if (it.endReached) it.events else buildList {
                                addAll(it.events)
                                add(EventUiModel.EventUiLoading)
                            }
                            historyAdapter.update(list)
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
                    }
                }
            }
        }

        viewModel.showSwipeProgressLiveData.observe {
            viewBinding.swipeLayout.isRefreshing = true
        }

        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireActivity(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }

        viewModel.qrErrorLiveData.observe {
            showErrorFromResponse(it)
        }

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val firstVisiblePosition =
                    (viewBinding.assetsRv.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()

                viewBinding.swipeLayout.isEnabled = firstVisiblePosition == 0
            }
        }

        viewBinding.assetsRv.addOnScrollListener(scrollListener)
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
