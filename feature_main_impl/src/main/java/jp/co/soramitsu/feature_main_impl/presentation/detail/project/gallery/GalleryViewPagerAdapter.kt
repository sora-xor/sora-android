package jp.co.soramitsu.feature_main_impl.presentation.detail.project.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.squareup.picasso.Picasso
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_votable_api.domain.model.project.GalleryItem
import jp.co.soramitsu.feature_votable_api.domain.model.project.GalleryItemType
import kotlinx.android.synthetic.main.view_gallery_page.view.galleryImg
import kotlinx.android.synthetic.main.view_gallery_page.view.playImg

class GalleryViewPagerAdapter(
    private val gallery: List<GalleryItem>
) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = LayoutInflater.from(container.context).inflate(R.layout.view_gallery_page, container, false)

        val galleryItem = gallery[position]

        if (GalleryItemType.VIDEO == galleryItem.type) {
            if (galleryItem.preview.isEmpty()) {
                view.galleryImg.setBackgroundColor(Color.BLACK)
                view.galleryImg.setImageResource(0)
            } else {
                view.galleryImg.setBackgroundColor(Color.TRANSPARENT)
                Picasso.get()
                    .load(gallery[position].preview)
                    .fit()
                    .centerInside()
                    .into(view.galleryImg)
            }
            view.playImg.show()
        } else {
            Picasso.get()
                .load(gallery[position].url)
                .fit()
                .centerInside()
                .into(view.galleryImg)
            view.playImg.gone()
        }

        view.setOnClickListener { if (GalleryItemType.VIDEO == galleryItem.type) openVideo(container.context, galleryItem.url) }

        container.addView(view)

        return view
    }

    private fun openVideo(context: Context, videoUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(videoUrl), "video/*")
        }
        context.startActivity(intent)
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj
    }

    override fun getCount(): Int {
        return gallery.size
    }
}