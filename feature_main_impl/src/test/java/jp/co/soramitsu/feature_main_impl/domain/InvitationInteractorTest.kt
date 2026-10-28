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

package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.given

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class InvitationInteractorTest {

    @Mock
    private lateinit var manager: SoraConfigManager

    private lateinit var interactor: InvitationInteractor

    private val invitationLink = "github.io"

    @Before
    fun setUp() = runTest {
        given(manager.getInviteLink()).willReturn(invitationLink)
        interactor = InvitationInteractor(manager)
    }

    @Test
    fun `getInviteLink() calls getInvitationLink from userRepository`() = runTest {
        val link = interactor.getInviteLink()
        assertTrue(link.contains(invitationLink))
    }
}
