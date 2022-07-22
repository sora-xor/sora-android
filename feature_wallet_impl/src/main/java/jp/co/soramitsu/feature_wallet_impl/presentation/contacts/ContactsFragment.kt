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
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.github.florent37.runtimepermission.RuntimePermission.askPermission
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentContactsBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactsAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.util.ScanQrBottomSheetDialog
import javax.inject.Inject

@AndroidEntryPoint
@SuppressLint("CheckResult")
class ContactsFragment :
    BaseFragment<ContactsViewModel>(R.layout.fragment_contacts),
    SearchView.OnQueryTextListener {

    companion object {
        private const val ARG_ASSET = "arg_asset"
        fun createBundle(assetId: String) = Bundle().apply {
            putString(ARG_ASSET, assetId)
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private var keyboardHelper: KeyboardHelper? = null
    private val viewBinding by viewBinding(FragmentContactsBinding::bind)

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
    override val viewModel: ContactsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        with(viewBinding.toolbar) {
            setRightActionClickListener {
                viewModel.qrMenuItemClicked()
            }
            setHomeButtonListener {
                if (keyboardHelper?.isKeyboardShowing == true) {
                    hideSoftKeyboard()
                } else {
                    viewModel.backButtonPressed()
                }
            }
        }
        viewBinding.contactsSearchView.setOnQueryTextListener(this)

        initListeners()
        initScanOptions()
    }

    private fun initScanOptions() {
        scanOptions.apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.contacts_scan))
            setBeepEnabled(false)
        }
    }

    private fun initListeners() {
        viewModel.contactsLiveData.observe {
            if (viewBinding.contactsRecyclerView.adapter == null) {
                viewBinding.contactsRecyclerView.layoutManager =
                    LinearLayoutManager(requireContext())
                viewBinding.contactsRecyclerView.adapter = ContactsAdapter(
                    debounceClickHandler,
                    { account ->
                        viewModel.contactClicked(
                            account.address,
                            requireArguments().getString(ARG_ASSET).orEmpty()
                        )
                    },
                    { item -> viewModel.menuItemClicked(item) },
                    { ethItem -> viewModel.ethItemClicked(ethItem) }
                )
            }

            (viewBinding.contactsRecyclerView.adapter as ContactsAdapter).submitList(it)
        }
        viewModel.chooseGallery.observe {
            selectQrFromGallery()
        }
        viewModel.initiateScanner.observe {
            initiateScan()
        }

        viewModel.showChooser.observe {
            ScanQrBottomSheetDialog(
                context = requireActivity(),
                uploadListener = { viewModel.openGallery() },
                cameraListener = { viewModel.openCamera() }
            ).show()
        }
        viewModel.emptyContactsVisibilityLiveData.observe {
            viewBinding.emptySearchResultPlaceholder.gone()
            viewBinding.emptyStatePlaceholder.showOrGone(it)
        }
        viewModel.emptySearchResultVisibilityLiveData.observe {
            viewBinding.emptyStatePlaceholder.gone()
            viewBinding.emptySearchResultPlaceholder.showOrGone(it)
        }
        viewModel.getProgressVisibility().observe {
            viewBinding.preloaderView.showOrGone(it)
        }
        viewModel.qrErrorLiveData.observe {
            showErrorFromResponse(it)
        }
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(requireView())
    }

    override fun onPause() {
        super.onPause()
        keyboardHelper?.release()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        hideSoftKeyboard()
        viewModel.search(query.orEmpty())
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        viewModel.search(newText.orEmpty())
        return true
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
