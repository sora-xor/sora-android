/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_assets_impl.domain

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.util.QrException
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.QrCodeInteractor
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.given

@RunWith(MockitoJUnitRunner::class)
class QrCodeInteractorTest {

    private val soraAccount = SoraAccount("address", "name")

    private lateinit var qrCodeInteractor: QrCodeInteractor

    @Mock
    private lateinit var assetsRepository: AssetsRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var runtimeManager: RuntimeManager

    @Before
    fun init() = runTest {
        given(
            methodCall = userRepository.getCurSoraAccount()
        ).willReturn(soraAccount)

        QrCodeInteractorImpl(
            assetsRepository = assetsRepository,
            userRepository = userRepository,
            runtimeManager = runtimeManager
        ).apply { qrCodeInteractor = this }
    }

    @Test
    fun `process qr called`() = runTest {
        val content = "substrate:notMyAddress:en:part4:part5"

        given(
            methodCall = runtimeManager
                .isAddressOk("notMyAddress")
        ).willReturn(true)

        given(
            methodCall = assetsRepository
                .isWhitelistedToken("part5")
        ).willReturn(true)

        val result = runCatching {
            qrCodeInteractor.processQrResult(content)
        }

        Assert.assertTrue(
            result.isSuccess
        )

        Assert.assertTrue(
            result.getOrThrow() == Triple(
                "notMyAddress",
                "part5",
                null
            )
        )
    }

    @Test
    fun `process qr called with wrong qr data`() = runTest {
        val content = "substrate:notMyAddress:en:tjj:qwe"

        given(
            methodCall = runtimeManager.isAddressOk(
                address = "notMyAddress"
            )
        ).willReturn(false)

        val result = runCatching {
            qrCodeInteractor.processQrResult(content)
        }

        Assert.assertTrue(
            result.isFailure
        )

        val exception = result.exceptionOrNull()!!
        Assert.assertTrue(
            exception is QrException &&
                exception.kind == QrException.Kind.USER_NOT_FOUND
        )
    }

    @Test
    fun `process qr called with users qr data`() = runTest {
        val content = "substrate:address:en:tjj:qwe"

        val result = runCatching {
            qrCodeInteractor.processQrResult(content)
        }

        Assert.assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()!!

        Assert.assertTrue(
            exception is QrException &&
                exception.kind == QrException.Kind.SENDING_TO_MYSELF
        )
    }
}
