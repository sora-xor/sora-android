/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.gallery

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import kotlinx.android.synthetic.main.activity_gallery.galleryViewPager

class GalleryActivity : AppCompatActivity() {

    companion object {
        private const val KEY_GALLERY = "gallery"
        private const val KEY_SELECTED_INDEX = "selected_index"

        fun start(activity: Activity, gallery: List<GalleryItem>, selectedIndex: Int) {
            val galleryArray = arrayListOf<GalleryItem>().apply { addAll(gallery) }
            val intent = Intent(activity, GalleryActivity::class.java).apply {
                putExtra(KEY_SELECTED_INDEX, selectedIndex)
                putParcelableArrayListExtra(KEY_GALLERY, galleryArray)
            }
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val gallery = intent.getParcelableArrayListExtra<GalleryItem>(KEY_GALLERY)

        val viewPager = GalleryViewPagerAdapter(gallery)
        galleryViewPager.adapter = viewPager
        galleryViewPager.setCurrentItem(intent.getIntExtra(KEY_SELECTED_INDEX, 0), false)
    }
}