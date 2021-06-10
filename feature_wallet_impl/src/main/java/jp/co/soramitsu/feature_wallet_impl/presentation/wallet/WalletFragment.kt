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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.zxing.integration.android.IntentIntegrator
import com.tbruyelle.rxpermissions2.RxPermissions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.ChooserDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.dpRes2px
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentWalletBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.asset.AssetAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.asset.AssetsListSwipeController
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.eth.EthAssetActionsBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.HistoryLimitationsInfoBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.LockBottomSheetBehavior
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.RecentEventsAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import javax.inject.Inject

class WalletFragment : BaseFragment<WalletViewModel>(R.layout.fragment_wallet) {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var cma: ConnectionManager

    private lateinit var bottomSheetBehavior: LockBottomSheetBehavior<View>
    private lateinit var integrator: IntentIntegrator

    private var bottomSheetCollapsed = true

    private val itemListener: (SoraTransaction) -> Unit = {
        viewModel.eventClicked(it)
    }

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

        viewBinding.historyHelp.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                HistoryLimitationsInfoBottomSheet(requireContext()).show()
            }
        )

        bottomSheetBehavior = LockBottomSheetBehavior.fromView(viewBinding.recentEventsBottomSheet)

        bottomSheetBehavior.setBottomSheetCallback(object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    bottomSheetCollapsed = BottomSheetBehavior.STATE_COLLAPSED == newState
                    if (isAdded) viewBinding.swipeLayout.isEnabled = bottomSheetCollapsed
                }
            })

        viewBinding.swipeLayout.setOnRefreshListener {
            viewModel.refreshAssets()
        }

        viewBinding.recentEventsBottomSheet.doOnLayout {
            bottomSheetBehavior.peekHeight =
                viewBinding.titleTv.height + viewBinding.titleTv.top
        }

        bottomSheetBehavior.isFitToContents = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        viewBinding.eventRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.eventRecyclerView.adapter =
            RecentEventsAdapter(debounceClickHandler, itemListener)

        viewBinding.ibWalletMore.setDebouncedClickListener(debounceClickHandler) {
            viewModel.assetSettingsClicked()
        }

        viewBinding.ibWalletScan.setDebouncedClickListener(debounceClickHandler) {
            ChooserDialog(
                requireContext(),
                R.string.contacts_scan,
                getString(R.string.common_camera),
                getString(R.string.common_gallery),
                { viewModel.openCamera() },
                { viewModel.openGallery() }
            ).show()
        }

        val assetsIconShow = requireNotNull(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_eye_24
            )
        )
        val assetsIconHide = requireNotNull(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_eye_no_24
            )
        )
        ItemTouchHelper(
            AssetsListSwipeController(
                dpRes2px(R.dimen.x3),
                { pos -> if (pos != RecyclerView.NO_POSITION) viewModel.onAssetCardSwiped(pos) },
                { pos -> if (pos != RecyclerView.NO_POSITION) (viewBinding.assetsRv.adapter as AssetAdapter).allowToSwipe(pos) else false },
                { pos -> if (pos != RecyclerView.NO_POSITION) viewModel.onAssetCardSwipedPartly(pos) },
                { pos ->
                    if (pos != RecyclerView.NO_POSITION) {
                        if ((viewBinding.assetsRv.adapter as AssetAdapter).isHideIcon(pos)
                        ) assetsIconHide else assetsIconShow
                    } else {
                        null
                    }
                },
                { swiping -> viewBinding.swipeLayout.isEnabled = !swiping }
            )
        ).attachToRecyclerView(viewBinding.assetsRv)
        initListeners()
    }

    override fun onResume() {
        super.onResume()
        // todo - refactor it after coroutine migration
        viewModel.refreshAssets()
    }

    private fun initListeners() {
        viewModel.initiateGalleryChooserLiveData.observe(viewLifecycleOwner) {
            selectQrFromGallery()
        }

        viewModel.initiateScannerLiveData.observe(viewLifecycleOwner) {
            initiateScan()
        }

        viewModel.assetsLiveData.observe(viewLifecycleOwner) {
            if (viewBinding.assetsRv.adapter == null) {
                viewBinding.assetsRv.layoutManager = LinearLayoutManager(requireContext())
                viewBinding.assetsRv.adapter =
                    AssetAdapter(debounceClickHandler) { asset -> viewModel.assetClicked(asset) }
            }

            (viewBinding.assetsRv.adapter as AssetAdapter).submitList(it)
            viewBinding.contentContainer.doOnNextLayout {
                runCatching {
                    val ratio: Float =
                        viewBinding.contentContainer.height.toFloat() / viewBinding.pageContainer.height.toFloat()
                    bottomSheetBehavior.halfExpandedRatio =
                        if (ratio > 0.15 && ratio < 0.85) 1 - ratio else 0.5f
                    viewBinding.assetsRv.adapter?.let { adapter ->
                        if (adapter.itemCount > 0) {
                            if (viewBinding.assetsRv.height >= viewBinding.pageContainer.height - viewBinding.assetsRv.getChildAt(0).height * 1.4) {
                                viewBinding.assetsRv.updatePadding(bottom = (viewBinding.assetsRv.getChildAt(0).height * 1.4).toInt())
                            }
                        }
                    }
                }
            }
        }

        viewModel.transactionsModelLiveData.observe(viewLifecycleOwner) { transactions ->
            (viewBinding.eventRecyclerView.adapter as RecentEventsAdapter).submitList(transactions)
            if (transactions.isEmpty()) {
                viewBinding.placeholder.show()
                viewBinding.eventRecyclerView.gone()
            } else {
                viewBinding.placeholder.gone()
                viewBinding.eventRecyclerView.show()
            }
        }

        viewModel.hideSwipeProgressLiveData.observe(viewLifecycleOwner) {
            viewBinding.swipeLayout.isRefreshing = false
        }

        viewModel.showAddressBottomSheetEvent.observe(viewLifecycleOwner) {
            openAddressInfoView(it, viewModel)
        }

        viewModel.copiedAddressEvent.observe(viewLifecycleOwner) {
            Toast.makeText(requireActivity(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }

        viewModel.qrErrorLiveData.observe(viewLifecycleOwner) {
            showErrorFromResponse(it)
        }

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val firstVisiblePosition = (viewBinding.assetsRv.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()

                viewBinding.swipeLayout.isEnabled = firstVisiblePosition == 0
            }
        }

        viewBinding.assetsRv.addOnScrollListener(scrollListener)
    }

    private fun openAddressInfoView(params: String, viewModel: WalletViewModel) {
        val bottomSheet = EthAssetActionsBottomSheet(requireActivity(), params) {
            viewModel.copyAddressClicked()
        }
        bottomSheet.show()
    }

    private fun initiateScan() {
        RxPermissions(this)
            .request(Manifest.permission.CAMERA)
            .subscribe {
                if (it) integrator.initiateScan()
            }
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
}
