/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.util

import android.app.Activity
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.ImageView
import android.widget.TextView
import android.widget.SeekBar
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import kotlinx.android.synthetic.main.vote_bottom_dialog.cancelBtn
import kotlinx.android.synthetic.main.vote_bottom_dialog.keyboardImg
import kotlinx.android.synthetic.main.vote_bottom_dialog.messageTv
import kotlinx.android.synthetic.main.vote_bottom_dialog.voteSubmitContainer
import kotlinx.android.synthetic.main.vote_bottom_dialog.voteSubmitIcon
import kotlinx.android.synthetic.main.vote_bottom_dialog.voteSubmitText
import kotlinx.android.synthetic.main.vote_bottom_dialog.votesEt
import kotlinx.android.synthetic.main.vote_bottom_dialog.votesSb
import kotlinx.android.synthetic.main.vote_bottom_dialog.voteSubmitButtonImage

class VoteStylebaleHolder(val icon: ImageView, val text: TextView, val container: View, val buttonImage: ImageView, val seekBar: SeekBar)

private typealias VoteDialogStyler = (VoteStylebaleHolder) -> Unit

class VoteBottomSheetDialog(
    context: Activity,
    private val votableType: VotableType,
    private val maxVotesNeeded: Int,
    private val voteClickListener: (Long) -> Unit,
    private val keyboardClickListener: (EditText) -> Unit,
    private val numbersFormatter: NumbersFormatter,
    debounceClickHandler: DebounceClickHandler
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    private var lockVotesCount = 0

    sealed class VotableType(val maxVoteType: MaxVoteType) {
        abstract val voteDialogStyler: VoteDialogStyler

        class Project(maxVoteType: MaxVoteType) : VotableType(maxVoteType) {
            override val voteDialogStyler: VoteDialogStyler = { dialogStylable ->
                with(dialogStylable) {
                    val context = container.context

                    container.show()
                    icon.gone()
                    buttonImage.gone()
                    text.text = context.getString(R.string.common_vote)

                    container.setBackgroundResource(R.drawable.rounded_rectangle_red)
                }
            }
        }

        class Referendum(private val isVotingFor: Boolean) : VotableType(MaxVoteType.USER_CAN_GIVE) {
            override val voteDialogStyler: VoteDialogStyler = { dialogStylable ->
                with(dialogStylable) {
                    val context = container.context
                    container.gone()

                    if (isVotingFor) {
                        seekBar.thumb = context.getDrawable(R.drawable.ic_seekbar_thumb_red)
                        seekBar.progressTintList = ColorStateList.valueOf(context.resources.getColor(R.color.uikit_lightRed))
                    } else {
                        seekBar.thumb = context.getDrawable(R.drawable.ic_seekbar_thumb_grey)
                        seekBar.progressTintList = ColorStateList.valueOf(context.resources.getColor(R.color.grey))
                    }

                    val iconRes = if (isVotingFor) R.drawable.ic_thumb_up_16 else R.drawable.ic_thumb_down_16
                    buttonImage.show()
                    buttonImage.setImageResource(iconRes)

                    val backgroundRes = if (isVotingFor) R.drawable.rounded_rectangle_red else R.drawable.rounded_rectangle_grey
                    buttonImage.setBackgroundResource(backgroundRes)
                }
            }
        }
    }

    enum class MaxVoteType {
        USER_CAN_GIVE,
        VOTABLE_NEED
    }

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.vote_bottom_dialog, null))

        val dialogHolder = VoteStylebaleHolder(voteSubmitIcon, voteSubmitText, voteSubmitContainer, voteSubmitButtonImage, votesSb)
        votableType.voteDialogStyler.invoke(dialogHolder)

        votesEt.tag = 0
        votesSb.max = maxVotesNeeded

        votesEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(afterChangeEvent: Editable?) {
                votesEt.tag = 1
                votesEt.removeTextChangedListener(this)

                val votesCount = numbersFormatter.getNumberFromString(afterChangeEvent.toString())
                votesChanged(votesCount)

                votesEt.addTextChangedListener(this)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        votesSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (votesEt.tag == 0) {
                    val votes = if (progress == 0) 1 else progress
                    setVotesText(votes)
                }
                votesEt.tag = 0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        cancelBtn.setDebouncedClickListener(debounceClickHandler) {
            dismiss()
        }

        window!!.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        voteSubmitContainer.setDebouncedClickListener(debounceClickHandler) {
            if (votesEt.text.toString().isNotBlank()) {
                dismiss()
                voteClickListener(numbersFormatter.getNumberFromString(votesEt.text.toString()).toLong())
            }
        }

        voteSubmitButtonImage.setDebouncedClickListener(debounceClickHandler) {
            if (votesEt.text.toString().isNotBlank()) {
                dismiss()
                voteClickListener(numbersFormatter.getNumberFromString(votesEt.text.toString()).toLong())
            }
        }

        keyboardImg.setDebouncedClickListener(debounceClickHandler) {
            keyboardClickListener(votesEt)
        }

        votesSb.progress = 1
    }

    private fun votesChanged(votesCount: Int) {
        if (votesCount == 0) {
            messageTv.text = context.getString(R.string.project_you_can_vote_at_least_1_point)
            messageTv.show()
            voteSubmitContainer.alpha = 0.5f
            voteSubmitContainer.isEnabled = false
            return
        }

        if (votesCount > maxVotesNeeded) {
            if (MaxVoteType.VOTABLE_NEED == votableType.maxVoteType) {

                setVotesText(maxVotesNeeded)

                messageTv.text = context.getString(R.string.project_requires_votes_format, maxVotesNeeded.toString())
                voteSubmitContainer.isEnabled = true
                voteSubmitContainer.alpha = 1f
            } else {
                if (lockVotesCount == 0) lockVotesCount = votesCount

                setVotesText(lockVotesCount)

                messageTv.text = context.getString(R.string.project_user_have_not_enough_votes_message)
                voteSubmitContainer.isEnabled = false
                voteSubmitContainer.alpha = 0.5f
            }
            messageTv.show()
            votesSb.progress = votesSb.max
            return
        } else {
            setVotesText(votesCount)
        }

        lockVotesCount = 0

        votesSb.progress = votesCount
        voteSubmitContainer.isEnabled = true
        voteSubmitContainer.alpha = 1f
        messageTv.hide()
    }

    fun showOpenKeyboard() {
        keyboardImg.setImageResource(R.drawable.icon_open_keyboard)
    }

    fun showCloseKeyboard() {
        keyboardImg.setImageResource(R.drawable.icon_close_keyboard)
    }

    private fun setVotesText(votes: Int) {
        val votesString = numbersFormatter.formatInteger(votes.toBigDecimal())
        votesEt.setText(votesString)
        votesEt.setSelection(votesEt.length())
    }
}