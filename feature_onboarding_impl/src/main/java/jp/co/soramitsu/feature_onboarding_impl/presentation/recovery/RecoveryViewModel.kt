package jp.co.soramitsu.feature_onboarding_impl.presentation.recovery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.mnemonic.EnglishWordList
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class RecoveryViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    companion object {
        const val MNEMONIC_INPUT_LENGTH = 150
    }

    private val _mnemonicInputLengthLiveData = MutableLiveData<Int>(MNEMONIC_INPUT_LENGTH)
    val mnemonicInputLengthLiveData: LiveData<Int> = _mnemonicInputLengthLiveData

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    fun btnNextClick(mnemonic: String) {
        val mnemonics = splitToArray(mnemonic)
        if (mnemonics.size == 15) {
            if (checkMnemonic(mnemonics)) {
                disposables.add(
                    interactor.runRecoverFlow(mnemonic)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSubscribe { progress.showProgress() }
                        .subscribe({
                            progress.hideProgress()
                            router.showMainScreen()
                        }, {
                            progress.hideProgress()
                            onError(it)
                        })
                )
            } else {
                progress.hideProgress()
                onError(SoraException.businessError(ResponseCode.MNEMONIC_IS_NOT_VALID))
            }
        } else {
            progress.hideProgress()
            onError(SoraException.businessError(ResponseCode.MNEMONIC_LENGTH_ERROR))
        }
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    private fun splitToArray(mnemonic: String): Array<String> {
        return mnemonic.trim().split(" ").toTypedArray()
    }

    private fun checkMnemonic(mnemonic: Array<String>): Boolean {
        for (word in mnemonic) {
            if (!EnglishWordList.words.contains(word)) {
                return false
            }
        }
        return true
    }

    fun onPassphraseChanged(mnemonic: String) {
        _nextButtonEnabledLiveData.value = mnemonic.isNotEmpty()
    }
}