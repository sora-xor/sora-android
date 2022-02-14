/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.github.florent37.runtimepermission.RuntimePermission.askPermission
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.zxing.integration.android.IntentIntegrator
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentWalletBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.util.ScanQrBottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.asset.AssetAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.EventsLoadAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.HistoryAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.LockBottomSheetBehavior
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

class WalletFragment : BaseFragment<WalletViewModel>(R.layout.fragment_wallet) {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var bottomSheetBehavior: LockBottomSheetBehavior<View>
    private lateinit var integrator: IntentIntegrator

    private var bottomSheetCollapsedOrHalfExpanded = true

    private val viewBinding by viewBinding(FragmentWalletBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .walletSubComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    private lateinit var historyAdapter: HistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as BottomBarController).showBottomBar()

        integrator = IntentIntegrator.forSupportFragment(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
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
            historyAdapter.refresh()
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
            .withLoadStateFooter(
                footer = EventsLoadAdapter()
            )
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

        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewModel.transactionsFlow.collectLatest {
                historyAdapter.submitData(it)
            }
        }
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            historyAdapter.loadStateFlow.collectLatest {
                viewBinding.swipeLayout.isRefreshing = it.mediator?.refresh is LoadState.Loading
                viewBinding.transactionsUnavailablePlaceholder.container.showOrHide(it.refresh is LoadState.Error && historyAdapter.itemCount == 0)
                val emptyList =
                    it.refresh is LoadState.NotLoading && it.refresh !is LoadState.Error && historyAdapter.itemCount == 0
                viewBinding.grEmptyHistoryWallet.showOrHide(emptyList)
            }
        }
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            historyAdapter.loadStateFlow
                .distinctUntilChangedBy { it.refresh }
                .filter { it.refresh is LoadState.NotLoading }
                .collect { viewBinding.eventRecyclerView.scrollToPosition(0) }
        }

        viewModel.showSwipeProgressLiveData.observe(viewLifecycleOwner) {
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
            integrator.initiateScan()
        }.ask()
    }

    private fun selectQrFromGallery() {
        val intent = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }

        startActivityForResult(
            Intent.createChooser(
                intent,
                getString(R.string.common_options_title)
            ),
            PICK_IMAGE_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                viewModel.qrResultProcess(result.contents)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            viewModel.decodeTextFromBitmapQr(data.data!!)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAssets()
    }
}
