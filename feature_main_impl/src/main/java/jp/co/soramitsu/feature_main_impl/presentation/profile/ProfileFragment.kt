package jp.co.soramitsu.feature_main_impl.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.android.synthetic.main.fragment_profile.howItWorksCard
import kotlinx.android.synthetic.main.fragment_profile.phoneNumberTv
import kotlinx.android.synthetic.main.fragment_profile.profileAboutTextView
import kotlinx.android.synthetic.main.fragment_profile.profileDetailsTextView
import kotlinx.android.synthetic.main.fragment_profile.profileLanguageTextView
import kotlinx.android.synthetic.main.fragment_profile.profileMyReputationNumber
import kotlinx.android.synthetic.main.fragment_profile.profileMyReputationTextView
import kotlinx.android.synthetic.main.fragment_profile.profileNameTv
import kotlinx.android.synthetic.main.fragment_profile.profilePassphraseTextView
import kotlinx.android.synthetic.main.fragment_profile.profileProfileCard
import kotlinx.android.synthetic.main.fragment_profile.profileVotesAmount
import kotlinx.android.synthetic.main.fragment_profile.profileVotesTextView
import kotlinx.android.synthetic.main.fragment_profile.selectedLanguageText
import kotlinx.android.synthetic.main.fragment_profile.userReputationAmount
import javax.inject.Inject

class ProfileFragment : BaseFragment<ProfileViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .profileSubComponentBuilder()
            .withProfileFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).showBottomBar()

        profileMyReputationTextView.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.onReputationClick()
        })

        profileVotesTextView.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.onVotesClick()
        })

        profileDetailsTextView.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.onEditProfileClicked()
        })

        profileProfileCard.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.onEditProfileClicked()
        })

        profilePassphraseTextView.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.onPassphraseClick()
        })

        profileAboutTextView.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.profileAboutClicked()
        })

        profileLanguageTextView.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.profileLanguageClicked()
        })

        howItWorksCard.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.btnHelpClicked()
        })
    }

    override fun subscribe(viewModel: ProfileViewModel) {
        observe(viewModel.userLiveData, Observer { user ->
            val fullName = "${user.firstName} ${user.lastName}"
            profileNameTv.text = fullName
            phoneNumberTv.text = user.phone
        })

        observe(viewModel.selectedLanguageLiveData, Observer {
            selectedLanguageText.text = it
        })

        observe(viewModel.votesLiveData, Observer { votes ->
            profileVotesAmount.text = votes
        })

        observe(viewModel.userReputationLiveData, Observer { reputation ->
            profileMyReputationNumber.visibility = if (reputation.rank > 0) View.VISIBLE else View.GONE
            userReputationAmount.text = reputation.rank.toString()
        })

        viewModel.loadUserData(false)
    }
}