/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.model.MnemonicWord

class MnemonicViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val preloader: WithPreloader
) : BaseViewModel(), WithPreloader by preloader {

    private val _mnemonicLiveData = MutableLiveData<List<MnemonicWord>>()
    val mnemonicLiveData: LiveData<List<MnemonicWord>> = _mnemonicLiveData

    private val _mnemonicShare = SingleLiveEvent<String>()
    val mnemonicShare: LiveData<String> = _mnemonicShare

    private var mnemonic: String? = null

    fun btnNextClicked() {
        router.showMnemonicConfirmation()
    }

    fun shareMnemonicClicked() = mnemonic?.let {
        _mnemonicShare.value = it
    }

    fun getPassphrase() {
        disposables.add(
            interactor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        mnemonic = it
                        _mnemonicLiveData.value = mapToMnemonicWordsList(it)
                    },
                    {
                        it.printStackTrace()
                    }
                )
        )
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    private fun mapToMnemonicWordsList(mnemonic: String): List<MnemonicWord> {
        val mnemonicList = mnemonic.split(" ")

        val halfCount = if (mnemonicList.size % 2 > 0) {
            mnemonicList.size / 2 + 1
        } else {
            mnemonicList.size / 2
        }

        val list1 = mnemonicList.subList(0, halfCount)
        val list2 = mnemonicList.subList(halfCount, mnemonicList.size)
        val resultList = mutableListOf<MnemonicWord>()
        var index = 1
        repeat(halfCount) {
            resultList.add(MnemonicWord(index, list1[it]))

            list2.getOrNull(it)?.let {
                resultList.add(MnemonicWord(index + halfCount, it))
            }
            index++
        }

        return resultList
    }
}
