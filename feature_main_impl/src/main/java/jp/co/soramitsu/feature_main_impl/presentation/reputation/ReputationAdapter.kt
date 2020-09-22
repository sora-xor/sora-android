package jp.co.soramitsu.feature_main_impl.presentation.reputation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_main_impl.R

class ReputationAdapter : ListAdapter<String, ReputationListViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ReputationListViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_reputation_list, viewGroup, false)
        return ReputationListViewHolder(view)
    }

    override fun onBindViewHolder(reputationListViewHolder: ReputationListViewHolder, position: Int) {
        reputationListViewHolder.bind(getItem(position))
    }
}

class ReputationListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val itemBullet: TextView = itemView.findViewById(R.id.item_bullet)
    private val itemText: TextView = itemView.findViewById(R.id.item_text)

    fun bind(string: String) {
        val positionText = (layoutPosition + 1).toString()
        itemBullet.text = positionText
        itemText.text = string
    }
}

object DiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }
}