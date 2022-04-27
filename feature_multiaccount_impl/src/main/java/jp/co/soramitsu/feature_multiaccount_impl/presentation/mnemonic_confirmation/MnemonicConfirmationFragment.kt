/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation

import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ScreenshotBlockHelper
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_multiaccount_api.di.MultiaccountFeatureApi
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.databinding.FragmentMnemonicConfirmationBinding
import jp.co.soramitsu.feature_multiaccount_impl.di.MultiaccountFeatureComponent
import javax.inject.Inject

class MnemonicConfirmationFragment :
    BaseFragment<MnemonicConfirmationViewModel>(R.layout.fragment_mnemonic_confirmation) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var screenshotBlockHelper: ScreenshotBlockHelper
    private val binding by viewBinding(FragmentMnemonicConfirmationBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<MultiaccountFeatureComponent>(
            requireContext(),
            MultiaccountFeatureApi::class.java
        )
            .mnemonicConfirmationComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.confirmBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.nextButtonClicked()
        }

        binding.toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }

        binding.toolbar.setRightActionClickListener { viewModel.resetConfirmationClicked() }

        screenshotBlockHelper = ScreenshotBlockHelper(requireActivity())

        binding.toolbar.setRightActionClickListener {
            viewModel.resetConfirmationClicked()
        }

        binding.confirmationMnemonicView.setOnClickListener {
            viewModel.removeLastWordFromConfirmation()
        }

        binding.confirmationMnemonicView.disableWordDisappearAnimation()

        binding.skipBtn.setOnClickListener {
            viewModel.skipClicked()
        }
        initListeners()
    }

    override fun onResume() {
        super.onResume()
        screenshotBlockHelper.disableScreenshoting()
    }

    override fun onPause() {
        screenshotBlockHelper.enableScreenshoting()
        super.onPause()
    }

    private fun initListeners() {
        viewModel.shuffledMnemonicLiveData.observe {
            populateMnemonicContainer(it)
        }
        viewModel.resetConfirmationEvent.observe {
            binding.confirmationMnemonicView.resetView()
            binding.wordsMnemonicView.restoreAllWords()
        }
        viewModel.removeLastWordFromConfirmationEvent.observe {
            binding.confirmationMnemonicView.removeLastWord()
            binding.wordsMnemonicView.restoreLastWord()
        }
        viewModel.nextButtonEnableLiveData.observe {
            binding.confirmBtn.isEnabled = it
        }
        viewModel.matchingMnemonicErrorAnimationEvent.observe {
            playMatchingMnemonicErrorAnimation()
        }
        viewModel.showMainScreen.observeForever {
            FeatureUtils.getFeature<MainFeatureApi>(requireActivity().applicationContext, MainFeatureApi::class.java)
                .provideMainStarter()
                .start(requireContext())
        }
    }

    private fun populateMnemonicContainer(mnemonicWords: List<String>) {
        val words = mnemonicWords.map { mnemonicWord ->
            jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.view.MnemonicWordView(
                requireActivity()
            ).apply {
                setWord(mnemonicWord)
                setOnClickListener { wordClickListener(this, mnemonicWord) }
                measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            }
        }

        binding.wordsMnemonicView.post {
            binding.wordsMnemonicView.populateWithMnemonic(words)
        }

        val containerHeight = binding.wordsMnemonicView.getMinimumMeasuredHeight()
        binding.wordsMnemonicView.minimumHeight = containerHeight
        binding.confirmationMnemonicView.minimumHeight = containerHeight
    }

    private val wordClickListener: (jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.view.MnemonicWordView, String) -> Unit =
        { mnemonicWordView, word ->
            viewModel.addWordToConfirmMnemonic(word)

            binding.wordsMnemonicView.removeWordView(mnemonicWordView)

            val wordView =
                jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.view.MnemonicWordView(
                    requireActivity()
                ).apply {
                    setWord(word)
                    measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                }
            binding.confirmationMnemonicView.populateWord(wordView)
        }

    private fun playMatchingMnemonicErrorAnimation() {
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.shake)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                viewModel.matchingErrorAnimationCompleted()
            }
        })
        binding.confirmationMnemonicView.startAnimation(animation)
    }
}
