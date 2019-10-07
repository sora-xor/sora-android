/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.reputation

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_reputation.toolbar
import kotlinx.android.synthetic.main.fragment_reputation.reputationScreenTitleTextView
import kotlinx.android.synthetic.main.fragment_reputation.reputationScreenDescriptionTextView
import kotlinx.android.synthetic.main.fragment_reputation.reputationScreenCurrentReputationTitleTextView
import kotlinx.android.synthetic.main.fragment_reputation.reputationRankTitle
import kotlinx.android.synthetic.main.fragment_reputation.reputationWrapperSeparateLineView
import kotlinx.android.synthetic.main.fragment_reputation.reputationRankText
import kotlinx.android.synthetic.main.fragment_reputation.dailyVotesText
import kotlinx.android.synthetic.main.fragment_reputation.reputationRankIcon
import kotlinx.android.synthetic.main.fragment_reputation.menuView
import kotlinx.android.synthetic.main.fragment_reputation.reputationList

class ReputationFragment : BaseFragment<ReputationViewModel>() {

    companion object {
        private const val HEADER_ITEMS_COUNT = 2
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_reputation, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .reputationComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        toolbar.setTitle(getString(R.string.reputationScreenTitle))
        toolbar.setHomeButtonListener { viewModel.backButtonClick() }
        (activity as MainActivity).hideBottomView()
    }

    override fun subscribe(viewModel: ReputationViewModel) {
        observe(viewModel.lastVotesLiveData, Observer {
            showLastVotes(it)
        })

        observe(viewModel.reputationContentLiveData, Observer {
            reputationScreenTitleTextView.text = it.firstOrNull()?.title ?: ""
            reputationScreenDescriptionTextView.text = it.firstOrNull()?.description ?: ""
            reputationScreenCurrentReputationTitleTextView.text = it.getOrNull(1)?.title ?: ""

            showReputationList(it.drop(HEADER_ITEMS_COUNT).map { it.description })
        })

        observe(viewModel.calculatingReputationLiveData, Observer {
            reputationRankTitle.gone()
            reputationWrapperSeparateLineView.gone()
            reputationRankText.gone()
            dailyVotesText.text = getString(R.string.rank_empty, it.first, it.second)
            dailyVotesText.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.reputationRankPlaceholderTextSize))
            dailyVotesText.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            dailyVotesText.gravity = Gravity.CENTER_HORIZONTAL
            val reputationRankImage = ContextCompat.getDrawable(activity!!, R.drawable.icon_reputation_empty)
            reputationRankIcon.setImageDrawable(reputationRankImage)
            menuView.show()
        })

        observe(viewModel.reputationLiveData, Observer { reputation ->
            reputationRankText.show()
            dailyVotesText.gravity = Gravity.START
            reputationRankText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            dailyVotesText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            reputationRankTitle.show()
            val heartImage = ContextCompat.getDrawable(activity!!, R.drawable.icon_reputation_heart)
            dailyVotesText.setCompoundDrawablesWithIntrinsicBounds(heartImage, null, null, null)
            reputationRankText.text = getString(R.string.rank_total_rank_template, reputation.rank, reputation.totalRank)
            val reputationRankImage = ContextCompat.getDrawable(activity!!, R.drawable.icon_reputation)
            reputationRankIcon.setImageDrawable(reputationRankImage)
            menuView.show()
        })

        viewModel.run {
            loadReputation(false)
            loadInformation(false)
        }
    }

    private fun showReputationList(reputationSubList: List<String>) {
        if (reputationList.adapter == null) {
            reputationList.layoutManager = LinearLayoutManager(activity!!)
            reputationList.adapter = ReputationAdapter()
        }
        (reputationList.adapter as ReputationAdapter).submitList(reputationSubList)
    }

    private fun showLastVotes(lastVotes: String) {
        dailyVotesText.text = getString(R.string.last_votes_template, lastVotes)
        if (lastVotes.isEmpty()) {
            dailyVotesText.gone()
            reputationWrapperSeparateLineView.gone()
        } else {
            dailyVotesText.show()
            reputationWrapperSeparateLineView.show()
        }
    }
}