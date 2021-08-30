package jp.co.soramitsu.feature_main_impl.presentation.invite

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentInviteBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class InviteFragment : BaseFragment<InviteViewModel>(R.layout.fragment_invite) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog
    private val binding by viewBinding(FragmentInviteBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .inviteComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        binding.btnInviteShareApp.setDebouncedClickListener(debounceClickHandler) {
            viewModel.sendInviteClick()
        }
        binding.tbInvite.setHomeButtonListener {
            viewModel.backButtonPressed()
        }
        progressDialog = SoraProgressDialog(requireContext())
        initListeners()
    }

    private fun initListeners() {
        viewModel.shareCodeLiveData.observe {
            ShareUtil.openShareDialog(
                (activity as AppCompatActivity),
                getString(R.string.invite_code_sharing_title),
                getString(R.string.invite_link_format, it)
            )
        }
        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
    }
}
