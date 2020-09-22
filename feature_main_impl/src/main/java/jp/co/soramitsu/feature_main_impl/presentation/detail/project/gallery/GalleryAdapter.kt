/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.project.gallery

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_votable_api.domain.model.project.GalleryItem
import jp.co.soramitsu.feature_votable_api.domain.model.project.GalleryItemType
import kotlinx.android.synthetic.main.gallery_item.view.durationView
import kotlinx.android.synthetic.main.gallery_item.view.galleryItemImageView
import kotlinx.android.synthetic.main.gallery_item.view.playImg

class GalleryAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val itemClickedListener: (GalleryItem, View, Int) -> Unit
) : ListAdapter<GalleryItem, GalleryViewHolder>(DiffCallback) {

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(getItem(position), debounceClickHandler, itemClickedListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.gallery_item, parent, false)
        return GalleryViewHolder(view)
    }
}

class GalleryViewHolder(
    view: View
) : RecyclerView.ViewHolder(view) {

    fun bind(item: GalleryItem, debounceClickHandler: DebounceClickHandler, itemClickedListener: (GalleryItem, View, Int) -> Unit) {
        with(itemView) {

            if (GalleryItemType.VIDEO == item.type) {
                if (item.preview.isEmpty()) {
                    galleryItemImageView.setBackgroundColor(Color.BLACK)
                    galleryItemImageView.setImageResource(0)
                } else {
                    galleryItemImageView.setBackgroundColor(Color.TRANSPARENT)
                    Picasso.get()
                        .load(item.preview)
                        .placeholder(R.color.lighterGrey)
                        .fit()
                        .centerCrop()
                        .into(galleryItemImageView)
                }
                playImg.show()
                durationView.show()
                durationView.setDuration(item.duration)
            } else {
                Picasso.get()
                    .load(item.url)
                    .placeholder(R.color.lighterGrey)
                    .fit()
                    .centerCrop()
                    .into(galleryItemImageView)
                playImg.gone()
                durationView.gone()
            }

            setOnClickListener(DebounceClickListener(debounceClickHandler) {
                itemClickedListener(item, galleryItemImageView, adapterPosition)
            })
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<GalleryItem>() {

    override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
        return oldItem.url == newItem.url
    }

    override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
        return oldItem == newItem
    }
}