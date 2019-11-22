/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail

import android.annotation.SuppressLint
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
import com.jakewharton.rxbinding2.view.RxView
import com.squareup.picasso.Picasso
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.EllipsizeUtil
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.createSendEmailIntent
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.detail.gallery.GalleryAdapter
import jp.co.soramitsu.feature_main_impl.presentation.util.CustomBottomSheetDialog
import jp.co.soramitsu.feature_main_impl.presentation.util.formatToClosedProjectDate
import jp.co.soramitsu.feature_main_impl.presentation.util.formatToOpenProjectDate
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import jp.co.soramitsu.feature_project_api.domain.model.ProjectStatus
import kotlinx.android.synthetic.main.fragment_project_detail.toolbar
import kotlinx.android.synthetic.main.fragment_project_detail.projectView
import kotlinx.android.synthetic.main.fragment_project_detail.preloaderView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailAddToFavouritesButton
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailTitleTextView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailVotesProgressBar
import kotlinx.android.synthetic.main.fragment_project_detail.favImg
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailDescriptionTextView
import kotlinx.android.synthetic.main.fragment_project_detail.friendsFavoritesCountText
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailSeparateLineView0
import kotlinx.android.synthetic.main.fragment_project_detail.webSiteTv
import kotlinx.android.synthetic.main.fragment_project_detail.emailTv
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailVoteButton
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailProgressTextView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDaysLeftTv
import kotlinx.android.synthetic.main.fragment_project_detail.projectVoteTv
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailsVoteButtonIcon
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailHeaderImageView
import kotlinx.android.synthetic.main.fragment_project_detail.discussLinkTextView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailSeparateLineView3
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailGalleryTextView
import kotlinx.android.synthetic.main.fragment_project_detail.projectDetailGalleryRecyclerView
import kotlinx.android.synthetic.main.fragment_project_detail.reward
import java.math.BigDecimal
import javax.inject.Inject

