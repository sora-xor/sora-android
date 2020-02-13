/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.AllProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.CompletedProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.FavoriteProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.VotedProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.util.CustomBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_main.howItWorksCard
import kotlinx.android.synthetic.main.fragment_main.projectsTab
import kotlinx.android.synthetic.main.fragment_main.viewPager
import kotlinx.android.synthetic.main.fragment_main.votesCard
import kotlinx.android.synthetic.main.fragment_main.votesText
import javax.inject.Inject

class MainFragment : BaseFragment<MainViewModel>(), KeyboardHelper.KeyboardListener {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private var keyboardHelper: KeyboardHelper? = null

    private var voteDialog: CustomBottomSheetDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .projectsComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).showBottomBar()

        howItWorksCard.setOnClickListener(DebounceClickListener(debounceClickHandler) { viewModel.btnHelpClicked() })

        val adapter = ProjectsViewPagerAdapter(childFragmentManager).apply {
            addPage(getString(R.string.project_all), AllProjectsFragment.newInstance())
            addPage(getString(R.string.project_voted), VotedProjectsFragment.newInstance())
            addPage(getString(R.string.project_favourites), FavoriteProjectsFragment.newInstance())
            addPage(getString(R.string.project_completed), CompletedProjectsFragment.newInstance())
        }
        viewPager.adapter = adapter
        projectsTab.setupWithViewPager(viewPager)

        votesCard.setOnClickListener(DebounceClickListener(debounceClickHandler) { viewModel.votesClick() })
    }

    override fun subscribe(viewModel: MainViewModel) {
        observe(viewModel.votesFormattedLiveData, Observer {
            votesText.text = it
        })

        observe(viewModel.showVoteProjectLiveData, EventObserver {
            voteDialog = CustomBottomSheetDialog(
                activity!!,
                CustomBottomSheetDialog.MaxVoteType.PROJECT_NEED,
                it,
                { viewModel.voteForProject(it) },
                {
                    if (keyboardHelper!!.isKeyboardShowing) {
                        hideSoftKeyboard(activity)
                    } else {
                        openSoftKeyboard(it)
                    }
                },
                debounceClickHandler
            )
            voteDialog!!.show()
        })

        observe(viewModel.showVoteUserLiveData, EventObserver {
            voteDialog = CustomBottomSheetDialog(
                activity!!,
                CustomBottomSheetDialog.MaxVoteType.USER_CAN_GIVE,
                it,
                { viewModel.voteForProject(it) },
                {
                    if (keyboardHelper!!.isKeyboardShowing) {
                        hideSoftKeyboard(activity)
                    } else {
                        openSoftKeyboard(it)
                    }
                },
                debounceClickHandler
            )
            voteDialog!!.show()
        })

        viewModel.onActivityCreated()
        viewModel.loadVotes(false)
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(view!!, this)
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