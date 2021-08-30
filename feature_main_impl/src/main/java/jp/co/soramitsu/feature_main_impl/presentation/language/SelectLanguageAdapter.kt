package jp.co.soramitsu.feature_main_impl.presentation.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.language.model.LanguageItem

class SelectLanguageAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val itemClickListener: (LanguageItem) -> Unit
) : ListAdapter<LanguageItem, LanguageViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): LanguageViewHolder {
        return LanguageViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_language, viewGroup, false))
    }

    override fun onBindViewHolder(languageViewHolder: LanguageViewHolder, position: Int) {
        languageViewHolder.bind(getItem(position), debounceClickHandler, itemClickListener)
    }
}

class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val languageNameTv: TextView = itemView.findViewById(R.id.languageNameTv)
    private val languageNativeNameTv: TextView = itemView.findViewById(R.id.languageNativeNameTv)

    fun bind(language: LanguageItem, debounceClickHandler: DebounceClickHandler, itemClickListener: (LanguageItem) -> Unit) {
        with(itemView) {
            languageNameTv.text = language.displayName
            languageNativeNameTv.text = language.nativeDisplayName

            setOnClickListener(
                DebounceClickListener(debounceClickHandler) {
                    itemClickListener(language)
                }
            )

            if (language.isSelected) {
                languageNameTv.setTextColor(ContextCompat.getColor(context, R.color.uikit_lightRed))
                languageNameTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_checkmark, 0)
            } else {
                languageNameTv.setTextColor(ContextCompat.getColor(context, R.color.lightBlack))
                languageNameTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<LanguageItem>() {
    override fun areItemsTheSame(oldItem: LanguageItem, newItem: LanguageItem): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: LanguageItem, newItem: LanguageItem): Boolean {
        return oldItem == newItem
    }
}
