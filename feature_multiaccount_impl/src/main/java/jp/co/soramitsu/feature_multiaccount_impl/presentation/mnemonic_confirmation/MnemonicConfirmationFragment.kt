/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation

import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.args.soraAccount
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ScreenshotBlockHelper
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.databinding.FragmentMnemonicConfirmationBinding
import javax.inject.Inject

@AndroidEntryPoint
class MnemonicConfirmationFragment :
    BaseFragment<MnemonicConfirmationViewModel>(R.layout.fragment_mnemonic_confirmation) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var viewModelFactory: MnemonicConfirmationViewModel.Factory

    private lateinit var screenshotBlockHelper: ScreenshotBlockHelper
    private lateinit var progressDialog: SoraProgressDialog
    private val binding by viewBinding(FragmentMnemonicConfirmationBinding::bind)

    override val viewModel: MnemonicConfirmationViewModel by viewModels {
        MnemonicConfirmationViewModel.provideFactory(
            viewModelFactory,
            requireArguments().soraAccount
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = SoraProgressDialog(requireContext())

        binding.confirmBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.nextButtonClicked()
        }

        binding.toolbar.setHomeButtonListener { findNavController().popBackStack() }

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

    @Inject
    lateinit var ms: MainStarter

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
        viewModel.showMainScreen.observeForever { multiAccount ->
            if (multiAccount) {
                ms.restartAfterAddAccount(requireContext())
            } else {
                ms.start(requireContext())
            }
        }

        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
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
