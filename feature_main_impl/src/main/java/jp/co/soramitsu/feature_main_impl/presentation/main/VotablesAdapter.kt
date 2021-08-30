/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.ItemReferendumBinding
import jp.co.soramitsu.feature_main_impl.presentation.main.VotablesAdapter.ReferendumHandler
import jp.co.soramitsu.feature_main_impl.presentation.util.DeadlineFormatter
import jp.co.soramitsu.feature_main_impl.presentation.util.loadImage
import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum

class VotablesAdapter(
    private val numbersFormatter: NumbersFormatter,
    private val debounceClickHandler: DebounceClickHandler,
    private val referendumHandler: ReferendumHandler,
    private val dateTimeFormatter: DateTimeFormatter
) : ListAdapter<Votable, VotableHolder<out Votable, out Any>>(DiffCallback) {

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
            R.layout.item_referendum -> ReferendumViewHolder(
                view,
                referendumHandler,
                dateTimeFormatter
            )
            else -> throw IllegalArgumentException("Unknown votable")
        }
    }

    override fun getItemViewType(position: Int) =
        when (getItem(position)) {
            is Referendum -> R.layout.item_referendum
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
        }
    }

    override fun onViewRecycled(holder: VotableHolder<out Votable, out Any>) {
        holder.unbind()
    }
}

abstract class VotableHolder<T : Votable, H>(
    containerView: View,
    protected val handler: H
) : RecyclerView.ViewHolder(containerView) {
    abstract fun bind(
        votable: T,
        numbersFormatter: NumbersFormatter,
        debounceClickHandler: DebounceClickHandler,
        dateTimeFormatter: DateTimeFormatter
    )

    open fun unbind() {
    }
}

class ReferendumViewHolder(
    itemView: View,
    handler: ReferendumHandler,
    dateTimeFormatter: DateTimeFormatter
) :
    VotableHolder<Referendum, ReferendumHandler>(itemView, handler) {

    private val binding = ItemReferendumBinding.bind(itemView)

    private val deadlineFormatter = DeadlineFormatter(
        binding.referendumDeadlineLabel,
        binding.referendumDeadline,
        dateTimeFormatter
    ) {
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
            if (imageLink.isEmpty()) {
                binding.referendumImage.gone()
            } else {
                binding.referendumImage.show()
                binding.referendumImage.loadImage(imageLink)
            }

            deadlineFormatter.setReferendum(this)

            binding.referendumTitle.text = name
            binding.referendumDescription.text = description
            binding.referendumVsLine.percentage = supportingPercentage
            binding.referendumYesCount.text = numbersFormatter.formatInteger(supportVotes)
            binding.referendumNoCount.text = numbersFormatter.formatInteger(opposeVotes)

            binding.referendumContainer.setDebouncedClickListener(debounceClickHandler) {
                handler.referendumClicked(votable)
            }

            binding.referendumVoteFor.isEnabled = isOpen
            binding.referendumVoteAgainst.isEnabled = isOpen

            binding.referendumVoteFor.setOnClickListener {
                handler.referendumVoteForClicked(votable)
            }

            binding.referendumVoteAgainst.setOnClickListener {
                handler.referendumVoteAgainstClicked(votable)
            }
        }
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
