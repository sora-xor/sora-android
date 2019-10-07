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
import android.view.View.OVER_SCROLL_NEVER
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
import com.jakewharton.rxbinding2.view.RxView
import com.tbruyelle.rxpermissions2.RxPermissions
import jp.co.soramitsu.account_information_list.list.models.Card
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.ChooserDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.contact_entry_list.list.ContactItemDecoration
import jp.co.soramitsu.contact_entry_list.list.ContactsSection
import jp.co.soramitsu.contact_entry_list.list.models.ContactItem
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.core_ui.presentation.list.BaseListAdapter
import jp.co.soramitsu.core_ui.presentation.list.Section
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import kotlinx.android.synthetic.main.fragment_contacts.contactsSearchView
import kotlinx.android.synthetic.main.fragment_contacts.toolbar

@SuppressLint("CheckResult")
class ContactsFragment : BaseFragment<ContactsViewModel>(), SearchView.OnQueryTextListener {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101

        private const val BALANCE = "balance"

        @JvmStatic
        fun start(balance: String, navController: NavController) {
            val bundle = Bundle()
            bundle.putString(BALANCE, balance)
            navController.navigate(R.id.contactsFragment, bundle)
        }
    }

    private lateinit var integrator: IntentIntegrator
    private lateinit var emptyStateView: View
    private lateinit var emptySearchResultView: View
    private lateinit var ethWithdrawalView: View
    private lateinit var contactsRecyclerView: RecyclerView

    private var keyboardHelper: KeyboardHelper? = null

    private val itemListener: (ContactItem) -> Unit = {
        viewModel.contactClicked(it.accountId, it.name, arguments!!.getString(BALANCE))
    }

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
        ethWithdrawalView = view!!.findViewById(R.id.contactEthHeader)

        ethWithdrawalView.gone()

        contactsSearchView.setOnQueryTextListener(this)
    }

    override fun subscribe(viewModel: ContactsViewModel) {
        configureClicks()

        observe(viewModel.fetchContactsResultLiveData, Observer {
            if (it.isNotEmpty()) {
                showAccounts(it)
            } else {
                showEmptyContacts()
            }
        })

        observe(viewModel.searchResultLiveData, Observer {
            if (it.isNotEmpty()) {
                showAccounts(it)
            } else {
                showEmptySearchResult()
            }
        })

        observe(viewModel.initiateGalleryChooserLiveData, Observer {
            if (it.getContentIfNotHandled() != null) {
                selectQrFromGallery()
            }
        })

        observe(viewModel.initiateScannerLiveData, Observer {
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
    }

    private fun configureClicks() {
        val qrButton = view!!.findViewById<LinearLayout>(R.id.contactQrHeader)

        integrator = IntentIntegrator.forSupportFragment(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES)
        integrator.setPrompt(getString(R.string.scan_qr))

        viewModel.fetchContacts(false, true)

        RxView.clicks(qrButton)
            .subscribe {
                viewModel.showImageChooser()
            }

        RxView.clicks(ethWithdrawalView)
            .subscribe { viewModel.ethWithdrawalClicked(arguments!!.getString(BALANCE, "")) }
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
                viewModel.qrResultProcess(result.contents, arguments!!.getString(BALANCE, ""))
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            viewModel.decodeTextFromBitmapQr(activity!!, data.data!!, arguments!!.getString(BALANCE, ""))
        }
    }

    private var sectionAdapter: BaseListAdapter = BaseListAdapter()

    private fun showAccounts(accounts: List<Account>) {
        sectionAdapter.removeAllSections()

        val section = ContactsSection(sectionAdapter.getAsyncDiffer(Card.diffCallback) as AsyncListDiffer<ContactItem>)

        section.submitContentItems(ContactsConverter.fromVm(accounts), itemListener)

        sectionAdapter.addSection("Contacts", section as Section<Nothing, Nothing, Nothing>)

        if (contactsRecyclerView.adapter == null) {
            with(contactsRecyclerView) {
                layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
                adapter = sectionAdapter
                addItemDecoration(ContactItemDecoration(activity!!))
                overScrollMode = OVER_SCROLL_NEVER
            }
        }

        sectionAdapter.notifyDataSetChanged()

        emptySearchResultView.gone()
        emptyStateView.gone()
        contactsRecyclerView.show()
    }

    private fun showEmptyContacts() {
        emptySearchResultView.gone()
        emptyStateView.show()
        contactsRecyclerView.gone()
    }

    private fun showEmptySearchResult() {
        emptySearchResultView.show()
        emptyStateView.gone()
        contactsRecyclerView.gone()
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