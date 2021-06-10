package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.SoraClickableSpan
import jp.co.soramitsu.common.util.ext.createSendEmailIntent
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentClaimBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import javax.inject.Inject

class ClaimFragment : BaseFragment<ClaimViewModel>(R.layout.fragment_claim) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var cma: ConnectionManager

    private lateinit var progressDialog: SoraProgressDialog
    private val viewBinding by viewBinding(FragmentClaimBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .claimComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        progressDialog = SoraProgressDialog(requireActivity())

        configureContactUsView()

        viewBinding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.nextButtonClicked(requireContext())
        }
        viewModel.openSendEmailEvent.observe {
            requireActivity().createSendEmailIntent(
                it,
                getString(R.string.common_select_email_app_title)
            )
        }
        viewModel.buttonPendingStatusLiveData.observe {
            viewBinding.nextBtn.showOrHide(!it)
            viewBinding.loadingLayout.showOrHide(it)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkMigrationIsAlreadyFinished()
    }

    private fun configureContactUsView() {
        val contactUsContent = SpannableString(getString(R.string.claim_contact_2))
        contactUsContent.setSpan(
            SoraClickableSpan { viewModel.contactsUsClicked() },
            0,
            contactUsContent.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val builder = SpannableStringBuilder()
        val firstLine = SpannableString(getString(R.string.claim_contact_1))
        firstLine.setSpan(
            ForegroundColorSpan(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.grey_400
                )
            ),
            0, firstLine.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        viewBinding.claimContactUs.text = firstLine
        builder.append(firstLine)
        builder.append(" ")
        builder.append(contactUsContent)
        viewBinding.claimContactUs.setText(builder, TextView.BufferType.SPANNABLE)
        viewBinding.claimContactUs.movementMethod = LinkMovementMethod.getInstance()
        viewBinding.claimContactUs.highlightColor = Color.TRANSPARENT
    }
}
