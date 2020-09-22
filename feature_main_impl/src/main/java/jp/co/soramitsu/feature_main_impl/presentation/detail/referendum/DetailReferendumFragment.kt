package jp.co.soramitsu.feature_main_impl.presentation.detail.referendum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.setImageTint
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.detail.BaseDetailFragment
import jp.co.soramitsu.feature_main_impl.presentation.util.DeadlineFormatter
import jp.co.soramitsu.feature_main_impl.presentation.util.VoteBottomSheetDialog.VotableType
import jp.co.soramitsu.feature_main_impl.presentation.util.loadImage
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.ReferendumStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumClose
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumDeadline
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumDeadlineLabel
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumDescription
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumImage
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumNoCount
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumPersonalNoCount
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumPersonalNoDescription
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumPersonalYesCount
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumPersonalYesDescription
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumResultGroup
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumResultIcon
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumResultStatus
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumTitle
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumTotalVotes
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumVoteAgainst
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumVoteFor
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumVotes
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumVotingGroup
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumVsLine
import kotlinx.android.synthetic.main.fragment_referendum_detail.referendumYesCount
import javax.inject.Inject

class DetailReferendumFragment : BaseDetailFragment<DetailReferendumViewModel>() {
    private enum class VotingResultStyle(
        @DrawableRes val iconRes: Int,
        @ColorRes val colorRes: Int,
        @StringRes val statusRes: Int
    ) {
        Accepted(R.drawable.ic_thumb_up_24, R.color.uikit_lightRed, R.string.referendum_support_title),
        Rejected(R.drawable.ic_thumb_down_24, R.color.grey, R.string.referendum_unsupport_title)
    }

    companion object {
        private const val KEY_REFERENDUM_ID = "referendum_id"

        fun createBundle(projectId: String): Bundle {
            return Bundle().apply { putString(KEY_REFERENDUM_ID, projectId) }
        }
    }

    @Inject
    override lateinit var debounceClickHandler: DebounceClickHandler

    @Inject lateinit var dateTimeFormatter: DateTimeFormatter

    private lateinit var deadlineFormatter: DeadlineFormatter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_referendum_detail, container, false)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        referendumClose.setOnClickListener { viewModel.backPressed() }

        referendumVotes.setOnClickListener { viewModel.votesClicked() }

        referendumVoteFor.setDebouncedClickListener(debounceClickHandler) {
            viewModel.voteOnReferendumClicked(toSupport = true)
        }

        referendumVoteAgainst.setDebouncedClickListener(debounceClickHandler) {
            viewModel.voteOnReferendumClicked(toSupport = false)
        }

        referendumPersonalYesDescription.text = buildPersonalVotesDescription(R.string.referendum_support_title)
        referendumPersonalNoDescription.text = buildPersonalVotesDescription(R.string.referendum_unsupport_title)

        deadlineFormatter = DeadlineFormatter(referendumDeadlineLabel, referendumDeadline, dateTimeFormatter) {
            viewModel.onDeadline(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        deadlineFormatter.release()
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .detailReferendumComponentBuilder()
            .withFragment(this)
            .withReferendumId(getReferendumId())
            .build()
            .inject(this)
    }

    override fun subscribe(viewModel: DetailReferendumViewModel) {
        viewModel.votesFormattedLiveData.observe {
            referendumVotes.text = it
        }

        viewModel.referendumLiveData.observe {
            showReferendum(it)
        }

        viewModel.showVoteSheet.observeEvent { payload ->
            openVotingSheet(payload.maxAllowedVotes, VotableType.Referendum(payload.toSupport)) {
                viewModel.voteOnReferendum(it, payload.toSupport)
            }
        }
    }

    private fun showReferendum(referendum: Referendum) = with(referendum) {
        referendumImage.loadImage(imageLink)

        deadlineFormatter.setReferendum(this)

        referendumTitle.text = name
        referendumDescription.text = detailedDescription
        referendumVsLine.percentage = supportingPercentage

        referendumYesCount.text = numbersFormatter.formatInteger(supportVotes)
        referendumNoCount.text = numbersFormatter.formatInteger(opposeVotes)

        referendumPersonalYesCount.text = numbersFormatter.formatInteger(userSupportVotes)
        referendumPersonalNoCount.text = numbersFormatter.formatInteger(userOpposeVotes)

        referendumTotalVotes.text = numbersFormatter.formatInteger(totalVotes)

        handleReferendumStatus(referendum)
    }

    @Suppress("NON_EXHAUSTIVE_WHEN")
    private fun handleReferendumStatus(referendum: Referendum) {
        when (referendum.status) {
            ReferendumStatus.ACCEPTED -> showReferendumResult(VotingResultStyle.Accepted)
            ReferendumStatus.REJECTED -> showReferendumResult(VotingResultStyle.Rejected)
        }
    }

    private fun showReferendumResult(style: VotingResultStyle) {
        referendumVotingGroup.visibility = View.GONE

        referendumResultIcon.setImageResource(style.iconRes)
        referendumResultIcon.setImageTint(style.colorRes)

        referendumResultStatus.setText(style.statusRes)
        referendumResultStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                style.colorRes
            )
        )

        referendumResultGroup.visibility = View.VISIBLE
    }

    private fun buildPersonalVotesDescription(@StringRes voteTypeRes: Int): String {
        val type = getString(voteTypeRes)

        return getString(R.string.brackets_format, type)
    }

    private fun getReferendumId() = arguments!!.getString(KEY_REFERENDUM_ID, "")
}