package jp.co.soramitsu.feature_main_impl.presentation.detail.referendum

import android.os.Bundle
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.setImageTint
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentReferendumDetailBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.detail.BaseDetailFragment
import jp.co.soramitsu.feature_main_impl.presentation.util.DeadlineFormatter
import jp.co.soramitsu.feature_main_impl.presentation.util.VoteBottomSheetDialog.VotableType
import jp.co.soramitsu.feature_main_impl.presentation.util.loadImage
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.ReferendumStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class DetailReferendumFragment :
    BaseDetailFragment<DetailReferendumViewModel>(R.layout.fragment_referendum_detail) {
    private enum class VotingResultStyle(
        @DrawableRes val iconRes: Int,
        @ColorRes val colorRes: Int,
        @StringRes val statusRes: Int
    ) {
        Accepted(
            R.drawable.ic_thumb_up_24,
            R.color.uikit_lightRed,
            R.string.referendum_support_title
        ),
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

    @Inject
    lateinit var dateTimeFormatter: DateTimeFormatter

    private lateinit var deadlineFormatter: DeadlineFormatter
    private val binding by viewBinding(FragmentReferendumDetailBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        binding.referendumClose.setOnClickListener { viewModel.backPressed() }

        binding.referendumVotes.setOnClickListener { viewModel.votesClicked() }

        binding.referendumVoteFor.setDebouncedClickListener(debounceClickHandler) {
            viewModel.voteOnReferendumClicked(toSupport = true)
        }

        binding.referendumVoteAgainst.setDebouncedClickListener(debounceClickHandler) {
            viewModel.voteOnReferendumClicked(toSupport = false)
        }

        binding.referendumPersonalYesDescription.text =
            buildPersonalVotesDescription(R.string.referendum_support_title)
        binding.referendumPersonalNoDescription.text =
            buildPersonalVotesDescription(R.string.referendum_unsupport_title)

        deadlineFormatter = DeadlineFormatter(
            binding.referendumDeadlineLabel,
            binding.referendumDeadline,
            dateTimeFormatter
        ) {
            viewModel.onDeadline(it)
        }

        initListeners()
    }

    override fun onDestroyView() {
        deadlineFormatter.release()
        super.onDestroyView()
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .detailReferendumComponentBuilder()
            .withFragment(this)
            .withReferendumId(getReferendumId())
            .build()
            .inject(this)
    }

    private fun initListeners() {
        viewModel.votesFormattedLiveData.observe {
            binding.referendumVotes.text = it
        }
        viewModel.referendumLiveData.observe {
            showReferendum(it)
        }
        viewModel.showVoteSheet.observe { payload ->
            openVotingSheet(payload.maxAllowedVotes, VotableType.Referendum(payload.toSupport)) {
                viewModel.voteOnReferendum(it, payload.toSupport)
            }
        }
    }

    private fun showReferendum(referendum: Referendum) = with(referendum) {
        binding.referendumImage.loadImage(imageLink)

        deadlineFormatter.setReferendum(this)

        binding.referendumTitle.text = name
        binding.referendumDescription.text = detailedDescription
        binding.referendumVsLine.percentage = supportingPercentage

        binding.referendumYesCount.text = numbersFormatter.formatInteger(supportVotes)
        binding.referendumNoCount.text = numbersFormatter.formatInteger(opposeVotes)

        binding.referendumPersonalYesCount.text = numbersFormatter.formatInteger(userSupportVotes)
        binding.referendumPersonalNoCount.text = numbersFormatter.formatInteger(userOpposeVotes)

        binding.referendumTotalVotes.text = numbersFormatter.formatInteger(totalVotes)

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
        binding.referendumVotingGroup.visibility = View.GONE

        binding.referendumResultIcon.setImageResource(style.iconRes)
        binding.referendumResultIcon.setImageTint(style.colorRes)

        binding.referendumResultStatus.setText(style.statusRes)
        binding.referendumResultStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                style.colorRes
            )
        )

        binding.referendumResultGroup.visibility = View.VISIBLE
    }

    private fun buildPersonalVotesDescription(@StringRes voteTypeRes: Int): String {
        val type = getString(voteTypeRes)

        return getString(R.string.brackets_format, type)
    }

    private fun getReferendumId() = requireArguments().getString(KEY_REFERENDUM_ID, "")
}
