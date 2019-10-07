/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.gallery

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItemType
import kotlinx.android.synthetic.main.gallery_item.view.durationView
import kotlinx.android.synthetic.main.gallery_item.view.galleryItemImageView
import kotlinx.android.synthetic.main.gallery_item.view.playImg

class GalleryAdapter(
    private val itemClickedListener: (GalleryItem, View, Int) -> Unit
) : ListAdapter<GalleryItem, GalleryViewHolder>(DiffCallback) {

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.gallery_item, parent, false)
        return GalleryViewHolder(view, itemClickedListener)
    }
}

class GalleryViewHolder(
    view: View,
    private val itemClickedListener: (GalleryItem, View, Int) -> Unit
) : RecyclerView.ViewHolder(view) {

    fun bind(item: GalleryItem) {
        with(itemView) {

            if (GalleryItemType.VIDEO == item.type) {
                if (item.preview.isEmpty()) {
                    galleryItemImageView.setBackgroundColor(Color.BLACK)
                    galleryItemImageView.setImageResource(0)
                } else {
                    galleryItemImageView.setBackgroundColor(Color.TRANSPARENT)
                    Picasso.get()
                        .load(item.preview)
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
                    .fit()
                    .centerCrop()
                    .into(galleryItemImageView)
                playImg.gone()
                durationView.gone()
            }

            setOnClickListener { itemClickedListener(item, galleryItemImageView, adapterPosition) }
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