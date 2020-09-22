/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.passphrase

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import io.reactivex.SingleTransformer
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PassphraseViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val schedulersRule = RxSchedulersRule()

    @Mock lateinit var interactor: MainInteractor
    @Mock lateinit var preloader: WithPreloader

    private lateinit var passphraseViewModel: PassphraseViewModel

    @Before fun setUp() {
        given(preloader.preloadCompose<Any>()).willReturn(SingleTransformer { upstream -> upstream })

        passphraseViewModel = PassphraseViewModel(interactor, preloader)
    }

    @Test fun `get passphrase`() {
        val mnemonic = "mnemonic"
        given(interactor.getMnemonic()).willReturn(Single.just(mnemonic))

        passphraseViewModel.getPassphrase()

        passphraseViewModel.passphraseLiveData.observeForever {
            assertEquals(mnemonic, it)
        }
    }
}