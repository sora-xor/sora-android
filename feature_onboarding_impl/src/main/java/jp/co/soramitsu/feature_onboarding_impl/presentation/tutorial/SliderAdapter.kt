package jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import jp.co.soramitsu.feature_onboarding_impl.R

class SliderAdapter : PagerAdapter() {

    companion object {
        val SLIDES = listOf(
            R.drawable.tutorial_one,
            R.drawable.tutorial_two,
            R.drawable.tutorial_three
        )
        val DESC = listOf(
            R.string.tutorial_projects_desc,
            R.string.tutorial_votes_desc,
            R.string.tutorial_project_success_desc
        )
    }

    override fun isViewFromObject(p0: View, p1: Any): Boolean {
        return p0 === p1
    }

    override fun getCount(): Int {
        return SLIDES.size
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val context = container.context
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.item_slider, null)

        val description = view.findViewById(R.id.tutorialDescriptionTextView) as TextView
        val image = view.findViewById(R.id.tutorialPictureImageView) as ImageView

        description.text = context.getText(DESC[position])
        image.setImageDrawable(context.getDrawable(SLIDES[position]))

        val viewPager = container as ViewPager
        viewPager.addView(view, 0)

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val viewPager = container as ViewPager
        val view = `object` as View
        viewPager.removeView(view)
    }
}