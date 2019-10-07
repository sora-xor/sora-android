/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.jakewharton.rxbinding2.view.RxView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
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

@SuppressLint("CheckResult")
class MainFragment : BaseFragment<MainViewModel>(), KeyboardHelper.KeyboardListener {

    private lateinit var keyboardHelper: KeyboardHelper

    private lateinit var bottomSheetDialog: CustomBottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .projectsComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        bottomSheetDialog = CustomBottomSheetDialog(activity!!)

        RxView.clicks(bottomSheetDialog.voteButton)
            .subscribe({
                viewModel.voteForProject(bottomSheetDialog.getVotes())
            }, {
                it.printStackTrace()
            })

        RxView.clicks(bottomSheetDialog.keyboardButton)
            .subscribe({
                if (keyboardHelper.isKeyboardShowing) {
                    hideSoftKeyboard(activity)
                } else {
                    openSoftKeyboard(bottomSheetDialog.votesEditText)
                }
            }, {
                it.printStackTrace()
            })

        RxView.clicks(howItWorksCard).subscribe { viewModel.btnHelpClicked() }

        val adapter = ProjectsViewPagerAdapter(childFragmentManager).apply {
            addPage(getString(R.string.tabs_all), AllProjectsFragment.newInstance())
            addPage(getString(R.string.tabs_voted), VotedProjectsFragment.newInstance())
            addPage(getString(R.string.tabs_favourites), FavoriteProjectsFragment.newInstance())
            addPage(getString(R.string.tabs_complete), CompletedProjectsFragment.newInstance())
        }
        viewPager.adapter = adapter
        projectsTab.setupWithViewPager(viewPager)

        votesCard.setOnClickListener { viewModel.votesClick() }
    }

    override fun subscribe(viewModel: MainViewModel) {
        observe(viewModel.hideVoteDialogLiveData, Observer {
            bottomSheetDialog.dismiss()
        })

        observe(viewModel.votesFormattedLiveData, Observer {
            votesText.text = it
        })

        observe(viewModel.showVoteDialogLiveData, EventObserver {
            bottomSheetDialog.setSeekBarMax(it)
            bottomSheetDialog.show()
        })

        viewModel.onActivityCreated()
        viewModel.loadVotes(false)
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(bottomSheetDialog.view, this)
    }

    override fun onPause() {
        super.onPause()
        keyboardHelper.release()
        bottomSheetDialog.dismiss()
    }

    override fun onKeyboardShow() {
        bottomSheetDialog.keyboardButton.setImageResource(R.drawable.icon_close_keyboard)
    }

    override fun onKeyboardHide() {
        bottomSheetDialog.keyboardButton.setImageResource(R.drawable.icon_open_keyboard)
    }
}