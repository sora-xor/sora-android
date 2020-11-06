/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.isValidNameChar
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.android.synthetic.main.fragment_personal_info.emptyInvitationLinkTextView
import kotlinx.android.synthetic.main.fragment_personal_info.firstNameEt
import kotlinx.android.synthetic.main.fragment_personal_info.invCodeEt
import kotlinx.android.synthetic.main.fragment_personal_info.lastNameEt
import kotlinx.android.synthetic.main.fragment_personal_info.nextBtn
import kotlinx.android.synthetic.main.fragment_personal_info.toolbar
import javax.inject.Inject

class PersonalInfoFragment : BaseFragment<PersonalInfoViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var keyboardHelper: KeyboardHelper
    private lateinit var progressDialog: SoraProgressDialog

    companion object {
        private const val KEY_COUNTRY_ISO = "country_iso"

        fun newInstance(navController: NavController, countryIso: String) {
            val bundle = Bundle().apply {
                putString(KEY_COUNTRY_ISO, countryIso)
            }
            navController.navigate(R.id.personalInfoFragment, bundle)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_personal_info, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .personalInfoComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        nextBtn.disable()
        progressDialog = SoraProgressDialog(activity!!)

        toolbar.setHomeButtonListener { viewModel.backButtonClick() }

        setFirstAndLastNameInputFilters()

        nextBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.register(
                firstNameEt.text.toString().trim(),
                lastNameEt.text.toString().trim(),
                invCodeEt.text.toString().trim()
            )
        })

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

    private fun setFirstAndLastNameInputFilters() {
        val filter = object : InputFilter {
            override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence? {
                for (i in start until end) {
                    if (!source[i].isValidNameChar()) {
                        return source.substring(0, i)
                    }
                }
                return null
            }
        }

        firstNameEt.filters = arrayOf(filter, InputFilter.LengthFilter(Const.NAME_MAX_LENGTH))
        lastNameEt.filters = arrayOf(filter, InputFilter.LengthFilter(Const.NAME_MAX_LENGTH))
    }

    override fun subscribe(viewModel: PersonalInfoViewModel) {
        viewModel.setCountryIso(arguments!!.getString(KEY_COUNTRY_ISO, ""))

        observe(viewModel.firstNameIsEmptyEventLiveData, Observer {
            Toast.makeText(activity, R.string.common_personal_info_first_name_is_empty, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.firstNameIsNotValidEventLiveData, Observer {
            Toast.makeText(activity, R.string.common_personal_info_first_name_hyphen_error, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.lastNameIsEmptyEventLiveData, Observer {
            Toast.makeText(activity, R.string.common_personal_info_last_name_is_empty, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.lastNameIsNotValidEventLiveData, Observer {
            Toast.makeText(activity, R.string.common_personal_info_last_name_hyphen_error, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.invitationNotValidEventLiveData, Observer {
            showInvitationNotValidDialog()
        })

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.inviteCodeLiveData, Observer {
            invCodeEt.setText(it)
            emptyInvitationLinkTextView.gone()
        })

        observe(viewModel.nextButtonEnableLiveData, Observer {
            if (it) {
                nextBtn.enable()
            } else {
                nextBtn.disable()
            }
        })
    }

    private fun showInvitationNotValidDialog() {
        AlertDialog.Builder(context!!)
            .setMessage(R.string.personal_info_invitation_is_invalid)
            .setNegativeButton(R.string.common_skip) { _, _ ->
                viewModel.continueWithoutInvitationCodePressed(
                    firstNameEt.text.toString().trim(),
                    lastNameEt.text.toString().trim()
                )
            }
            .setPositiveButton(R.string.common_try_another) { _, _ ->
            }
            .show()
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