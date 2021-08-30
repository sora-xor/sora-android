package jp.co.soramitsu.common.presentation.view.chooserbottomsheet

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.R

class ChooserAdapter(private val dialogChooserBottomSheet: ChooserBottomSheet) : ListAdapter<ChooserItem, ChooserViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return R.layout.item_chooser
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ChooserViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_chooser, viewGroup, false)

        return ChooserViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChooserViewHolder, position: Int) {
        holder.bind(getItem(position), dialogChooserBottomSheet)
    }
}

class ChooserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val title: TextView = itemView.findViewById(R.id.chooserItemText)

    fun bind(item: ChooserItem, dialogChooserBottomSheet: ChooserBottomSheet) {
        title.setText(item.title)
        title.setOnClickListener { item.clickHandler(); dialogChooserBottomSheet.dismiss() }

        if (item.selected) {
            title.setCompoundDrawablesWithIntrinsicBounds(item.icon, 0, R.drawable.ic_checkmark_24, 0)
        } else {
            title.setCompoundDrawablesWithIntrinsicBounds(item.icon, 0, 0, 0)
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<ChooserItem>() {
    override fun areItemsTheSame(oldItem: ChooserItem, newItem: ChooserItem): Boolean {
        return when {
            oldItem is ChooserItem && newItem is ChooserItem -> oldItem.title == newItem.title
            else -> false
        }
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: ChooserItem, newItem: ChooserItem): Boolean {
        return when {
            oldItem is ChooserItem && newItem is ChooserItem -> oldItem == newItem
            else -> false
        }
    }
}
