/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.util

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxSeekBar
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import jp.co.soramitsu.feature_main_impl.R

@SuppressLint("CheckResult")
class CustomBottomSheetDialog(
    context: Activity
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    val view: View = context.layoutInflater.inflate(R.layout.vote_bottom_dialog, null)
    val votesEditText: EditText
    val voteButton: Button
    val keyboardButton: ImageView

    private val votesSeekBar: SeekBar

    init {
        voteButton = view.findViewById(R.id.btn_vote)
        val cancelDialog = view.findViewById<Button>(R.id.btn_cancel)
        val btnClose = view.findViewById<ImageView>(R.id.btn_close)
        votesEditText = view.findViewById(R.id.votes_count)
        votesSeekBar = view.findViewById(R.id.votes_seekbar)
        keyboardButton = view.findViewById(R.id.btn_keyboard)

        votesEditText.tag = 0

        RxTextView.afterTextChangeEvents(votesEditText)
            .subscribe({ str ->
                votesEditText.tag = 1

                if (str.editable()!!.toString().isEmpty()) {
                    setProgress(0)
                    setEditTextVotes("0")
                } else {
                    val votes = java.lang.Double.valueOf(str.editable()!!.toString())
                    setProgress(votes.toInt())
                    voteButton.isEnabled = true
                    if (votesEditText.text.toString().startsWith("0") and (votesEditText.length() > 1)) {
                        setEditTextVotes(votesEditText.text.toString().substring(1))
                    }
                }
                voteButton.isEnabled = votesEditText.text.toString() != "0"
            }, { e -> e.printStackTrace() })

        RxSeekBar.changes(votesSeekBar)
            .subscribe({ value ->
                if (votesEditText.tag as Int != 1) {
                    setEditTextVotes(value.toString())
                }
                votesEditText.tag = 0
            }, { e -> e.printStackTrace() })

        Observable.merge(RxView.clicks(btnClose), RxView.clicks(cancelDialog))
            .subscribe({ dismiss() }, { })

        setEditTextVotes("1")

        setContentView(view)
        window!!.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun getVotes(): Long {
        return java.lang.Long.valueOf(votesEditText.text.toString())
    }

    fun setSeekBarMax(max: Int) {
        votesSeekBar.max = max
    }

    fun setEditTextVotes(votes: String) {
        votesEditText.setText(votes)
        votesEditText.setSelection(votesEditText.length())
    }

    fun setProgress(votes: Int) {
        if (votes < votesSeekBar.max) {
            votesSeekBar.progress = votes
        } else {
            votesSeekBar.progress = votesSeekBar.max
        }
    }
}