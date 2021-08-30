/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.invite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.date.DateTimeFormatter.Companion.DD_MMM_YYYY_HH_MM_SS_2
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_wallet_api.domain.model.InvitedUser

class AcceptedInvitesAdapter(
    private val invites: List<InvitedUser>,
    private val dateTimeFormatter: DateTimeFormatter
) : RecyclerView.Adapter<AcceptedInvitesAdapter.AcceptedInviteViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): AcceptedInviteViewHolder {
        val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_accepted_invitation, viewGroup, false)
        return AcceptedInviteViewHolder(v, dateTimeFormatter)
    }

    override fun onBindViewHolder(acceptedInviteViewHolder: AcceptedInviteViewHolder, i: Int) {
        acceptedInviteViewHolder.bind(invites[i])
    }

    override fun getItemCount(): Int {
        return invites.size
    }

    class AcceptedInviteViewHolder internal constructor(internal var parent: View, val dateTimeFormatter: DateTimeFormatter) : RecyclerView.ViewHolder(parent) {
        private val dateTextView: TextView = parent.findViewById(R.id.date)

        fun bind(invite: InvitedUser) {
            invite.invitedDate?.let {
                dateTextView.text = dateTimeFormatter.formatDate(it, DD_MMM_YYYY_HH_MM_SS_2)
            }
        }
    }
}