@SuppressLint("CheckResult")
class DetailFragment : BaseFragment<DetailViewModel>(), KeyboardHelper.KeyboardListener {

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
            .withRouter(activity as MainRouter)
            .withProjectId(arguments!!.getString(Const.PROJECT_ID, ""))
            .build()
            .inject(this)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.backPressed() }
        toolbar.setOnVotesClickListener { viewModel.votesClicked() }
        configureClicks()
    }

    override fun subscribe(viewModel: DetailViewModel) {
        observe(viewModel.projectDetailsLiveData, Observer {
            showProject(it)
            showRewardByCurrency(it)
            if (it.gallery.isEmpty()) hideGallery() else showGallery(it.gallery)
        })

        observe(viewModel.votesLiveData, Observer {
            val votes = it.second
            if (votes.length > 4) {
                toolbar.setVotes(getString(R.string.votes_k_template, votes.substring(0, votes.length - 3).trim()))
            } else {
                toolbar.setVotes(votes)
            }
            toolbar.showVotes()
        })

        observe(viewModel.playVideoLiveData, EventObserver {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(it), "video/*")
            }
            activity?.packageManager?.let { packageManager ->
                if (intent.resolveActivity(packageManager) == null) {
                    Toast.makeText(activity!!, R.string.no_video_app_installed_error, Toast.LENGTH_SHORT).show()
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
                }
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
                }
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
            activity!!.createSendEmailIntent(it, getString(R.string.send_email))
        })

        viewModel.onActivityCreated()
        viewModel.getVotes(false)
        viewModel.updateProject()
    }

    private fun configureClicks() {
        val scaleAnimation = AnimationUtils.loadAnimation(activity, R.anim.scale_animation)

        RxView.clicks(projectDetailAddToFavouritesButton)
            .subscribe {
                projectDetailAddToFavouritesButton.startAnimation(scaleAnimation)
                viewModel.favoriteClicked()
            }

        discussLinkTextView.setOnClickListener { viewModel.discussionLinkClicked() }
    }

    private fun showProject(project: ProjectDetails) {
        val favouritesCountString = getFavouritesCountString(project)
        val friendsCountString = getFriendsCountString(project)

        projectDetailTitleTextView.text = project.name
        projectDetailVotesProgressBar.progress = project.getFundingPercent()

        val favIcon = if (project.isFavorite) {
            ContextCompat.getDrawable(activity!!, R.drawable.icon_fav_filled)
        } else {
            ContextCompat.getDrawable(activity!!, R.drawable.icon_fav_shape)
        }
        favImg.setImageDrawable(favIcon)

        val details = if (project.detailedDescription.isEmpty()) {
            project.description
        } else {
            project.detailedDescription
        }
        projectDetailDescriptionTextView.text = details

        if (friendsCountString.isEmpty() && favouritesCountString.isEmpty()) {
            friendsFavoritesCountText.gone()
        } else {
            friendsFavoritesCountText.show()
        }

        friendsFavoritesCountText.text = getString(R.string.friends_and_favourites_template, friendsCountString, favouritesCountString)

        if (project.votes == BigDecimal.ZERO) {
            projectDetailSeparateLineView0.gone()
        } else {
            projectDetailSeparateLineView0.show()
        }

        webSiteTv.text = EllipsizeUtil.ellipsizeMiddle(project.projectLink.toString())
        emailTv.text = EllipsizeUtil.ellipsizeMiddle(project.email)

        webSiteTv.setOnClickListener { viewModel.websiteClicked(project.projectLink) }
        emailTv.setOnClickListener { viewModel.emailClicked() }

        if (project.status == ProjectStatus.OPEN) {
            projectDetailVoteButton.isClickable = true
            projectDetailVoteButton.setCardBackgroundColor(ContextCompat.getColor(activity!!, R.color.lightRed))
            projectDetailVoteButton.setOnClickListener { viewModel.voteClicked() }

            projectDetailProgressTextView.text = getString(R.string.founded_template,
                project.getFundingPercent(),
                numbersFormatter.formatInteger(BigDecimal.valueOf(project.fundingTarget)))
            projectDaysLeftTv.text = project.deadline.formatToOpenProjectDate(resources)
        } else {
            projectDetailVoteButton.isClickable = false
            projectDetailVoteButton.setCardBackgroundColor(ContextCompat.getColor(activity!!, R.color.greyBackground))

            projectDetailVotesProgressBar.gone()
            projectDetailProgressTextView.text = getString(R.string.votes_template, numbersFormatter.formatInteger(BigDecimal.valueOf(project.fundingCurrent)))

            projectDaysLeftTv.text = project.statusUpdateTime.formatToClosedProjectDate(resources)
        }
        when (project.status) {
            ProjectStatus.COMPLETED -> {
                projectVoteTv.setText(R.string.successful_voting)
                projectVoteTv.setTextColor(ContextCompat.getColor(activity!!, R.color.green))
                projectDetailsVoteButtonIcon.setImageDrawable(ContextCompat.getDrawable(activity!!, R.drawable.icon_succ_voting))
            }
            ProjectStatus.FAILED -> {
                projectVoteTv.setText(R.string.unsuccessful_voting)
                projectVoteTv.setTextColor(ContextCompat.getColor(activity!!, R.color.green))
                projectDetailsVoteButtonIcon.setImageDrawable(ContextCompat.getDrawable(activity!!, R.drawable.icon_failed))
            }
            ProjectStatus.OPEN -> {
                projectDetailsVoteButtonIcon.imageTintMode = Mode.SRC_ATOP
                projectDetailsVoteButtonIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity!!, R.color.white))
                if (project.votes.toInt() == 0) {
                    projectVoteTv.text = getString(R.string.vote)
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
            discussLinkTextView.text = getString(R.string.discussion_template, project.discussionLink!!.title)
        }
    }

    private fun getFavouritesCountString(projectVm: ProjectDetails): String {
        return if (projectVm.favoriteCount != 0) {
            getString(R.string.friends_and_favourites_template,
                projectVm.favoriteCount.toString(),
                resources.getQuantityString(R.plurals.favourites, projectVm.favoriteCount))
        } else {
            ""
        }
    }

    private fun getFriendsCountString(projectVm: ProjectDetails): String {
        return if (projectVm.votedFriendsCount != 0) {
            getString(R.string.friends_template,
                projectVm.votedFriendsCount.toString(),
                resources.getQuantityString(R.plurals.friends, projectVm.votedFriendsCount)) + "\t"
        } else {
            ""
        }
    }

    private fun showGallery(items: List<GalleryItem>) {
        projectDetailGalleryTextView.show()
        projectDetailGalleryRecyclerView.show()
        if (projectDetailGalleryRecyclerView.adapter == null) {
            projectDetailGalleryRecyclerView.adapter = GalleryAdapter(galleryItemClickListener)
            LinearSnapHelper().attachToRecyclerView(projectDetailGalleryRecyclerView)
        }
        (projectDetailGalleryRecyclerView.adapter as GalleryAdapter).submitList(items)
    }

    private fun hideGallery() {
        projectDetailGalleryTextView.gone()
        projectDetailGalleryRecyclerView.gone()
    }

    private val galleryItemClickListener: (GalleryItem, View, Int) -> Unit = { item, sharedView, position ->
        viewModel.galleryClicked(activity!!, item, position)
    }

    private fun showRewardByCurrency(project: ProjectDetails) {
        if (project.votes.toInt() != 0 && project.status !== ProjectStatus.OPEN) {
            reward.show()
            reward.text = getString(R.string.spent, project.votes.toString())
            projectDetailSeparateLineView0.show()
        } else {
            reward.gone()
            projectDetailSeparateLineView0.gone()
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