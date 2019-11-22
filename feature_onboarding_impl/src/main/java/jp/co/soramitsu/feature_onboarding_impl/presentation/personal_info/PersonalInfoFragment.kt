/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.isValidNameChar
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.android.synthetic.main.fragment_personal_info.firstNameEt
import kotlinx.android.synthetic.main.fragment_personal_info.invCodeEt
import kotlinx.android.synthetic.main.fragment_personal_info.lastNameEt
import kotlinx.android.synthetic.main.fragment_personal_info.nextBtn
import kotlinx.android.synthetic.main.fragment_personal_info.toolbar
import kotlinx.android.synthetic.main.fragment_personal_info.emptyInvitationLinkTextView

@SuppressLint("CheckResult")
class PersonalInfoFragment : BaseFragment<PersonalInfoViewModel>() {

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

    override fun initViews() {
        progressDialog = SoraProgressDialog(activity!!)

        toolbar.setTitle(getString(R.string.fragment_personal_info_title))
        toolbar.setHomeButtonListener { viewModel.backButtonClick() }

        setFirstAndLastNameInputFilters()
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

        nextBtn.setOnClickListener {
            viewModel.register(
                firstNameEt.text.toString().trim(),
                lastNameEt.text.toString().trim(),
                invCodeEt.text.toString().trim()
            )
        }

        observe(viewModel.firstNameIsEmptyEventLiveData, Observer {
            Toast.makeText(activity, R.string.first_name_is_empty, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.firstNameIsNotValidEventLiveData, Observer {
            Toast.makeText(activity, R.string.first_name_hyphen_error, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.lastNameIsEmptyEventLiveData, Observer {
            Toast.makeText(activity, R.string.last_name_is_empty, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.lastNameIsNotValidEventLiveData, Observer {
            Toast.makeText(activity, R.string.last_name_hyphen_error, Toast.LENGTH_SHORT).show()
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
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .personalInfoComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    private fun showInvitationNotValidDialog() {
        AlertDialog.Builder(context!!)
            .setMessage(R.string.fragment_personal_invitation_is_invalid)
            .setNegativeButton(R.string.fragment_personal_invitation_skip) { _, _ ->
                viewModel.continueWithoutInvitationCodePressed(
                    firstNameEt.text.toString().trim(),
                    lastNameEt.text.toString().trim()
                )
            }
            .setPositiveButton(R.string.fragment_personal_invitation_try_another) { _, _ ->
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