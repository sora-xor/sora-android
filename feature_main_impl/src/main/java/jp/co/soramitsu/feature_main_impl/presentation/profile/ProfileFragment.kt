/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.jakewharton.rxbinding2.view.RxView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.setMultipleOnClickListeners
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_profile.profileMyReputationNumber
import kotlinx.android.synthetic.main.fragment_profile.profileMyReputationTextView
import kotlinx.android.synthetic.main.fragment_profile.profileReputationArrow
import kotlinx.android.synthetic.main.fragment_profile.profileVotesTextView
import kotlinx.android.synthetic.main.fragment_profile.profileVotesArrow
import kotlinx.android.synthetic.main.fragment_profile.profileVotesNumber
import kotlinx.android.synthetic.main.fragment_profile.profileDetailsTextView
import kotlinx.android.synthetic.main.fragment_profile.profileDetailsArrow
import kotlinx.android.synthetic.main.fragment_profile.profileProfileCard
import kotlinx.android.synthetic.main.fragment_profile.profilePassphraseTextView
import kotlinx.android.synthetic.main.fragment_profile.profilePassphraseArrow
import kotlinx.android.synthetic.main.fragment_profile.profileConversionTextView
import kotlinx.android.synthetic.main.fragment_profile.profileConversionArrow
import kotlinx.android.synthetic.main.fragment_profile.profileAboutTextView
import kotlinx.android.synthetic.main.fragment_profile.profileAboutArrow
import kotlinx.android.synthetic.main.fragment_profile.howItWorksCard
import kotlinx.android.synthetic.main.fragment_profile.profileNameTv
import kotlinx.android.synthetic.main.fragment_profile.phoneNumberTv
import kotlinx.android.synthetic.main.fragment_profile.profileVotesAmount
import kotlinx.android.synthetic.main.fragment_profile.profileConversionName
import kotlinx.android.synthetic.main.fragment_profile.userReputationAmount

@SuppressLint("CheckResult")
class ProfileFragment : BaseFragment<ProfileViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .profileSubComponentBuilder()
            .withRouter(activity as MainRouter)
            .withProfileFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as MainActivity).showBottomView()

        setMultipleOnClickListeners(profileMyReputationTextView, profileReputationArrow, profileMyReputationNumber) {
            viewModel.onReputationClick()
        }

        setMultipleOnClickListeners(profileVotesTextView, profileVotesArrow, profileVotesNumber) {
            viewModel.onVotesClick()
        }

        setMultipleOnClickListeners(profileDetailsTextView, profileDetailsArrow, profileProfileCard) {
            viewModel.onEditProfileClicked()
        }

        setMultipleOnClickListeners(profilePassphraseTextView, profilePassphraseArrow) {
            viewModel.onPassphraseClick()
        }

        setMultipleOnClickListeners(profileConversionTextView, profileConversionArrow) {
            viewModel.profileConversionClicked()
        }

        setMultipleOnClickListeners(profileAboutTextView, profileAboutArrow) {
            viewModel.profileAboutClicked()
        }

        RxView.clicks(howItWorksCard)
            .subscribe { viewModel.btnHelpClicked() }
    }

    override fun subscribe(viewModel: ProfileViewModel) {
        observe(viewModel.userLiveData, Observer { user ->
            val fullName = "${user.firstName} ${user.lastName}"
            profileNameTv.text = fullName
            phoneNumberTv.text = user.phone
        })

        observe(viewModel.votesLiveData, Observer { votes ->
            profileVotesAmount.text = votes
        })

        observe(viewModel.selectedCurrencyLiveData, Observer { currencySymbol ->
            profileConversionName.text = currencySymbol
        })

        observe(viewModel.userReputationLiveData, Observer { reputation ->
            profileMyReputationNumber.visibility = if (reputation.rank > 0) View.VISIBLE else View.GONE
            userReputationAmount.text = reputation.rank.toString()
        })

        viewModel.loadUserData(false)
    }
}