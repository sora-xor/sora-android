/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.highlightWords
import jp.co.soramitsu.common.util.ext.onBackPressed
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentClaimBinding
import javax.inject.Inject

@AndroidEntryPoint
class ClaimFragment : BaseFragment<ClaimViewModel>(R.layout.fragment_claim) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog
    private val viewBinding by viewBinding(FragmentClaimBinding::bind)

    private val vm: ClaimViewModel by viewModels()
    override val viewModel: ClaimViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        onBackPressed { requireActivity().finish() }

        progressDialog = SoraProgressDialog(requireActivity())

        configureContactUsView()
        configureTitleView()

        viewBinding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.nextButtonClicked(requireContext())
        }
        viewModel.openSendEmailEvent.observe {
            context?.let { c ->
                ShareUtil.sendEmail(c, it, getString(R.string.common_select_email_app_title))
            }
        }
        viewModel.buttonPendingStatusLiveData.observe {
            viewBinding.claimSubtitle.text =
                if (it) getString(R.string.claim_subtitle_confirmed_2) else getString(R.string.claim_subtitle)
            viewBinding.claimSubtitle.gravity =
                if (it) Gravity.CENTER_HORIZONTAL else Gravity.NO_GRAVITY
            viewBinding.claimSubtitle1.showOrHide(it)
            viewBinding.nextBtn.showOrHide(!it)
            viewBinding.loadingLayout.showOrHide(it)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkMigrationIsAlreadyFinished()
    }

    private fun configureTitleView() {
        val contactUsContent = getString(R.string.claim_welcome_sora2_v1).highlightWords(
            listOf(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.brand_soramitsu_red
                )
            ),
            listOf { }
        )
        viewBinding.claimTitle.setText(contactUsContent, TextView.BufferType.SPANNABLE)
        viewBinding.claimTitle.movementMethod = LinkMovementMethod.getInstance()
        viewBinding.claimTitle.highlightColor = Color.TRANSPARENT
    }

    private fun configureContactUsView() {
        val contactUsContent = getString(R.string.claim_contact_us).highlightWords(
            listOf(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.grey_400
                )
            ),
            listOf { viewModel.contactsUsClicked() },
            true
        )
        viewBinding.claimContactUs.setText(contactUsContent, TextView.BufferType.SPANNABLE)
        viewBinding.claimContactUs.movementMethod = LinkMovementMethod.getInstance()
        viewBinding.claimContactUs.highlightColor = Color.TRANSPARENT
    }
}
