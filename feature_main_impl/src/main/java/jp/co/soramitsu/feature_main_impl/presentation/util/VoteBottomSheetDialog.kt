package jp.co.soramitsu.feature_main_impl.presentation.util

import android.app.Activity
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.VoteBottomDialogBinding

class VoteStyleableHolder(
    val icon: ImageView,
    val text: TextView,
    val container: View,
    val buttonImage: ImageView,
    val seekBar: SeekBar
)

private typealias VoteDialogStyler = (VoteStyleableHolder) -> Unit

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

        class Referendum(private val isVotingFor: Boolean) :
            VotableType(MaxVoteType.USER_CAN_GIVE) {
            override val voteDialogStyler: VoteDialogStyler = { dialogStylable ->
                with(dialogStylable) {
                    val context = container.context
                    container.gone()

                    if (isVotingFor) {
                        seekBar.thumb =
                            ContextCompat.getDrawable(context, R.drawable.ic_seekbar_thumb_red)
                        seekBar.progressTintList =
                            ColorStateList.valueOf(
                                ContextCompat.getColor(
                                    context,
                                    R.color.uikit_lightRed
                                )
                            )
                    } else {
                        seekBar.thumb =
                            ContextCompat.getDrawable(context, R.drawable.ic_seekbar_thumb_grey)
                        seekBar.progressTintList =
                            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.grey))
                    }

                    val iconRes =
                        if (isVotingFor) R.drawable.ic_thumb_up_16 else R.drawable.ic_thumb_down_16
                    buttonImage.show()
                    buttonImage.setImageResource(iconRes)

                    val backgroundRes =
                        if (isVotingFor) R.drawable.rounded_rectangle_red else R.drawable.rounded_rectangle_grey
                    buttonImage.setBackgroundResource(backgroundRes)
                }
            }
        }
    }

    enum class MaxVoteType {
        USER_CAN_GIVE,
        VOTABLE_NEED
    }

    private val binding: VoteBottomDialogBinding =
        VoteBottomDialogBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)

        val dialogHolder = VoteStyleableHolder(
            binding.voteSubmitIcon,
            binding.voteSubmitText,
            binding.voteSubmitContainer,
            binding.voteSubmitButtonImage,
            binding.votesSb
        )
        votableType.voteDialogStyler.invoke(dialogHolder)

        binding.votesEt.tag = 0
        binding.votesSb.max = maxVotesNeeded

        binding.votesEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(afterChangeEvent: Editable?) {
                binding.votesEt.tag = 1
                binding.votesEt.removeTextChangedListener(this)

                val votesCount = numbersFormatter.getNumberFromString(afterChangeEvent.toString())
                votesChanged(votesCount)

                binding.votesEt.addTextChangedListener(this)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        binding.votesSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (binding.votesEt.tag == 0) {
                    val votes = if (progress == 0) 1 else progress
                    setVotesText(votes)
                }
                binding.votesEt.tag = 0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        binding.cancelBtn.setDebouncedClickListener(debounceClickHandler) {
            dismiss()
        }

        window!!.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        binding.voteSubmitContainer.setDebouncedClickListener(debounceClickHandler) {
            if (binding.votesEt.text.toString().isNotBlank()) {
                dismiss()
                voteClickListener(
                    numbersFormatter.getNumberFromString(binding.votesEt.text.toString()).toLong()
                )
            }
        }

        binding.voteSubmitButtonImage.setDebouncedClickListener(debounceClickHandler) {
            if (binding.votesEt.text.toString().isNotBlank()) {
                dismiss()
                voteClickListener(
                    numbersFormatter.getNumberFromString(binding.votesEt.text.toString()).toLong()
                )
            }
        }

        binding.keyboardImg.setDebouncedClickListener(debounceClickHandler) {
            keyboardClickListener(binding.votesEt)
        }

        binding.votesSb.progress = 1
    }

    private fun votesChanged(votesCount: Int) {
        if (votesCount == 0) {
            binding.messageTv.text =
                context.getString(R.string.project_you_can_vote_at_least_1_point)
            binding.messageTv.show()
            binding.voteSubmitContainer.alpha = 0.5f
            binding.voteSubmitContainer.isEnabled = false
            return
        }

        if (votesCount > maxVotesNeeded) {
            if (MaxVoteType.VOTABLE_NEED == votableType.maxVoteType) {

                setVotesText(maxVotesNeeded)

                binding.messageTv.text = context.getString(
                    R.string.project_requires_votes_format,
                    maxVotesNeeded.toString()
                )
                binding.voteSubmitContainer.isEnabled = true
                binding.voteSubmitContainer.alpha = 1f
            } else {
                if (lockVotesCount == 0) lockVotesCount = votesCount

                setVotesText(lockVotesCount)

                binding.messageTv.text =
                    context.getString(R.string.project_user_have_not_enough_votes_message)
                binding.voteSubmitContainer.isEnabled = false
                binding.voteSubmitContainer.alpha = 0.5f
            }
            binding.messageTv.show()
            binding.votesSb.progress = binding.votesSb.max
            return
        } else {
            setVotesText(votesCount)
        }

        lockVotesCount = 0

        binding.votesSb.progress = votesCount
        binding.voteSubmitContainer.isEnabled = true
        binding.voteSubmitContainer.alpha = 1f
        binding.messageTv.hide()
    }

    fun showOpenKeyboard() {
        binding.keyboardImg.setImageResource(R.drawable.icon_open_keyboard)
    }

    fun showCloseKeyboard() {
        binding.keyboardImg.setImageResource(R.drawable.icon_close_keyboard)
    }

    private fun setVotesText(votes: Int) {
        val votesString = numbersFormatter.formatInteger(votes.toBigDecimal())
        binding.votesEt.setText(votesString)
        binding.votesEt.setSelection(binding.votesEt.length())
    }
}
