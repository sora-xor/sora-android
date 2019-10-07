/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.reputation

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.DeciminalFormatter
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone

class ReputationViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val timerWrapper: TimerWrapper
) : BaseViewModel() {

    companion object {
        private const val ASIA_TIMEZONE = "Asia/Tokyo"
        private const val DISTRIBUTION_HOUR = 13
        private const val DISTRIBUTION_MINUTE = 37
    }

    val reputationLiveData = MutableLiveData<Reputation>()
    val calculatingReputationLiveData = MutableLiveData<Pair<Int, Int>>()
    val lastVotesLiveData = MutableLiveData<String>()
    val reputationContentLiveData = MutableLiveData<List<InformationContainer>>()

    fun loadReputation(updateCached: Boolean) {
        disposables.add(
            interactor.getReputationWithLastVotes(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val reputation = it.first
                    if (reputation.rank <= 0) {
                        setupTimer()
                    } else {
                        timerWrapper.cancel()
                        reputationLiveData.value = reputation
                        val lastVotes = it.second
                        lastVotesLiveData.value = if (lastVotes > BigDecimal.ZERO) {
                            DeciminalFormatter.formatInteger(lastVotes)
                        } else {
                            ""
                        }
                    }
                    if (!updateCached) loadReputation(true)
                }, {
                    logException(it)
                })
        )
    }

    fun backButtonClick() {
        router.popBackStackFragment()
    }

    fun loadInformation(updateCached: Boolean) {
        disposables.add(
            interactor.getReputationContent(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (!updateCached) loadInformation(true)
                    reputationContentLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    private fun setupTimer() {
        timerWrapper.setTimerCallbacks({ _, _ ->
            calculatingReputationLiveData.value = countHoursAndMinutesLeft()
        }, {
            calculatingReputationLiveData.value = Pair(0, 0)
        })

        val countDownHoursAndMinutes = countHoursAndMinutesLeft()

        val timerSecondsLeft = ((countDownHoursAndMinutes.first * 60) + countDownHoursAndMinutes.second) * 60

        timerWrapper.start(timerSecondsLeft.toLong() * 1000, 60 * 1000)
    }

    private fun countHoursAndMinutesLeft(): Pair<Int, Int> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(ASIA_TIMEZONE))
        calendar.set(Calendar.HOUR_OF_DAY, DISTRIBUTION_HOUR)
        calendar.set(Calendar.MINUTE, DISTRIBUTION_MINUTE)

        val differentTimeInMillis = (calendar.timeInMillis - Calendar.getInstance().timeInMillis) % (24 * 60 * 60 * 1000)
        val hoursDiff = differentTimeInMillis / 1000 / 60 / 60
        val minutesDiff = (differentTimeInMillis - hoursDiff * 60 * 60 * 1000) / 1000 / 60

        return Pair(hoursDiff.toInt(), minutesDiff.toInt())
    }
}