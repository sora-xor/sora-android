/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_api.domain.model

import jp.co.soramitsu.feature_votable_api.domain.model.project.Project
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectStatus
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.net.URL
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class ProjectTest {

    private val project = Project(
        "id",
        URL("http://link.sora"),
        URL("http://link.sora"),
        "name",
        "email",
        "description",
        "detailedDescription",
        Date(0),
        123.2,
        200,
        2,
        3,
        BigDecimal.TEN,
        true,
        false,
        ProjectStatus.OPEN,
        Date(0)
    )

    @Test
    fun `project get funding percent called`() {
        val expectedFundingPercent = 61
        val result = project.getFundingPercent()

        assertEquals(expectedFundingPercent, result)
    }

    @Test
    fun `project get votes left`() {
        val expectedVotesLeft = 76
        val result = project.getVotesLeft()

        assertEquals(expectedVotesLeft, result)
    }
}