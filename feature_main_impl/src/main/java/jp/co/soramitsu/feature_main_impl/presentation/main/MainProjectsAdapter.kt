/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.makeramen.roundedimageview.RoundedImageView
import com.squareup.picasso.Picasso
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.util.formatToClosedProjectDate
import jp.co.soramitsu.feature_main_impl.presentation.util.formatToOpenProjectDate
import jp.co.soramitsu.feature_project_api.domain.model.Project
import jp.co.soramitsu.feature_project_api.domain.model.ProjectStatus
import java.math.BigDecimal

class MainProjectsAdapter(
    private val numbersFormatter: NumbersFormatter,
    private val debounceClickHandler: DebounceClickHandler,
    private val voteButtonClickListener: (Project) -> Unit,
    private val favButtonClickListener: (Project) -> Unit,
    private val itemViewClickListener: (Project) -> Unit
) : ListAdapter<Project, ProjectViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ProjectViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_project, viewGroup, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(projectViewHolder: ProjectViewHolder, position: Int) {
        projectViewHolder.bind(getItem(position), numbersFormatter, debounceClickHandler, voteButtonClickListener, favButtonClickListener, itemViewClickListener)
    }
}

class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val root: CardView = itemView.findViewById(R.id.item_wrapper)
    private val cardNew: CardView = itemView.findViewById(R.id.card_new)
    private val imageView: RoundedImageView = itemView.findViewById(R.id.image)
    private val voteTv: TextView = itemView.findViewById(R.id.voteTv)
    private val favoriteTv: TextView = itemView.findViewById(R.id.favoriteTv)
    private val nameTv: TextView = itemView.findViewById(R.id.name)
    private val descriptionTv: TextView = itemView.findViewById(R.id.descriptionTv)
    private val votesProgressBar: ProgressBar = itemView.findViewById(R.id.progressbar_votes)
    private val leftToFundTv: TextView = itemView.findViewById(R.id.leftToFundTv)
    private val daysLeftTv: TextView = itemView.findViewById(R.id.days_left)
    private val divider1: View = itemView.findViewById(R.id.divider1)
    private val divider2: View = itemView.findViewById(R.id.divider2)
    private val rewardTv: TextView = itemView.findViewById(R.id.reward)
    private val friendsVotedTv: TextView = itemView.findViewById(R.id.friends_voted)

    fun bind(
        project: Project,
        numbersFormatter: NumbersFormatter,
        debounceClickHandler: DebounceClickHandler,
        voteButtonClickListener: (Project) -> Unit,
        favButtonClickListener: (Project) -> Unit,
        itemViewClickListener: (Project) -> Unit
    ) {
        Picasso.get().load(project.image.toString()).fit().centerCrop().into(imageView)

        nameTv.text = project.name
        descriptionTv.text = project.description

        if (project.isUnwatched) cardNew.show() else cardNew.gone()

        val foundedPercent = project.getFundingPercent().toString()

        favoriteTv.text = if (project.favoriteCount == 0) "" else project.favoriteCount.toString()

        val favoriteIconDrawable = itemView.resources.getDrawable(if (project.isFavorite) R.drawable.icon_fav_filled else R.drawable.icon_fav_shape)

        votesProgressBar.progress = foundedPercent.toInt()

        favoriteTv.setCompoundDrawablesWithIntrinsicBounds(null, null, favoriteIconDrawable, null)

        root.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            itemViewClickListener(project)
        })

        voteTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            voteButtonClickListener(project)
        })

        val scaleAnimation = AnimationUtils.loadAnimation(itemView.context, R.anim.scale_animation)

        if (ProjectStatus.OPEN == project.status) {
            divider1.gone()
            divider2.show()

            if (project.votedFriendsCount == 0) {
                friendsVotedTv.gone()
            } else {
                friendsVotedTv.show()
                friendsVotedTv.text = itemView.context.resources.getQuantityString(
                    R.plurals.project_friends_template,
                    project.votedFriendsCount,
                    project.votedFriendsCount

                )
            }

            rewardTv.gone()

            voteTv.isClickable = true
            val typeFace = ResourcesCompat.getFont(itemView.context, R.font.sora_semibold)
            voteTv.setTextColor(itemView.context.resources.getColor(R.color.lightRed))
            voteTv.typeface = typeFace

            votesProgressBar.show()

            leftToFundTv.text = itemView.context.getString(R.string.project_founded_template, foundedPercent,
                numbersFormatter.formatInteger(BigDecimal.valueOf(project.fundingTarget)))
            daysLeftTv.text = project.deadline.formatToOpenProjectDate(itemView.context.resources)
        } else {
            divider1.show()
            divider2.gone()

            friendsVotedTv.gone()

            if (project.votes == BigDecimal.ZERO) {
                rewardTv.gone()
            } else {
                rewardTv.text = itemView.context.getString(R.string.project_spent_format, project.votes.toString())
                rewardTv.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                rewardTv.show()
            }

            voteTv.isClickable = false
            val typeFace = ResourcesCompat.getFont(itemView.context, R.font.sora_regular)
            voteTv.setTextColor(itemView.resources.getColor(R.color.darkGreyBlue))
            voteTv.typeface = typeFace

            votesProgressBar.gone()

            leftToFundTv.text = itemView.context.getString(R.string.project_votes_template, numbersFormatter.format(project.fundingCurrent.toDouble()))
            daysLeftTv.text = project.statusUpdateTime.formatToClosedProjectDate(itemView.context.resources)
        }

        when (project.status) {
            ProjectStatus.COMPLETED -> {
                voteTv.setText(R.string.project_successful_voting)
                voteTv.setCompoundDrawablesWithIntrinsicBounds(itemView.context.getDrawable(R.drawable.icon_succ_voting), null, null, null)
            }
            ProjectStatus.FAILED -> {
                voteTv.setText(R.string.project_unsuccessful_voting)
                voteTv.setCompoundDrawablesWithIntrinsicBounds(itemView.context.getDrawable(R.drawable.icon_failed), null, null, null)
            }
            ProjectStatus.OPEN -> {
                if (project.votes.toInt() == 0) {
                    voteTv.text = itemView.context.getString(R.string.common_vote)
                    voteTv.setCompoundDrawablesWithIntrinsicBounds(itemView.context.getDrawable(R.drawable.icon_vote_shape), null, null, null)
                } else {
                    voteTv.text = numbersFormatter.formatInteger(project.votes)
                    voteTv.setCompoundDrawablesWithIntrinsicBounds(itemView.context.getDrawable(R.drawable.icon_vote_filled), null, null, null)
                }
            }
        }

        favoriteTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            favButtonClickListener(project)
            favoriteTv.startAnimation(scaleAnimation)
        })
    }
}

object DiffCallback : DiffUtil.ItemCallback<Project>() {
    override fun areItemsTheSame(oldItem: Project, newItem: Project): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Project, newItem: Project): Boolean {
        return oldItem == newItem
    }
}