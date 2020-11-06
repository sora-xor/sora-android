/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.privacy

import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrivacyViewModelTest {

    @Mock private lateinit var router: MainRouter

    private lateinit var privacyViewModel: PrivacyViewModel

    @Before fun setUp() {
        privacyViewModel = PrivacyViewModel(router)
    }

    @Test fun `back button pressed`() {
        privacyViewModel.onBackPressed()

        verify(router).popBackStack()
    }
}