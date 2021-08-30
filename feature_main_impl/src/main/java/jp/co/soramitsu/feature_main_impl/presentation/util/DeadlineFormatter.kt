/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.util

import android.content.res.Resources
import android.os.CountDownTimer
import android.text.format.DateUtils
import android.widget.TextView
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import java.util.concurrent.TimeUnit

class DeadlineFormatter(
    private val labelView: TextView,
    private val valueView: TextView,
    private val dateTimeFormatter: DateTimeFormatter,
    private val deadlineListener: (String) -> Unit
) {

    private var countDownTimer: CountDownTimer? = null

    private val resources: Resources
        get() = valueView.resources

    fun release() {
        stopCountdown()
    }

    fun setReferendum(referendum: Referendum) {
        labelView.text = deadlineLabel(referendum)

        if (referendum.isOpen) {
            processOpenReferendum(referendum)
        } else {
            processFinishedReferendum(referendum)
        }
    }

    private fun processFinishedReferendum(referendum: Referendum) {
        labelView.text = resources.getString(R.string.project_ended_template, "")

        valueView.text = dateTimeFormatter.formatToClosedVotableDateString(
            referendum.statusUpdateTime.time,
            false
        )
    }

    private fun processOpenReferendum(referendum: Referendum) {
        labelView.text = resources.getString(R.string.referendum_ends_in_title)

        val leftMillis = referendum.deadline.time - System.currentTimeMillis()
        val leftDays = convertMillisToDays(leftMillis)

        if (shouldShowTimer(leftDays)) {
            startCountdown(leftMillis, referendum.id)
        } else {
            stopCountdown()

            valueView.text = resources.getQuantityString(R.plurals.referendum_date_day_plurals_value, leftDays, leftDays)
        }
    }

    private fun shouldShowTimer(leftDays: Int) = leftDays == 0

    private fun deadlineLabel(referendum: Referendum) =
        if (referendum.isOpen) {
            resources.getString(R.string.referendum_ends_in_title)
        } else {
            resources.getString(R.string.project_ended_template, "")
        }

    private fun convertMillisToDays(millis: Long) =
        TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS)
            .toInt()

    private fun stopCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun startCountdown(deadlineInMillis: Long, id: String) {
        countDownTimer = object : CountDownTimer(deadlineInMillis, 1000) {
            override fun onFinish() {
                labelView.gone()
                valueView.text = resources.getString(R.string.referendum_finishing_soon)

                deadlineListener(id)

                countDownTimer = null
            }

            override fun onTick(millisUntilFinished: Long) {
                val formatted = DateUtils.formatElapsedTime(millisUntilFinished / 1000)

                valueView.text = formatted
            }
        }

        countDownTimer!!.start()
    }
}
