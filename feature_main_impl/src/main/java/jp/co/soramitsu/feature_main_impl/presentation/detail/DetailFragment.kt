/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff.Mode
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearSnapHelper
import com.squareup.picasso.Picasso
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.EllipsizeUtil
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.createSendEmailIntent
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showBrowser
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.detail.gallery.GalleryAdapter
import jp.co.soramitsu.feature_main_impl.presentation.util.CustomBottomSheetDialog
import jp.co.soramitsu.feature_main_impl.presentation.util.formatToClosedProjectDate
import jp.co.soramitsu.feature_main_impl.presentation.util.formatToOpenProjectDate
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import jp.co.soramitsu.feature_project_api.domain.model.ProjectStatus
import kotlinx.android.synthetic.main.fragment_project_detail.descriptionTv
import kotlinx.android.synthetic.main.fragment_project_detail.discussLinkTextView
import kotlinx.android.synthetic.main.fragment_project_detail.emailTv
import kotlinx.android.synthetic.main.fragment_project_detail.favImg
import kotlinx.android.synthetic.main.fragment_project_detail.favoritesCountTv
import kotlinx.android.synthetic.main.fragment_project_detail.friendsVotedTv
import kotlinx.android.synthetic.main.fragment_project_detail.preloaderView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDaysLeftTv
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailAddToFavouritesButton
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailGalleryRecyclerView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailGalleryTextView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailHeaderImageView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailProgressTextView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailSeparateLineView0
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailSeparateLineView3
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailTitleTextView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailVoteButton
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailVotesProgressBar
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailsVoteButtonIcon
import kotlinx.android.synthetic.main.fragment_project_detail.projectView
import kotlinx.android.synthetic.main.fragment_project_detail.projectVoteTv
import kotlinx.android.synthetic.main.fragment_project_detail.rewardTv
import kotlinx.android.synthetic.main.fragment_project_detail.toolbar
import kotlinx.android.synthetic.main.fragment_project_detail.votesAndFavoritesView
import kotlinx.android.synthetic.main.fragment_project_detail.webSiteTv
import java.math.BigDecimal
import javax.inject.Inject

class DetailFragment : BaseFragment<DetailViewModel>(), KeyboardHelper.KeyboardListener {

