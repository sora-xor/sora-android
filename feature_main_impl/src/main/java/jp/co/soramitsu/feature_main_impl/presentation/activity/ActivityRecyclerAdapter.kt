/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.activity

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeedAnnouncement
import jp.co.soramitsu.feature_main_impl.R

class ActivityRecyclerAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val helpClickListener: () -> Unit
) : ListAdapter<Any, ActivityFeedViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (val currentItem = getItem(position)) {
            is ActivityFeedItem -> {
                when (currentItem.listItemType) {
                    ActivityFeedItem.Type.DURING_THE_DAY -> R.layout.item_activity_feed_during_the_day
                    ActivityFeedItem.Type.FIRST_OF_THE_DAY -> R.layout.item_activity_feed_first_of_the_day
                    ActivityFeedItem.Type.LAST_OF_THE_DAY -> R.layout.item_activity_feed_last_of_the_day
                    ActivityFeedItem.Type.THE_ONLY_EVENT_OF_THE_DAY -> R.layout.item_activity_feed_the_only_of_the_day
                }
            }
            is ActivityFeedAnnouncement -> R.layout.item_activity_feed_announce
            is ActivityHeader -> R.layout.item_activity_feed_header
            is ActivityDate -> R.layout.item_activity_feed_date
            else -> throw IllegalStateException("Unknown view type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityFeedViewHolder {
        return when (viewType) {
            R.layout.item_activity_feed_during_the_day -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_feed_during_the_day, parent, false)
                ActivityFeedViewHolder.ActivityViewHolder(view)
            }
            R.layout.item_activity_feed_first_of_the_day -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_feed_first_of_the_day, parent, false)
                ActivityFeedViewHolder.ActivityViewHolder(view)
            }
            R.layout.item_activity_feed_last_of_the_day -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_feed_last_of_the_day, parent, false)
                ActivityFeedViewHolder.ActivityViewHolder(view)
            }
            R.layout.item_activity_feed_the_only_of_the_day -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_feed_the_only_of_the_day, parent, false)
                ActivityFeedViewHolder.ActivityViewHolder(view)
            }
            R.layout.item_activity_feed_announce -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_feed_announce, parent, false)
                ActivityFeedViewHolder.ActivityAnnouncementViewHolder(view)
            }
            R.layout.item_activity_feed_header -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_feed_header, parent, false)
                ActivityFeedViewHolder.ActivityHeaderViewHolder(view)
            }
            R.layout.item_activity_feed_date -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_feed_date, parent, false)
                ActivityFeedViewHolder.ActivityDateViewHolder(view)
            }
            else -> throw IllegalStateException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: ActivityFeedViewHolder, position: Int) {
        when (holder) {
            is ActivityFeedViewHolder.ActivityViewHolder -> holder.bind(getItem(position) as ActivityFeedItem)
            is ActivityFeedViewHolder.ActivityAnnouncementViewHolder -> holder.bind(getItem(position) as ActivityFeedAnnouncement)
            is ActivityFeedViewHolder.ActivityHeaderViewHolder -> holder.bind(getItem(position) as ActivityHeader, debounceClickHandler, helpClickListener)
            is ActivityFeedViewHolder.ActivityDateViewHolder -> holder.bind(getItem(position) as ActivityDate)
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is ActivityFeedItem && newItem is ActivityFeedItem -> false
            oldItem is ActivityFeedAnnouncement && newItem is ActivityFeedAnnouncement -> oldItem.message == newItem.message
            oldItem is ActivityHeader && newItem is ActivityHeader -> true
            oldItem is ActivityDate && newItem is ActivityDate -> oldItem.date == newItem.date
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is ActivityFeedItem && newItem is ActivityFeedItem -> false
            oldItem is ActivityFeedAnnouncement && newItem is ActivityFeedAnnouncement -> oldItem.message == newItem.message
            oldItem is ActivityHeader && newItem is ActivityHeader -> true
            oldItem is ActivityDate && newItem is ActivityDate -> oldItem.date == newItem.date
            else -> true
        }
    }
}

sealed class ActivityFeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class ActivityViewHolder(
        itemView: View
    ) : ActivityFeedViewHolder(itemView) {

        private var typeTv: TextView = itemView.findViewById(R.id.activity_type)
        private var typeImg: ImageView = itemView.findViewById(R.id.typeImg)
        private var eventDateTv: TextView = itemView.findViewById(R.id.activity_event_date)
        private var titleTv: TextView = itemView.findViewById(R.id.activity_project_name)
        private var descriptionTv: TextView = itemView.findViewById(R.id.activity_description)
        private var votesTv: TextView = itemView.findViewById(R.id.activity_votes_count)
        private var plusIconImg: ImageView = itemView.findViewById(R.id.activity_icon_plus)
        private var heartIconImg: ImageView = itemView.findViewById(R.id.activity_icon_heart)

        fun bind(activity: ActivityFeedItem) {

            typeTv.text = activity.type
            typeImg.setImageResource(activity.iconDrawable)

            titleTv.text = activity.title

            if (activity.description.isEmpty()) {
                descriptionTv.gone()
            } else {
                descriptionTv.show()
            }

            descriptionTv.text = activity.description

            if (activity.votesString.isEmpty()) {
                votesTv.gone()
                plusIconImg.gone()
            } else {
                votesTv.show()
                plusIconImg.show()
            }

            if (activity.votesRightDrawable == -1) {
                heartIconImg.gone()
            } else {
                heartIconImg.show()
                heartIconImg.setImageResource(activity.votesRightDrawable)
            }

            votesTv.text = activity.votesString
            plusIconImg.setImageResource(R.drawable.plus)
            eventDateTv.text = activity.issuedAtString
        }
    }

    class ActivityAnnouncementViewHolder(
        itemView: View
    ) : ActivityFeedViewHolder(itemView) {

        private val announcementTv: TextView = itemView.findViewById(R.id.announcementTv)

        fun bind(announcement: ActivityFeedAnnouncement) {
            announcementTv.text = announcement.message
        }
    }

    class ActivityHeaderViewHolder(
        itemView: View
    ) : ActivityFeedViewHolder(itemView) {

        private val titleTv: TextView = itemView.findViewById(R.id.titleTv)
        private val helpView: View = itemView.findViewById(R.id.howItWorksCard)

        fun bind(header: ActivityHeader, debounceClickHandler: DebounceClickHandler, helpClickListener: () -> Unit) {
            titleTv.text = header.headerText
            helpView.setOnClickListener(DebounceClickListener(debounceClickHandler) {
                helpClickListener()
            })
        }
    }

    class ActivityDateViewHolder(
        itemView: View
    ) : ActivityFeedViewHolder(itemView) {

        fun bind(header: ActivityDate) {
            (itemView as TextView).text = header.date
        }
    }
}