package jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.ByteSizeTextWatcher
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.SoraClickableSpan
import jp.co.soramitsu.common.util.nameByteSizeTextWatcher
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.databinding.FragmentPersonalInfoBinding
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import javax.inject.Inject

class PersonalInfoFragment : BaseFragment<PersonalInfoViewModel>(R.layout.fragment_personal_info) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var keyboardHelper: KeyboardHelper
    private lateinit var progressDialog: SoraProgressDialog
    private lateinit var nameSizeTextWatcher: ByteSizeTextWatcher
    private val binding by viewBinding(FragmentPersonalInfoBinding::bind)

    companion object {
        fun newInstance(navController: NavController) {
            navController.navigate(R.id.personalInfoFragment)
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(
            requireContext(),
            OnboardingFeatureApi::class.java
        )
            .personalInfoComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = SoraProgressDialog(requireContext())

        binding.toolbar.setHomeButtonListener { viewModel.backButtonClick() }

        nameSizeTextWatcher = nameByteSizeTextWatcher(
            binding.accountNameEt
        ) {
            Toast.makeText(
                requireActivity(),
                R.string.common_personal_info_account_name_invalid,
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.accountNameEt.addTextChangedListener(nameSizeTextWatcher)

        binding.nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.register(binding.accountNameEt.text.toString().trim())
            }
        )

        val termsContent = SpannableString(getString(R.string.tutorial_terms_and_conditions_3))
        termsContent.setSpan(
            SoraClickableSpan { viewModel.showTermsScreen() },
            0,
            termsContent.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val privacyContent = SpannableString(getString(R.string.tutorial_privacy_policy))
        privacyContent.setSpan(
            SoraClickableSpan { viewModel.showPrivacyScreen() },
            0,
            privacyContent.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val builder = SpannableStringBuilder()
        val firstLine = SpannableString(getString(R.string.tutorial_terms_and_conditions_1))
        firstLine.setSpan(
            ForegroundColorSpan(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.grey_400
                )
            ),
            0, firstLine.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tutorialTermsCondition.text = firstLine
        val and = SpannableString(getString(R.string.common_and))
        and.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.grey_400)),
            0,
            and.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.append(firstLine)
        builder.append(termsContent)
        builder.append(" ")
        builder.append(and)
        builder.append(" ")
        builder.append(privacyContent)
        binding.tutorialTermsCondition.setText(builder, TextView.BufferType.SPANNABLE)
        binding.tutorialTermsCondition.movementMethod = LinkMovementMethod.getInstance()
        binding.tutorialTermsCondition.highlightColor = Color.TRANSPARENT

        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
        viewModel.screenshotAlertDialogEvent.observe {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.screenshot_alert_title)
                .setMessage(R.string.screenshot_alert_text)
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.screenshotAlertOkClicked() }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(requireView())
    }

    override fun onPause() {
        super.onPause()
        if (keyboardHelper != null && keyboardHelper!!.isKeyboardShowing) {
            hideSoftKeyboard(activity)
        }
        keyboardHelper?.release()
    }

    override fun onDestroyView() {
        nameSizeTextWatcher.destroy()
        super.onDestroyView()
    }
}
