package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
import com.tbruyelle.rxpermissions2.RxPermissions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.ChooserDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactsAdapter
import kotlinx.android.synthetic.main.fragment_contacts.contactsSearchView
import kotlinx.android.synthetic.main.fragment_contacts.preloaderView
import kotlinx.android.synthetic.main.fragment_contacts.toolbar
import javax.inject.Inject

@SuppressLint("CheckResult")
class ContactsFragment : BaseFragment<ContactsViewModel>(), SearchView.OnQueryTextListener {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var integrator: IntentIntegrator
    private lateinit var emptyStateView: View
    private lateinit var emptySearchResultView: View
    private lateinit var contactsRecyclerView: RecyclerView

    private var keyboardHelper: KeyboardHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        with(toolbar) {
            inflateMenu(R.menu.contacts_fragment_menu)

            setOnMenuItemClickListener {
                viewModel.qrMenuItemClicked()
                true
            }

            setHomeButtonListener {
                if (keyboardHelper?.isKeyboardShowing == true) {
                    hideSoftKeyboard(activity)
                } else {
                    viewModel.backButtonPressed()
                }
            }
        }

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
                    debounceClickHandler,
                    { viewModel.contactClicked(it.accountId, "${it.firstName} ${it.lastName}") },
                    { viewModel.menuItemClicked(it) },
                    { viewModel.ethItemClicked(it) }
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
                R.string.contacts_scan,
                getString(R.string.invoice_scan_camera),
                getString(R.string.invoice_scan_gallery),
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

        observe(viewModel.qrErrorLiveData, EventObserver {
            showErrorFromResponse(it)
        })
    }

    private fun configureClicks() {
        integrator = IntentIntegrator.forSupportFragment(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES)
            setPrompt(getString(R.string.contacts_scan))
            setBeepEnabled(false)
        }

        viewModel.getContacts(false, true)
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .contactsComponentBuilder()
            .withFragment(this)
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

        startActivityForResult(Intent.createChooser(intent, getString(R.string.common_options_title)), PICK_IMAGE_REQUEST)
    }
}