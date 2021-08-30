/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentMainBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.AllProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.CompletedProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.VotedProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.util.VoteBottomSheetDialog
import jp.co.soramitsu.feature_main_impl.presentation.util.VoteBottomSheetDialog.VotableType
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class MainFragment :
    BaseFragment<MainViewModel>(R.layout.fragment_main),
    KeyboardHelper.KeyboardListener {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var numbersFormatter: NumbersFormatter

    private var keyboardHelper: KeyboardHelper? = null
    private val binding by viewBinding(FragmentMainBinding::bind)
    private var voteDialog: VoteBottomSheetDialog? = null

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .projectsComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        val adapter = ProjectsViewPagerAdapter(childFragmentManager).apply {
            addPage(getString(R.string.project_mark_new), AllProjectsFragment.newInstance())
            addPage(getString(R.string.project_voted), VotedProjectsFragment.newInstance())
            addPage(
                getString(R.string.referendum_ended_title),
                CompletedProjectsFragment.newInstance()
            )
        }
        binding.viewPager.adapter = adapter
        binding.projectsTab.setupWithViewPager(binding.viewPager)

        for (i in 0 until binding.projectsTab.tabCount) {
            val tabView =
                LayoutInflater.from(activity).inflate(R.layout.item_project_tab, null) as TextView
            tabView.text = adapter.getPageTitle(i)
            binding.projectsTab.getTabAt(i)?.customView = tabView
        }

        binding.toolbar.setHomeButtonListener { viewModel.backPressed() }
        initListeners()
    }

    private fun initListeners() {
        viewModel.showVoteForReferendumLiveData.observe { maxAllowedVotes ->
            openVotingSheet(maxAllowedVotes, VotableType.Referendum(true)) {
                viewModel.voteOnReferendum(it, true)
            }
        }
        viewModel.showVoteAgainstReferendumLiveData.observe { maxAllowedVotes ->
            openVotingSheet(maxAllowedVotes, VotableType.Referendum(false)) {
                viewModel.voteOnReferendum(it, false)
            }
        }
    }

    private fun openVotingSheet(
        maxAllowedVotes: Int,
        votableType: VotableType,
        whenDone: (Long) -> Unit
    ) {
        voteDialog = VoteBottomSheetDialog(
            requireActivity(),
            votableType,
            maxAllowedVotes,
            { whenDone.invoke(it) },
            {
                if (keyboardHelper!!.isKeyboardShowing) {
                    hideSoftKeyboard(activity)
                } else {
                    openSoftKeyboard(it)
                }
            },
            numbersFormatter,
            debounceClickHandler
        )
        voteDialog!!.show()
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(requireView(), this)
    }

    override fun onPause() {
        super.onPause()
        keyboardHelper?.release()
        voteDialog?.dismiss()
    }

    override fun onKeyboardShow() {
        voteDialog?.showCloseKeyboard()
    }

    override fun onKeyboardHide() {
        voteDialog?.showOpenKeyboard()
    }
}