    companion object {
        private const val KEY_PROJECT_ID = "project_id"

        fun createBundle(projectId: String): Bundle {
            return Bundle().apply { putString(KEY_PROJECT_ID, projectId) }
        }
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private var keyboardHelper: KeyboardHelper? = null

    private var voteDialog: CustomBottomSheetDialog? = null

    @Inject lateinit var numbersFormatter: NumbersFormatter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_project_detail, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .detailComponentBuilder()
            .withFragment(this)
            .withProjectId(arguments!!.getString(KEY_PROJECT_ID, ""))
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        toolbar.setHomeButtonListener { viewModel.backPressed() }
        toolbar.setOnVotesClickListener { viewModel.votesClicked() }

        val scaleAnimation = AnimationUtils.loadAnimation(activity, R.anim.scale_animation)

        projectDetailAddToFavouritesButton.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            projectDetailAddToFavouritesButton.startAnimation(scaleAnimation)
            viewModel.favoriteClicked()
        })

        discussLinkTextView.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.discussionLinkClicked()
        })

        webSiteTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.websiteClicked()
        })

        emailTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.emailClicked()
        })

        projectDetailVoteButton.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.voteClicked()
        })
    }

    override fun subscribe(viewModel: DetailViewModel) {
        observe(viewModel.projectDetailsLiveData, Observer {
            showProject(it)
            showRewardByCurrency(it)
        })

        observe(viewModel.galleryLiveData, Observer {
            if (it.isEmpty()) {
                projectDetailGalleryTextView.gone()
                projectDetailGalleryRecyclerView.gone()
            } else {
                projectDetailGalleryTextView.show()
                projectDetailGalleryRecyclerView.show()
                if (projectDetailGalleryRecyclerView.adapter == null) {
                    projectDetailGalleryRecyclerView.adapter = GalleryAdapter(debounceClickHandler, galleryItemClickListener)
                    LinearSnapHelper().attachToRecyclerView(projectDetailGalleryRecyclerView)
                }
                (projectDetailGalleryRecyclerView.adapter as GalleryAdapter).submitList(it)
            }
        })

        observe(viewModel.votesFormattedLiveData, Observer {
            toolbar.setVotes(it)
            toolbar.showVotes()
        })

        observe(viewModel.playVideoLiveData, EventObserver {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(it), "video/*")
            }
            activity?.packageManager?.let { packageManager ->
                if (intent.resolveActivity(packageManager) == null) {
                    Toast.makeText(activity!!, R.string.project_no_video_app_installed_error, Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(intent)
                }
            }
        })

        observe(viewModel.showVoteProjectLiveData, EventObserver {
            voteDialog = CustomBottomSheetDialog(
                activity!!,
                CustomBottomSheetDialog.MaxVoteType.PROJECT_NEED,
                it,
                { viewModel.voteForProject(it) },
                {
                    if (keyboardHelper!!.isKeyboardShowing) {
                        hideSoftKeyboard(activity)
                    } else {
                        openSoftKeyboard(it)
                    }
                },
                debounceClickHandler
            )
            voteDialog!!.show()
        })

        observe(viewModel.showVoteUserLiveData, EventObserver {
            voteDialog = CustomBottomSheetDialog(
                activity!!,
                CustomBottomSheetDialog.MaxVoteType.USER_CAN_GIVE,
                it,
                { viewModel.voteForProject(it) },
                {
                    if (keyboardHelper!!.isKeyboardShowing) {
                        hideSoftKeyboard(activity)
                    } else {
                        openSoftKeyboard(it)
                    }
                },
                debounceClickHandler
            )
            voteDialog!!.show()
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) {
                projectView.gone()
                preloaderView.show()
            } else {
                preloaderView.gone()
                projectView.show()
            }
        })

        observe(viewModel.sendEmailEvent, EventObserver {
            activity?.createSendEmailIntent(it, getString(R.string.common_select_email_app_title))
        })

        observe(viewModel.friendsVotedLiveData, Observer {
            if (it.isEmpty()) friendsVotedTv.gone() else friendsVotedTv.show()
            friendsVotedTv.text = it
        })

        observe(viewModel.favoritesLiveData, Observer {
            if (it.isEmpty()) favoritesCountTv.gone() else favoritesCountTv.show()
            favoritesCountTv.text = it
        })

        observe(viewModel.projectDescriptionLiveData, Observer {
            descriptionTv.text = it
        })

        observe(viewModel.votesAndFavoritesVisibility, Observer {
            if (it) {
                projectDetailSeparateLineView0.show()
                votesAndFavoritesView.show()
            } else {
                projectDetailSeparateLineView0.gone()
                votesAndFavoritesView.gone()
            }
        })

        observe(viewModel.showBrowserLiveData, EventObserver {
            showBrowser(it)
        })

        viewModel.getVotes(false)
        viewModel.updateProject()
    }

    private fun showProject(project: ProjectDetails) {
        projectDetailTitleTextView.text = project.name
        projectDetailVotesProgressBar.progress = project.getFundingPercent()

        val favIcon = if (project.isFavorite) {
            ContextCompat.getDrawable(activity!!, R.drawable.icon_fav_filled)
        } else {
            ContextCompat.getDrawable(activity!!, R.drawable.icon_fav_shape)
        }
        favImg.setImageDrawable(favIcon)

        webSiteTv.text = EllipsizeUtil.ellipsizeMiddle(project.projectLink.toString())
        emailTv.text = EllipsizeUtil.ellipsizeMiddle(project.email)

        if (project.status == ProjectStatus.OPEN) {
            projectDetailVoteButton.isClickable = true
            projectDetailVoteButton.setCardBackgroundColor(ContextCompat.getColor(activity!!, R.color.lightRed))

            projectDetailProgressTextView.text = getString(
                R.string.project_founded_template,
                project.getFundingPercent().toString(),
                numbersFormatter.formatInteger(BigDecimal.valueOf(project.fundingTarget))
            )
            projectDaysLeftTv.text = project.deadline.formatToOpenProjectDate(resources)
        } else {
            projectDetailVoteButton.isClickable = false
            projectDetailVoteButton.setCardBackgroundColor(ContextCompat.getColor(activity!!, R.color.greyBackground))

            projectDetailVotesProgressBar.gone()
            projectDetailProgressTextView.text = getString(R.string.project_votes_template, numbersFormatter.formatInteger(BigDecimal.valueOf(project.fundingCurrent)))

            projectDaysLeftTv.text = project.statusUpdateTime.formatToClosedProjectDate(resources)
        }

        when (project.status) {
            ProjectStatus.COMPLETED -> {
                projectVoteTv.setText(R.string.project_successful_voting)
                projectVoteTv.setTextColor(ContextCompat.getColor(activity!!, R.color.green))
                projectDetailsVoteButtonIcon.setImageDrawable(ContextCompat.getDrawable(activity!!, R.drawable.icon_succ_voting))
            }
            ProjectStatus.FAILED -> {
                projectVoteTv.setText(R.string.project_unsuccessful_voting)
                projectVoteTv.setTextColor(ContextCompat.getColor(activity!!, R.color.green))
                projectDetailsVoteButtonIcon.setImageDrawable(ContextCompat.getDrawable(activity!!, R.drawable.icon_failed))
            }
            ProjectStatus.OPEN -> {
                projectDetailsVoteButtonIcon.imageTintMode = Mode.SRC_ATOP
                projectDetailsVoteButtonIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity!!, R.color.white))
                if (project.votes.toInt() == 0) {
                    projectVoteTv.text = getString(R.string.common_vote)
                    projectDetailsVoteButtonIcon.setImageDrawable(ContextCompat.getDrawable(activity!!, R.drawable.icon_vote_shape))
                } else {
                    projectVoteTv.text = numbersFormatter.formatInteger(project.votes)
                    projectDetailsVoteButtonIcon.setImageDrawable(ContextCompat.getDrawable(activity!!, R.drawable.icon_vote_filled))
                }
            }
        }

        Picasso.get().load(project.image.toString()).fit().centerCrop().into(projectDetailHeaderImageView)

        if (project.discussionLink == null) {
            discussLinkTextView.gone()
            projectDetailSeparateLineView3.gone()
        } else {
            discussLinkTextView.show()
            projectDetailSeparateLineView3.show()
            discussLinkTextView.text = getString(R.string.project_discussion_template, project.discussionLink!!.title)
        }
    }

    private val galleryItemClickListener: (GalleryItem, View, Int) -> Unit = { item, sharedView, position ->
        viewModel.galleryClicked(activity!!, item, position)
    }

    private fun showRewardByCurrency(project: ProjectDetails) {
        if (project.votes.toInt() != 0 && project.status !== ProjectStatus.OPEN) {
            rewardTv.show()
            rewardTv.text = getString(R.string.project_spent_format, project.votes.toString())
        } else {
            rewardTv.gone()
        }
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(view!!, this)
    }

    override fun onPause() {
        super.onPause()
        keyboardHelper?.release()
        voteDialog?.dismiss()
    }

    override fun onKeyboardShow() {
        voteDialog?.showCloseKeyboard()
    }

    override fun onKeyboardHide() {
        voteDialog?.showOpenKeyboard()
    }
}