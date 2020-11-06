/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.main.VotablesAdapter.ProjectHandler
import jp.co.soramitsu.feature_main_impl.presentation.main.VotablesAdapter.ReferendumHandler
import jp.co.soramitsu.feature_main_impl.presentation.util.DeadlineFormatter
import jp.co.soramitsu.feature_main_impl.presentation.util.loadImage
import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import jp.co.soramitsu.feature_votable_api.domain.model.project.Project
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectStatus
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_project.cardNew
import kotlinx.android.synthetic.main.item_project.daysLeft
import kotlinx.android.synthetic.main.item_project.description
import kotlinx.android.synthetic.main.item_project.divider1
import kotlinx.android.synthetic.main.item_project.divider2
import kotlinx.android.synthetic.main.item_project.favorite
import kotlinx.android.synthetic.main.item_project.friendsVoted
import kotlinx.android.synthetic.main.item_project.image
import kotlinx.android.synthetic.main.item_project.itemContainer
import kotlinx.android.synthetic.main.item_project.leftToFund
import kotlinx.android.synthetic.main.item_project.name
import kotlinx.android.synthetic.main.item_project.progressbarVotes
import kotlinx.android.synthetic.main.item_project.reward
import kotlinx.android.synthetic.main.item_project.vote
import kotlinx.android.synthetic.main.item_referendum.referendumContainer
import kotlinx.android.synthetic.main.item_referendum.referendumDeadline
import kotlinx.android.synthetic.main.item_referendum.referendumDeadlineLabel
import kotlinx.android.synthetic.main.item_referendum.referendumDescription
import kotlinx.android.synthetic.main.item_referendum.referendumImage
import kotlinx.android.synthetic.main.item_referendum.referendumNoCount
import kotlinx.android.synthetic.main.item_referendum.referendumTitle
import kotlinx.android.synthetic.main.item_referendum.referendumVoteAgainst
import kotlinx.android.synthetic.main.item_referendum.referendumVoteFor
import kotlinx.android.synthetic.main.item_referendum.referendumVsLine
import kotlinx.android.synthetic.main.item_referendum.referendumYesCount
import java.math.BigDecimal

class VotablesAdapter(
    private val numbersFormatter: NumbersFormatter,
    private val debounceClickHandler: DebounceClickHandler,
    private val projectHandler: ProjectHandler,
    private val referendumHandler: ReferendumHandler,
    private val dateTimeFormatter: DateTimeFormatter
) : ListAdapter<Votable, VotableHolder<out Votable, out Any>>(DiffCallback) {

    interface ProjectHandler {
        fun projectVoteClicked(project: Project)
        fun projectFavouriteClicked(project: Project)
        fun projectClicked(project: Project)
    }

    interface ReferendumHandler {
        fun referendumClicked(referendum: Referendum)
        fun referendumVoteForClicked(referendum: Referendum)
        fun referendumVoteAgainstClicked(referendum: Referendum)
        fun onDeadline(id: String)
    }

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        viewType: Int
    ): VotableHolder<out Votable, out Any> {
        val view = LayoutInflater.from(viewGroup.context).inflate(viewType, viewGroup, false)

        return when (viewType) {
            R.layout.item_project -> ProjectViewHolder(view, projectHandler)
            R.layout.item_referendum -> ReferendumViewHolder(view, referendumHandler, dateTimeFormatter)
            else -> throw IllegalArgumentException("Unknown votable")
        }
    }

    override fun getItemViewType(position: Int) =
        when (getItem(position)) {
            is Referendum -> R.layout.item_referendum
            is Project -> R.layout.item_project
            else -> throw IllegalArgumentException("Unknown votable type")
        }

    override fun onBindViewHolder(holder: VotableHolder<out Votable, out Any>, position: Int) {
        when (holder) {
            is ReferendumViewHolder -> holder.bind(
                getItem(position) as Referendum,
                numbersFormatter,
                debounceClickHandler,
                dateTimeFormatter
            )
            is ProjectViewHolder -> holder.bind(
                getItem(position) as Project,
                numbersFormatter,
                debounceClickHandler,
                dateTimeFormatter
            )
        }
    }

    override fun onViewRecycled(holder: VotableHolder<out Votable, out Any>) {
        holder.unbind()
    }
}

abstract class VotableHolder<T : Votable, H>(
    override val containerView: View,
    protected val handler: H
) : RecyclerView.ViewHolder(containerView), LayoutContainer {
    abstract fun bind(
        votable: T,
        numbersFormatter: NumbersFormatter,
        debounceClickHandler: DebounceClickHandler,
        dateTimeFormatter: DateTimeFormatter
    )

    open fun unbind() {
    }
}

class ReferendumViewHolder(itemView: View, handler: ReferendumHandler, dateTimeFormatter: DateTimeFormatter) :
    VotableHolder<Referendum, ReferendumHandler>(itemView, handler) {
    private val deadlineFormatter = DeadlineFormatter(referendumDeadlineLabel, referendumDeadline, dateTimeFormatter) {
        handler.onDeadline(it)
    }

    override fun unbind() {
        deadlineFormatter.release()
    }

    override fun bind(
        votable: Referendum,
        numbersFormatter: NumbersFormatter,
        debounceClickHandler: DebounceClickHandler,
        dateTimeFormatter: DateTimeFormatter
    ) {
        with(votable) {
            referendumImage.loadImage(imageLink)

            deadlineFormatter.setReferendum(this)

            referendumTitle.text = name
            referendumDescription.text = description
            referendumVsLine.percentage = supportingPercentage
            referendumYesCount.text = numbersFormatter.formatInteger(supportVotes)
            referendumNoCount.text = numbersFormatter.formatInteger(opposeVotes)

            referendumContainer.setDebouncedClickListener(debounceClickHandler) {
                handler.referendumClicked(votable)
            }

            referendumVoteFor.isEnabled = isOpen
            referendumVoteAgainst.isEnabled = isOpen

            referendumVoteFor.setOnClickListener {
                handler.referendumVoteForClicked(votable)
            }

            referendumVoteAgainst.setOnClickListener {
                handler.referendumVoteAgainstClicked(votable)
            }
        }
    }
}

