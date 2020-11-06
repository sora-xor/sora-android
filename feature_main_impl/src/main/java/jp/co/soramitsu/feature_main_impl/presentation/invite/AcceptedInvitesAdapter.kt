package jp.co.soramitsu.feature_main_impl.presentation.invite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_main_impl.R

class AcceptedInvitesAdapter(
    private val invites: List<InvitedUser>
) : RecyclerView.Adapter<AcceptedInvitesAdapter.AcceptedInviteViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): AcceptedInviteViewHolder {
        val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_accepted_invitation, viewGroup, false)
        return AcceptedInviteViewHolder(v)
    }

    override fun onBindViewHolder(acceptedInviteViewHolder: AcceptedInviteViewHolder, i: Int) {
        acceptedInviteViewHolder.bind(invites[i])
    }

    override fun getItemCount(): Int {
        return invites.size
    }

    class AcceptedInviteViewHolder internal constructor(internal var parent: View) : RecyclerView.ViewHolder(parent) {
        private val nameTextView: TextView = parent.findViewById(R.id.name)

        fun bind(invite: InvitedUser) {
            nameTextView.text = StringBuilder()
                .append(invite.firstName)
                .append(" ")
                .append(invite.lastName)
                .toString()
        }
    }
}