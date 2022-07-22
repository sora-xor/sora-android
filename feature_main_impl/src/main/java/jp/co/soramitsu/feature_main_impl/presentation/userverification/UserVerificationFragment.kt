/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.userverification

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.util.ext.onBackPressed
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentUserVerificationBinding
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity

@AndroidEntryPoint
class UserVerificationFragment :
    BaseFragment<UserVerificationViewModel>(R.layout.fragment_user_verification) {

    private val binding by viewBinding(FragmentUserVerificationBinding::bind)
    private val vm: UserVerificationViewModel by viewModels()
    override val viewModel: UserVerificationViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.checkUser()
        viewModel.checkInviteLiveData.observe {
            (activity as? MainActivity)?.checkInviteAction()
        }
        viewModel.restartApplicationLiveData.observe {
            (activity as? MainActivity)?.restartApp()
        }

        onBackPressed { requireActivity().finish() }
    }
}
