package jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_impl.R

class ContactsAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val itemViewClickListener: (Account) -> Unit,
    private val menuItemViewClickListener: (ContactMenuItem) -> Unit
) : ListAdapter<Any, ContactsViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ContactListItem -> R.layout.item_contact
            is ContactHeader -> R.layout.item_contact_header
            is ContactMenuItem -> R.layout.item_contact_menu
            else -> throw IllegalStateException("Unknown view type at position $position")
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ContactsViewHolder {
        return when (viewType) {
            R.layout.item_contact -> {
                val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_contact, viewGroup, false)
                ContactsViewHolder.ContactViewHolder(view)
            }

            R.layout.item_contact_header -> {
                val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_contact_header, viewGroup, false)
                ContactsViewHolder.ContactsHeaderViewHolder(view)
            }

            R.layout.item_contact_menu -> {
                val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_contact_menu, viewGroup, false)
                ContactsViewHolder.ContactsMenuViewHolder(view)
            }

            else -> throw IllegalStateException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: ContactsViewHolder, position: Int) {
        when (holder) {
            is ContactsViewHolder.ContactViewHolder -> holder.bind(getItem(position) as ContactListItem, debounceClickHandler, itemViewClickListener)
            is ContactsViewHolder.ContactsHeaderViewHolder -> holder.bind(getItem(position) as ContactHeader)
            is ContactsViewHolder.ContactsMenuViewHolder -> holder.bind(getItem(position) as ContactMenuItem, debounceClickHandler, menuItemViewClickListener)
        }
    }
}

sealed class ContactsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    class ContactViewHolder(itemView: View) : ContactsViewHolder(itemView) {

        private val root: ConstraintLayout = itemView.findViewById(R.id.item_contact)
        private val nameTv: TextView = itemView.findViewById(R.id.contactNameTextView)
        private val iconTv: TextView = itemView.findViewById(R.id.contactIconTextView)
        private val contactSeparator: View = itemView.findViewById(R.id.contactsGreySeparator)

        fun bind(contact: ContactListItem, debounceClickHandler: DebounceClickHandler, itemViewClickListener: (Account) -> Unit) {
            val account = contact.account
            val nameStr = "${account.firstName} ${account.lastName}"
            nameTv.text = nameStr
            val iconStr = (account.firstName.getOrNull(0) ?: "").toString() +
                (account.lastName.getOrNull(0) ?: "").toString()
            iconTv.text = iconStr
            root.setOnClickListener(DebounceClickListener(debounceClickHandler) {
                itemViewClickListener(account)
            })

            if (contact.isLast) {
                contactSeparator.gone()
            } else {
                contactSeparator.show()
            }
        }
    }

    class ContactsMenuViewHolder(itemView: View) : ContactsViewHolder(itemView) {

        private val root: ConstraintLayout = itemView.findViewById(R.id.item_contact)
        private val nameTv: TextView = itemView.findViewById(R.id.contactNameTextView)
        private val iconTv: ImageView = itemView.findViewById(R.id.contactIconImageView)

        fun bind(contactMenuItem: ContactMenuItem, debounceClickHandler: DebounceClickHandler, itemViewClickListener: (ContactMenuItem) -> Unit) {
            nameTv.setText(contactMenuItem.nameRes)
            iconTv.setImageResource(contactMenuItem.iconRes)

            root.setOnClickListener(DebounceClickListener(debounceClickHandler) {
                itemViewClickListener(contactMenuItem)
            })
        }
    }

    class ContactsHeaderViewHolder(
        itemView: View
    ) : ContactsViewHolder(itemView)

    private val title: TextView = itemView.findViewById(R.id.contactNameTextView)

    fun bind(contactHeader: ContactHeader) {
        title.text = contactHeader.title
    }
}

object DiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is ContactListItem && newItem is ContactListItem -> oldItem.account.accountId == newItem.account.accountId
            oldItem is ContactHeader && newItem is ContactHeader -> true
            oldItem is ContactMenuItem && newItem is ContactMenuItem -> oldItem == newItem
            else -> false
        }
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is ContactListItem && newItem is ContactListItem -> oldItem == newItem
            oldItem is ContactHeader && newItem is ContactHeader -> true
            oldItem is ContactMenuItem && newItem is ContactMenuItem -> oldItem == newItem
            else -> false
        }
    }
}