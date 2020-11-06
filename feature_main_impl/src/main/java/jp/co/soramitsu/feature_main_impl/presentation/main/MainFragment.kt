package jp.co.soramitsu.feature_main_impl.presentation.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.AllProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.CompletedProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.FavoriteProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.VotedProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.util.VoteBottomSheetDialog
import jp.co.soramitsu.feature_main_impl.presentation.util.VoteBottomSheetDialog.MaxVoteType
import jp.co.soramitsu.feature_main_impl.presentation.util.VoteBottomSheetDialog.VotableType
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.android.synthetic.main.fragment_main.howItWorksCard
import kotlinx.android.synthetic.main.fragment_main.projectsTab
import kotlinx.android.synthetic.main.fragment_main.viewPager
import kotlinx.android.synthetic.main.fragment_main.votesCard
import kotlinx.android.synthetic.main.fragment_main.votesText
import javax.inject.Inject

class MainFragment : BaseFragment<MainViewModel>(), KeyboardHelper.KeyboardListener {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    @Inject lateinit var numbersFormatter: NumbersFormatter

    private var keyboardHelper: KeyboardHelper? = null

    private var voteDialog: VoteBottomSheetDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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

        for (i in 0 until projectsTab.tabCount) {
            val tabView =
                LayoutInflater.from(activity).inflate(R.layout.item_project_tab, null) as TextView
            tabView.text = adapter.getPageTitle(i)
            projectsTab.getTabAt(i)?.customView = tabView
        }

        votesCard.setOnClickListener(DebounceClickListener(debounceClickHandler) { viewModel.votesClick() })
    }

    override fun subscribe(viewModel: MainViewModel) {
        observe(viewModel.votesFormattedLiveData, Observer {
            votesText.text = it
        })

        observe(viewModel.showVoteProjectLiveData, EventObserver { maxAllowedVotes ->
            openVotingSheet(
                maxAllowedVotes,
                VotableType.Project(MaxVoteType.VOTABLE_NEED),
                viewModel::voteForProject
            )
        })

        observe(viewModel.showVoteUserLiveData, EventObserver { maxAllowedVotes ->
            openVotingSheet(
                maxAllowedVotes,
                VotableType.Project(MaxVoteType.USER_CAN_GIVE),
                viewModel::voteForProject
            )
        })

        observe(viewModel.showVoteForReferendumLiveData, EventObserver { maxAllowedVotes ->
            openVotingSheet(maxAllowedVotes, VotableType.Referendum(true)) {
                viewModel.voteOnReferendum(it, true)
            }
        })

        observe(viewModel.showVoteAgainstReferendumLiveData, EventObserver { maxAllowedVotes ->
            openVotingSheet(maxAllowedVotes, VotableType.Referendum(false)) {
                viewModel.voteOnReferendum(it, false)
            }
        })

        viewModel.onActivityCreated()
    }

    private fun openVotingSheet(
        maxAllowedVotes: Int,
        votableType: VotableType,
        whenDone: (Long) -> Unit
    ) {
        voteDialog = VoteBottomSheetDialog(
            activity!!,
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