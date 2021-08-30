package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ScreenshotBlockHelper
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.databinding.FragmentMnemonicBinding
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.adapter.MnemonicListAdapter
import javax.inject.Inject

class MnemonicFragment : BaseFragment<MnemonicViewModel>(R.layout.fragment_mnemonic) {

    companion object {
        private const val MNEMONIC_COLUMNS_COUNT = 2
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var screenshotBlockHelper: ScreenshotBlockHelper
    private val binding by viewBinding(FragmentMnemonicBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(
            requireContext(),
            OnboardingFeatureApi::class.java
        )
            .mnemonicComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.btnNextClicked()
        }

        binding.ibMnemonicShare.setDebouncedClickListener(debounceClickHandler) {
            viewModel.shareMnemonicClicked()
        }

        binding.toolbar.setHomeButtonListener { viewModel.backButtonClick() }

        binding.toolbar.setRightActionClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.common_info)
                .setMessage(R.string.mnemonic_alert_text)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        screenshotBlockHelper = ScreenshotBlockHelper(requireActivity())
        viewModel.mnemonicLiveData.observe {
            if (binding.mnemonicRecyclerView.adapter == null) {
                binding.mnemonicRecyclerView.layoutManager =
                    GridLayoutManager(requireContext(), MNEMONIC_COLUMNS_COUNT)
                binding.mnemonicRecyclerView.adapter = MnemonicListAdapter()
            }

            (binding.mnemonicRecyclerView.adapter as MnemonicListAdapter).submitList(it)
        }
        viewModel.mnemonicShare.observe { mnemonic ->
            (requireActivity() as? AppCompatActivity)?.let {
                ShareUtil.openShareDialog(it, getString(R.string.common_share), mnemonic)
            }
        }
        viewModel.getPassphrase()
    }

    override fun onResume() {
        super.onResume()
        screenshotBlockHelper.disableScreenshoting()
    }

    override fun onPause() {
        screenshotBlockHelper.enableScreenshoting()
        super.onPause()
    }
}