class ProjectViewHolder(itemView: View, handler: ProjectHandler) :
    VotableHolder<Project, ProjectHandler>(itemView, handler) {

    override fun bind(
        votable: Project,
        numbersFormatter: NumbersFormatter,
        debounceClickHandler: DebounceClickHandler,
        dateTimeFormatter: DateTimeFormatter
    ) {
        image.loadImage(votable.image.toString())

        name.text = votable.name
        description.text = votable.description

        if (votable.isUnwatched) cardNew.show() else cardNew.gone()

        val foundedPercent = votable.getFundingPercent().toString()

        favorite.text = if (votable.favoriteCount == 0) "" else votable.favoriteCount.toString()

        val favoriteIconRes =
            if (votable.isFavorite) R.drawable.icon_fav_filled else R.drawable.icon_fav_shape
        val favoriteIconDrawable = ContextCompat.getDrawable(containerView.context, favoriteIconRes)

        progressbarVotes.progress = foundedPercent.toInt()

        favorite.setCompoundDrawablesWithIntrinsicBounds(null, null, favoriteIconDrawable, null)

        itemContainer.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            handler.projectClicked(votable)
        })

        vote.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            handler.projectVoteClicked(votable)
        })

        val scaleAnimation = AnimationUtils.loadAnimation(itemView.context, R.anim.scale_animation)

        if (ProjectStatus.OPEN == votable.status) {
            divider1.gone()
            divider2.show()

            if (votable.votedFriendsCount == 0) {
                friendsVoted.gone()
            } else {
                friendsVoted.show()
                friendsVoted.text = itemView.context.resources.getQuantityString(
                    R.plurals.project_friends_template,
                    votable.votedFriendsCount,
                    votable.votedFriendsCount

                )
            }

            reward.gone()

            vote.isClickable = true
            val typeFace = ResourcesCompat.getFont(itemView.context, R.font.sora_semibold)
            vote.setTextColor(itemView.context.resources.getColor(R.color.uikit_lightRed))
            vote.typeface = typeFace

            progressbarVotes.show()

            leftToFund.text = itemView.context.getString(
                R.string.project_founded_template, foundedPercent,
                numbersFormatter.formatInteger(BigDecimal.valueOf(votable.fundingTarget))
            )
            daysLeft.text = dateTimeFormatter.formatToOpenVotableDateString(votable.deadline.time)
        } else {
            divider1.show()
            divider2.gone()

            friendsVoted.gone()

            if (votable.votes == BigDecimal.ZERO) {
                reward.gone()
            } else {
                reward.text = itemView.context.getString(
                    R.string.project_spent_format,
                    votable.votes.toString()
                )
                reward.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                reward.show()
            }

            vote.isClickable = false
            val typeFace = ResourcesCompat.getFont(itemView.context, R.font.sora_regular)
            vote.setTextColor(itemView.resources.getColor(R.color.darkGreyBlue))
            vote.typeface = typeFace

            progressbarVotes.gone()

            leftToFund.text = itemView.context.getString(
                R.string.project_votes_template,
                numbersFormatter.format(votable.fundingCurrent)
            )
            daysLeft.text = dateTimeFormatter.formatToClosedVotableDateString(votable.statusUpdateTime.time)
        }

        when (votable.status) {
            ProjectStatus.COMPLETED -> {
                vote.setText(R.string.project_successful_voting)
                vote.setCompoundDrawablesWithIntrinsicBounds(
                    itemView.context.getDrawable(R.drawable.icon_succ_voting),
                    null,
                    null,
                    null
                )
            }
            ProjectStatus.FAILED -> {
                vote.setText(R.string.project_unsuccessful_voting)
                vote.setCompoundDrawablesWithIntrinsicBounds(
                    itemView.context.getDrawable(R.drawable.icon_failed),
                    null,
                    null,
                    null
                )
            }
            ProjectStatus.OPEN -> {
                if (votable.votes.toInt() == 0) {
                    vote.text = itemView.context.getString(R.string.common_vote)
                    vote.setCompoundDrawablesWithIntrinsicBounds(
                        itemView.context.getDrawable(R.drawable.icon_vote_shape),
                        null,
                        null,
                        null
                    )
                } else {
                    vote.text = numbersFormatter.formatInteger(votable.votes)
                    vote.setCompoundDrawablesWithIntrinsicBounds(
                        itemView.context.getDrawable(R.drawable.icon_vote_filled),
                        null,
                        null,
                        null
                    )
                }
            }
        }

        favorite.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            handler.projectFavouriteClicked(votable)
            favorite.startAnimation(scaleAnimation)
        })
    }
}

object DiffCallback : DiffUtil.ItemCallback<Votable>() {
    override fun areItemsTheSame(oldItem: Votable, newItem: Votable): Boolean {
        return oldItem::class == newItem::class && oldItem.id == newItem.id
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Votable, newItem: Votable): Boolean {
        return oldItem.isSameAs(newItem)
    }
}