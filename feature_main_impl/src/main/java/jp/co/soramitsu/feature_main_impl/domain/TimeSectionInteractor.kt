/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.common.date.DateFormatter
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs

class TimeSectionInteractor @Inject constructor(
    private val resourceManager: ResourceManager
) {

    fun insertDateSections(list: List<VotesHistoryItem>): List<VotesHistoryItem> {
        var lastDate: String? = null
        val queue = mutableListOf<VotesHistoryItem>()
        val today = resourceManager.getString(R.string.today)
        val yesterday = resourceManager.getString(R.string.yesterday)
        list.forEach { model ->
            if (getTimeRemaining(model.timestamp!!.time) == 0) {

                if (queue.any { it.header == today }) {
                    queue.add(model)
                } else {
                    if (lastDate != today) {
                        queue.add(VotesHistoryItem(header = today))
                        queue.add(model)
                        lastDate = today
                    } else {
                        queue.add(model)
                    }
                }
            } else if (getTimeRemaining(model.timestamp.time) == 1) {

                if (queue.any { it.header == yesterday })
                    queue.add(model)
                else {
                    if (lastDate != yesterday) {
                        queue.add(VotesHistoryItem(header = yesterday))
                        queue.add(model)
                        lastDate = yesterday
                    } else {
                        queue.add(model)
                    }
                }
            } else {
                val date = DateFormatter.format(model.timestamp, DateFormatter.DD_MMMM)

                if (queue.any { it.header == date }) {
                    queue.add(model)
                } else {
                    if (lastDate != date) {
                        queue.add(VotesHistoryItem(header = date))
                        queue.add(model)
                        lastDate = date
                    } else {
                        queue.add(model)
                    }
                }
            }
        }
        return queue
    }

    private fun getTimeRemaining(timeStamp: Long): Int {
        val startDate = toCalendar(timeStamp)
        val endDate = toCalendar(System.currentTimeMillis())

        // Get the represented date in milliseconds
        val milis1 = startDate.timeInMillis
        val milis2 = endDate.timeInMillis

        // Calculate difference in milliseconds
        val diff = abs(milis2 - milis1)

        return (diff / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun toCalendar(timestamp: Long): Calendar {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }
}