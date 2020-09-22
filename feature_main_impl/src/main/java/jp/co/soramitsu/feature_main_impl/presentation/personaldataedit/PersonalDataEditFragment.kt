/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.isValidNameChar
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.android.synthetic.main.fragment_personal_data_edit.firstNameEt
import kotlinx.android.synthetic.main.fragment_personal_data_edit.lastNameEt
import kotlinx.android.synthetic.main.fragment_personal_data_edit.nextBtn
import kotlinx.android.synthetic.main.fragment_personal_data_edit.phoneNumberEt
import kotlinx.android.synthetic.main.fragment_personal_data_edit.toolbar
import javax.inject.Inject

class PersonalDataEditFragment : BaseFragment<PersonalDataEditViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog

    private var keyboardHelper: KeyboardHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_personal_data_edit, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .personalComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        toolbar.setHomeButtonListener { viewModel.backPressed() }

        nextBtn.setText(R.string.common_save)

        nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.saveData(firstNameEt.text!!.toString(), lastNameEt.text!!.toString())
            }
        )

        val inputFilter = InputFilter { source, start, end, _, _, _ ->

            for (i in start until end) {
                if (!source[i].isValidNameChar()) {
                    return@InputFilter source.substring(0, i)
                }
            }
            null
        }

        firstNameEt.filters = arrayOf(inputFilter, InputFilter.LengthFilter(Const.NAME_MAX_LENGTH))
        lastNameEt.filters = arrayOf(inputFilter, InputFilter.LengthFilter(Const.NAME_MAX_LENGTH))

        progressDialog = SoraProgressDialog(activity!!)

        val textWatcher = object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.firstNameAndLastNameChanged(firstNameEt.text.toString(), lastNameEt.text.toString())
            }
        }

        firstNameEt.addTextChangedListener(textWatcher)
        lastNameEt.addTextChangedListener(textWatcher)
    }
    override fun subscribe(viewModel: PersonalDataEditViewModel) {
        observe(viewModel.userLiveData, Observer { user ->
            firstNameEt.setText(user.firstName)
            lastNameEt.setText(user.lastName)
            phoneNumberEt.setText(user.phone)
        })

        observe(viewModel.emptyFirstNameLiveData, EventObserver {
            Toast.makeText(activity!!, R.string.common_personal_info_first_name_is_empty, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.incorrectFirstNameLiveData, EventObserver {
            Toast.makeText(activity!!, R.string.common_personal_info_first_name_hyphen_error, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.emptyLastNameLiveData, EventObserver {
            Toast.makeText(activity!!, R.string.common_personal_info_last_name_is_empty, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.incorrectLastNameLiveData, EventObserver {
            Toast.makeText(activity!!, R.string.common_personal_info_last_name_hyphen_error, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.nextButtonEnableLiveData, Observer {
            if (it) {
                nextBtn.enable()
            } else {
                nextBtn.disable()
            }
        })

        viewModel.getUserData(false)
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(view!!)
    }

    override fun onPause() {
        super.onPause()
        if (keyboardHelper != null && keyboardHelper!!.isKeyboardShowing) {
            hideSoftKeyboard(activity)
        }
        keyboardHelper?.release()
    }
}