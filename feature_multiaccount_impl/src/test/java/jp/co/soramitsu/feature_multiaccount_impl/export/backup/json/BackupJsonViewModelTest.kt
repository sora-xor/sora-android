/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.export.backup.json

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json.BackupJsonViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupJsonScreenState
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.mockito.BDDMockito.given
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class BackupJsonViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var interactor: MultiaccountInteractor

    @Mock
    private lateinit var uri: Uri

    private lateinit var viewModel: BackupJsonViewModel

    private val addresses = listOf("address1", "address2")

    @Before
    fun setUp() = runTest {
        viewModel = BackupJsonViewModel(interactor, router, addresses, resourceManager)
    }

    @Test
    fun init() = runTest {
        viewModel.backupJsonScreenState.value?.let {
            assertEquals(it, BackupJsonScreenState())
        }
    }

    @Test
    fun passwordInputChangedCalled() {
        val textFieldValue = TextFieldValue("text")

        viewModel.passwordInputChanged(textFieldValue)

        viewModel.backupJsonScreenState.value?.let {
            assertEquals(
                it,
                BackupJsonScreenState(state = InputTextState(textFieldValue), buttonEnabledState = false)
            )
        }
    }

    @Test
    fun confirmationInputChangedCalled() {
        val textFieldValue = TextFieldValue("text")

        viewModel.passwordInputChanged(textFieldValue)
        viewModel.confirmationInputChanged(textFieldValue)

        viewModel.backupJsonScreenState.value?.let {
            assertEquals(
                it,
                BackupJsonScreenState(state = InputTextState(textFieldValue), confirmationState = InputTextState(textFieldValue), buttonEnabledState = true)
            )
        }
    }

    @Test
    fun confirmationInputChangedWithButtonDisabledCalled() {
        val textFieldValue = TextFieldValue("text")
        val textFieldValue2 = TextFieldValue("text2")

        viewModel.passwordInputChanged(textFieldValue)
        viewModel.confirmationInputChanged(textFieldValue2)

        viewModel.backupJsonScreenState.value?.let {
            assertEquals(
                it,
                BackupJsonScreenState(state = InputTextState(textFieldValue), confirmationState = InputTextState(textFieldValue2), buttonEnabledState = false)
            )
        }
    }

    @Test
    fun downloadJsonClickedCalled() = runTest {
        val textFieldValue2 = TextFieldValue("text2")
        viewModel.confirmationInputChanged(textFieldValue2)
        given(interactor.getJsonFileUri(addresses, "text2")).willReturn(uri)

        viewModel.downloadJsonClicked()

        viewModel.jsonTextLiveData.value?.let {
            assertEquals(it, uri)
        }
    }
}