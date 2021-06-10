/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.zxing.integration.android.IntentIntegrator
import com.tbruyelle.rxpermissions2.RxPermissions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.ChooserDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentContactsBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactsAdapter
import javax.inject.Inject

@SuppressLint("CheckResult")
class ContactsFragment :
    BaseFragment<ContactsViewModel>(R.layout.fragment_contacts),
    SearchView.OnQueryTextListener {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101
        private const val ARG_ASSET = "arg_asset"
        fun createBundle(assetId: String) = Bundle().apply {
            putString(ARG_ASSET, assetId)
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private lateinit var integrator: IntentIntegrator
    private var keyboardHelper: KeyboardHelper? = null
    private val viewBinding by viewBinding(FragmentContactsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        with(viewBinding.toolbar) {
            setRightActionClickListener {
                viewModel.qrMenuItemClicked()
            }
            setHomeButtonListener {
                if (keyboardHelper?.isKeyboardShowing == true) {
                    hideSoftKeyboard(activity)
                } else {
                    viewModel.backButtonPressed()
                }
            }
        }
        viewBinding.contactsSearchView.setOnQueryTextListener(this)
        initListeners()
    }

    private fun initListeners() {
        configureClicks()
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
            ChooserDialog(
                requireContext(),
                R.string.contacts_scan,
                getString(R.string.common_camera),
                getString(R.string.common_gallery),
                { viewModel.openCamera() },
                { viewModel.openGallery() }
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
        viewModel.getPreloadVisibility().observe {
            viewBinding.preloaderView.showOrGone(it)
        }
        viewModel.qrErrorLiveData.observe {
            showErrorFromResponse(it)
        }
    }

    private fun configureClicks() {
        integrator = IntentIntegrator.forSupportFragment(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt(getString(R.string.contacts_scan))
            setBeepEnabled(false)
        }

        viewModel.getContacts(true)
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .contactsComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(requireView())
    }

    override fun onPause() {
        super.onPause()
        keyboardHelper?.release()
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

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            viewModel.decodeTextFromBitmapQr(data.data!!)
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        hideSoftKeyboard(activity)
        viewModel.search(query.orEmpty())
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        if (newText?.isEmpty() == true) viewModel.search("")
        return true
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
}
