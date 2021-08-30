package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import by.kirich1409.viewbindingdelegate.viewBinding
import com.github.florent37.runtimepermission.RuntimePermission.askPermission
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.zxing.integration.android.IntentIntegrator
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.chooserbottomsheet.ChooserBottomSheet
import jp.co.soramitsu.common.presentation.view.chooserbottomsheet.ChooserItem
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentAssetDetailsBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.HistoryAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.LockBottomSheetBehavior
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class AssetDetailsFragment : BaseFragment<AssetDetailsViewModel>(R.layout.fragment_asset_details) {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101
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
    private lateinit var integrator: IntentIntegrator
    private var accountDetailsBottomSheet: AccountDetailsBottomSheet? = null
    private var assetIdBottomSheet: AssetIdBottomSheet? = null

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var bottomSheetBehavior: LockBottomSheetBehavior<View>

    private val viewBinding by viewBinding(FragmentAssetDetailsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        progressDialog = SoraProgressDialog(requireActivity())

        integrator = IntentIntegrator.forSupportFragment(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt(getString(R.string.contacts_scan))
            setBeepEnabled(false)
        }

        viewBinding.toolbar.setHomeButtonListener { viewModel.backClicked() }

        bottomSheetBehavior = LockBottomSheetBehavior.fromView(viewBinding.recentEventsBottomSheet)

        bottomSheetBehavior.isFitToContents = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        viewBinding.sendCard.setDebouncedClickListener(debounceClickHandler) {
            viewModel.sendClicked()
        }

        viewBinding.receiveCard.setDebouncedClickListener(debounceClickHandler) {
            viewModel.receiveClicked()
        }

        viewBinding.scanCard.setDebouncedClickListener(debounceClickHandler) {
            ChooserBottomSheet(
                requireActivity(),
                R.string.qr_code,
                listOf(
                    ChooserItem(
                        R.string.qr_upload,
                        R.drawable.ic_gallery_24
                    ) { viewModel.openGallery() },
                    ChooserItem(
                        R.string.contacts_scan,
                        R.drawable.ic_scan_24
                    ) { viewModel.openCamera() }
                )
            ).show()
        }

        viewBinding.toolbar.setRightActionClickListener {
            viewModel.userIconClicked()
        }

        viewBinding.title.setDebouncedClickListener(debounceClickHandler) {
            viewModel.titleClicked()
        }

        viewBinding.tvFrozenValue.setDebouncedClickListener(debounceClickHandler) {
            viewModel.frozenClicked()
        }

        viewBinding.tvFrozenTitle.setDebouncedClickListener(debounceClickHandler) {
            viewModel.frozenClicked()
        }

        viewBinding.eventRecyclerView.adapter =
            HistoryAdapter(debounceClickHandler) {
                viewModel.eventClicked(it)
            }

        initListeners()
    }

    override fun inject() {
        val assetId = requireArguments().getString(KEY_ASSET_ID, "")

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .assetDetailsComponentBuilder()
            .withFragment(this)
            .withAssetId(assetId)
            .build()
            .inject(this)
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

        viewModel.assetIcon.observe(viewLifecycleOwner) {
            viewBinding.toolbar.setTitleIcon(it)
        }

        viewModel.userIcon.observe(viewLifecycleOwner) {
            viewBinding.toolbar.setRightIconDrawable(it)
        }

        viewModel.assetSymbolTitle.observe(viewLifecycleOwner) {
            viewBinding.toolbar.setTitle(it)
        }

        viewModel.assetNameTitle.observe(viewLifecycleOwner) {
            viewBinding.title.text = it
        }

        viewModel.totalBalanceLiveData.observe(viewLifecycleOwner) {
            viewBinding.tvTotalValue.text = it
        }

        viewModel.transferableBalanceLiveData.observe(viewLifecycleOwner) {
            viewBinding.tvTransferableTitle.show()
            viewBinding.tvTransferableValue.show()
            viewBinding.divider2.show()
            viewBinding.tvTransferableValue.text = it
        }

        viewModel.frozenBalanceLiveData.observe(viewLifecycleOwner) {
            viewBinding.tvFrozenTitle.show()
            viewBinding.tvFrozenValue.show()
            viewBinding.divider3.show()
            viewBinding.tvFrozenValue.text = it
        }

        viewModel.initiateGalleryChooserLiveData.observe(viewLifecycleOwner) {
            selectQrFromGallery()
        }

        viewModel.initiateScannerLiveData.observe(viewLifecycleOwner) {
            initiateScan()
        }

        viewModel.accountDetailsLiveData.observe(viewLifecycleOwner) {
            accountDetailsBottomSheet =
                AccountDetailsBottomSheet(requireActivity(), it.second, it.first, it.third) {
                    viewModel.addressCopyClicked()
                }

            accountDetailsBottomSheet!!.show()
        }

        viewModel.assetDetailsLiveData.observe(viewLifecycleOwner) {
            assetIdBottomSheet = AssetIdBottomSheet(requireActivity(), it) {
                viewModel.assetIdCopyClicked()
            }

            assetIdBottomSheet!!.show()
        }

        viewModel.copiedAddressEvent.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
            accountDetailsBottomSheet?.dismiss()
            assetIdBottomSheet?.dismiss()
        }

        viewModel.frozenBalanceDialogEvent.observe(viewLifecycleOwner) {
            BalanceDetailsBottomSheet(
                requireActivity(),
                it
            ).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.transactionsFlow.collectLatest {
                    viewBinding.eventRecyclerView.adapter.safeCast<HistoryAdapter>()?.submitData(it)
                }
            }
        }

        viewBinding.eventRecyclerView.adapter.safeCast<HistoryAdapter>()?.let { adapter ->
            adapter.addLoadStateListener { loadState ->
                if (loadState.source.append.endOfPaginationReached || loadState.append.endOfPaginationReached) {
                    if (adapter.itemCount < 1) {
                        viewBinding.placeholder.show()
                    } else {
                        viewBinding.placeholder.hide()
                    }
                }
            }
        }
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
}
