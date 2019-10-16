/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.util

import android.annotation.SuppressLint
import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.WindowManager.LayoutParams
import android.widget.EditText
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import kotlinx.android.synthetic.main.vote_bottom_dialog.cancelBtn
import kotlinx.android.synthetic.main.vote_bottom_dialog.closeBtn
import kotlinx.android.synthetic.main.vote_bottom_dialog.keyboardImg
import kotlinx.android.synthetic.main.vote_bottom_dialog.messageTv
import kotlinx.android.synthetic.main.vote_bottom_dialog.voteBtn
import kotlinx.android.synthetic.main.vote_bottom_dialog.votesEt
import kotlinx.android.synthetic.main.vote_bottom_dialog.votesSb

@SuppressLint("CheckResult")
class CustomBottomSheetDialog(
    context: Activity,
    private val maxVoteType: MaxVoteType,
    private val maxVotesNeeded: Int,
    private val voteClickListener: (Long) -> Unit,
    private val keyboardClickListener: (EditText) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    enum class MaxVoteType {
        USER_CAN_GIVE,
        PROJECT_NEED
    }

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.vote_bottom_dialog, null))

        votesEt.tag = 0

        votesEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(afterChangeEvent: Editable?) {
                votesEt.tag = 1
                votesEt.removeTextChangedListener(this)

                val votesCount = afterChangeEvent.toString().toIntOrNull() ?: 0
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
                    val votesStr = if (progress == 0) "1" else progress.toString()
                    votesEt.setText(votesStr)
                    votesEt.setSelection(votesEt.length())
                }
                votesEt.tag = 0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        closeBtn.setOnClickListener { dismiss() }
        cancelBtn.setOnClickListener { dismiss() }

        window!!.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        voteBtn.setOnClickListener {
            dismiss()
            voteClickListener(votesEt.text.toString().toLong())
        }

        keyboardImg.setOnClickListener { keyboardClickListener(votesEt) }

        votesSb.max = maxVotesNeeded
    }

    private fun votesChanged(votesCount: Int) {
        if (votesCount == 0) {
            messageTv.text = context.getString(R.string.you_can_vote_at_least_1_point)
            messageTv.show()
            voteBtn.alpha = 0.5f
            voteBtn.isEnabled = false
            return
        }

        if (votesCount > maxVotesNeeded) {
            if (MaxVoteType.PROJECT_NEED == maxVoteType) {

                votesEt.setText(maxVotesNeeded.toString())
                votesEt.setSelection(votesEt.length())

                messageTv.text = context.getString(R.string.project_requires_votes_format, maxVotesNeeded)
                voteBtn.isEnabled = true
                voteBtn.alpha = 1f
            } else {
                messageTv.text = context.getString(R.string.user_have_not_enough_votes_message)
                voteBtn.isEnabled = false
                voteBtn.alpha = 0.5f
            }
            messageTv.show()
            votesSb.progress = votesSb.max
            return
        }

        votesSb.progress = votesCount
        voteBtn.isEnabled = true
        voteBtn.alpha = 1f
        messageTv.hide()
    }

    fun showOpenKeyboard() {
        keyboardImg.setImageResource(R.drawable.icon_open_keyboard)
    }

    fun showCloseKeyboard() {
        keyboardImg.setImageResource(R.drawable.icon_close_keyboard)
    }
}