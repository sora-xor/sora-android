/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
import com.tbruyelle.rxpermissions2.RxPermissions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.ChooserDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_contacts.contactsSearchView
import kotlinx.android.synthetic.main.fragment_contacts.preloaderView
import kotlinx.android.synthetic.main.fragment_contacts.toolbar

@SuppressLint("CheckResult")
class ContactsFragment : BaseFragment<ContactsViewModel>(), SearchView.OnQueryTextListener {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101

        private const val KEY_BALANCE = "balance"

        @JvmStatic
        fun start(balance: String, navController: NavController) {
            val bundle = Bundle().apply {
                putString(KEY_BALANCE, balance)
            }
            navController.navigate(R.id.contactsFragment, bundle)
        }
    }

    private lateinit var integrator: IntentIntegrator
    private lateinit var emptyStateView: View
    private lateinit var emptySearchResultView: View
    private lateinit var contactsRecyclerView: RecyclerView

    private var keyboardHelper: KeyboardHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }

    override fun initViews() {
        toolbar.setTitle(getString(R.string.send_to_contacts_title))
        toolbar.setHomeButtonListener {
            if (keyboardHelper?.isKeyboardShowing == true) {
                hideSoftKeyboard(activity)
            } else {
                viewModel.backButtonPressed()
            }
        }

        (activity as MainActivity).hideBottomView()

        emptyStateView = view!!.findViewById(R.id.emptyStatePlaceholder)
        emptySearchResultView = view!!.findViewById(R.id.emptySearchResultPlaceholder)
        contactsRecyclerView = view!!.findViewById(R.id.contactsRecyclerView)

        contactsSearchView.setOnQueryTextListener(this)
    }

    override fun subscribe(viewModel: ContactsViewModel) {
        configureClicks()

        observe(viewModel.contactsLiveData, Observer {
            if (contactsRecyclerView.adapter == null) {
                contactsRecyclerView.layoutManager = LinearLayoutManager(activity!!)
                contactsRecyclerView.adapter = ContactsAdapter(
                    { viewModel.contactClicked(it.accountId, "${it.firstName} ${it.lastName}", arguments!!.getString(KEY_BALANCE)) },
                    { viewModel.menuItemClicked(it) }
                )
            }

            (contactsRecyclerView.adapter as ContactsAdapter).submitList(it)
        })

        observe(viewModel.initiateGalleryChooserLiveData, Observer {
            if (it.getContentIfNotHandled() != null) {
                selectQrFromGallery()
            }
        })

        observe(viewModel.initiateScannerLiveData, EventObserver {
            initiateScan()
        })

        observe(viewModel.showChooserEvent, EventObserver {
            ChooserDialog(
                activity!!,
                R.string.qr_dialog_title,
                R.array.qr_dialog_array,
                { viewModel.openCamera() },
                { viewModel.openGallery() }
            ).show()
        })

        observe(viewModel.emptyContactsVisibilityLiveData, Observer {
            emptySearchResultView.gone()

            if (it) {
                emptyStateView.show()
            } else {
                emptyStateView.gone()
            }
        })

        observe(viewModel.emptySearchResultVisibilityLiveData, Observer {
            emptyStateView.gone()

            if (it) {
                emptySearchResultView.show()
            } else {
                emptySearchResultView.gone()
            }
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) {
                preloaderView.show()
            } else {
                preloaderView.gone()
            }
        })
    }

    private fun configureClicks() {
        integrator = IntentIntegrator.forSupportFragment(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES)
            setPrompt(getString(R.string.scan_qr))
            setBeepEnabled(false)
        }

        viewModel.getContacts(false, true)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .contactsComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(view!!)
    }

    override fun onPause() {
        super.onPause()
        keyboardHelper?.release()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(activity, R.string.scan_canceled, Toast.LENGTH_LONG).show()
            } else {
                viewModel.qrResultProcess(result.contents, arguments!!.getString(KEY_BALANCE, ""))
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            viewModel.decodeTextFromBitmapQr(data.data!!, arguments!!.getString(KEY_BALANCE, ""))
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        hideSoftKeyboard(activity)
        viewModel.search(query!!)
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
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

        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_qr)), PICK_IMAGE_REQUEST)
    }
}