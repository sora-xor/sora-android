/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.switchaccount

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentSwitchAccountBinding
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
class SwitchAccountFragment :
    BaseFragment<SwitchAccountViewModel>(R.layout.fragment_switch_account) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var mas: MultiaccountStarter

    private val binding by viewBinding(FragmentSwitchAccountBinding::bind)
    override val viewModel: SwitchAccountViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        binding.toolbar.setHomeButtonListener {
            viewModel.onBackClick()
        }

        binding.rvSwitchAccount.adapter = SwitchAccountAdapter(
            debounceClickHandler,
            {
                viewModel.onAccountItemClick(it)
            },
            {
                viewModel.onAccountItemLongClick(it)
            }
        )

        ContextCompat.getDrawable(
            binding.rvSwitchAccount.context,
            R.drawable.line_ver_divider
        )?.let {
            binding.rvSwitchAccount.addItemDecoration(
                DividerItemDecoration(
                    binding.rvSwitchAccount.context,
                    DividerItemDecoration.VERTICAL
                ).apply {
                    setDrawable(it)
                }
            )
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }

        viewModel.soraAccounts.observe {
            binding.rvSwitchAccount.adapter.safeCast<SwitchAccountAdapter>()?.submitList(it)
        }

        binding.tutorialSignUpButton.setOnClickListener {
            showPersonalInfo()
        }

        binding.tutorialRestoreTextView.setOnClickListener {
            showRecovery()
        }
    }

    private fun showPersonalInfo() {
        mas.startCreateAccount(findNavController())
    }

    private fun showRecovery() {
        mas.startRecoveryAccount(findNavController())
    }
}
