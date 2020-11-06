package jp.co.soramitsu.feature_main_impl.presentation.userverification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity

class UserVerificationFragment : BaseFragment<UserVerificationViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_user_verification, container, false)
    }

    override fun initViews() {}

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .verificationComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun subscribe(viewModel: UserVerificationViewModel) {
        viewModel.checkUser()

        observe(viewModel.checkInviteLiveData, EventObserver {
            (activity as MainActivity).checkInviteAction()
        })

        observe(viewModel.restartApplicationLiveData, EventObserver {
            (activity as MainActivity).restartApp()
        })
    }
}
