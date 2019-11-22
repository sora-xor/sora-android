/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.invite

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_invite.acceptedInvitesRecyclerview
import kotlinx.android.synthetic.main.fragment_invite.addInvitationTv
import kotlinx.android.synthetic.main.fragment_invite.addInvitationView
import kotlinx.android.synthetic.main.fragment_invite.howItWorksCard
import kotlinx.android.synthetic.main.fragment_invite.invitedText
import kotlinx.android.synthetic.main.fragment_invite.placeholder
import kotlinx.android.synthetic.main.fragment_invite.sendInviteView
import kotlinx.android.synthetic.main.fragment_invite.swipeLayout
import kotlinx.android.synthetic.main.fragment_invite.timerTv
import kotlinx.android.synthetic.main.fragment_invite.votesCount

@SuppressLint("CheckResult")
class InviteFragment : BaseFragment<InviteViewModel>() {

    private val acceptedInvites = mutableListOf<InvitedUser>()
    private lateinit var adapter: AcceptedInvitesAdapter
    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_invite, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .inviteComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as MainActivity).showBottomView()

        sendInviteView.setOnClickListener { viewModel.sendInviteClick() }
        howItWorksCard.setOnClickListener { viewModel.btnHelpClicked() }
        addInvitationView.setOnClickListener { viewModel.addInvitationClicked() }

        adapter = AcceptedInvitesAdapter(acceptedInvites)
        acceptedInvitesRecyclerview.adapter = adapter
        acceptedInvitesRecyclerview.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)

        swipeLayout.setOnRefreshListener { viewModel.loadUserInviteInfo(true) }

        progressDialog = SoraProgressDialog(activity!!)
    }

    override fun subscribe(viewModel: InviteViewModel) {
        observe(viewModel.parentUserLiveData, Observer {
            invitedText.show()
            invitedText.text = getString(R.string.parent_invitation_template, it.firstName, it.lastName)
        })

        observe(viewModel.invitedUsersLiveData, Observer {
            acceptedInvites.clear()
            acceptedInvites.addAll(it)
            adapter.notifyDataSetChanged()

            if (adapter.itemCount == 0) {
                placeholder.show()
                acceptedInvitesRecyclerview.gone()
            } else {
                acceptedInvitesRecyclerview.show()
                placeholder.gone()
            }
        })

        observe(viewModel.invitationsCountLiveData, Observer {
            votesCount.text = getString(R.string.votes_left_template, it)
        })

        observe(viewModel.shareCodeLiveData, Observer {
            ShareUtil.openShareDialog(
                (activity as AppCompatActivity),
                getString(R.string.invite_code_sharing_title),
                getString(R.string.invite_link_format, it)
            )
        })

        observe(viewModel.hideSwipeRefreshEventLiveData, EventObserver {
            swipeLayout.isRefreshing = false
        })

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.enterInviteCodeButtonVisibilityLiveData, Observer {
            if (it) {
                addInvitationTv.show()
                addInvitationView.show()
            } else {
                addInvitationTv.gone()
                addInvitationView.gone()
            }
        })

        observe(viewModel.timerFormattedLiveData, Observer {
            timerTv.setTextColor(it.second)
            timerTv.text = it.first
        })

        observe(viewModel.showInvitationDialogLiveData, EventObserver {
            EnterInviteCodeDialog(activity!!) { viewModel.invitationCodeEntered(it) }.show()
        })

        observe(viewModel.enteredCodeAppliedLiveData, EventObserver {
            AlertDialog.Builder(activity!!)
                .setTitle(R.string.invite_code_applied_title)
                .setMessage(R.string.invite_code_applied_body)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        })

        viewModel.loadUserInviteInfo(false)
    }
}