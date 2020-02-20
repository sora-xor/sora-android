/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.mappers

import jp.co.soramitsu.core_db.model.DiscussionLinkLocal
import jp.co.soramitsu.core_db.model.GalleryItemLocal
import jp.co.soramitsu.core_db.model.GalleryItemTypeLocal
import jp.co.soramitsu.core_db.model.ProjectDetailsLocal
import jp.co.soramitsu.core_db.model.ProjectDetailsWithGalleryLocal
import jp.co.soramitsu.core_db.model.ProjectLocal
import jp.co.soramitsu.core_db.model.ProjectStatusLocal
import jp.co.soramitsu.feature_project_api.domain.model.DiscussionLink
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItemType
import jp.co.soramitsu.feature_project_api.domain.model.Project
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import jp.co.soramitsu.feature_project_api.domain.model.ProjectStatus
import jp.co.soramitsu.feature_project_impl.data.network.model.DiscussionLinkRemote
import jp.co.soramitsu.feature_project_impl.data.network.model.GalleryItemRemote
import jp.co.soramitsu.feature_project_impl.data.network.model.GalleryItemTypeRemote
import jp.co.soramitsu.feature_project_impl.data.network.model.ProjectDetailsRemote
import jp.co.soramitsu.feature_project_impl.data.network.model.ProjectRemote
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.net.URL
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class ProjectMapperTest {

    private val project = Project(
        "id",
        URL("http://link.sora"),
        URL("http://link.sora"),
        "name",
        "email",
        "description",
        "detailedDescription",
        Date(0),
        100,
        200,
        2,
        3,
        BigDecimal.TEN,
        true,
        false,
        ProjectStatus.OPEN,
        Date(0)
    )
    private val projectLocal = ProjectLocal(
        "id",
        URL("http://link.sora"),
        URL("http://link.sora"),
        "name",
        "email",
        "description",
        "detailedDescription",
        0,
        100,
        200,
        2,
        3,
        BigDecimal.TEN,
        true,
        false,
        ProjectStatusLocal.OPEN,
        0
    )
    private val projectRemote = ProjectRemote(
        "id",
        "name",
        "email",
        "description",
        "detailedDescription",
        100,
        200,
        0,
        URL("http://link.sora"),
        "OPEN",
        URL("http://link.sora"),
        true,
        false,
        2,
        3,
        BigDecimal.TEN,
        0
    )
    private val projectDetails = ProjectDetails(
        "id",
        URL("http://link.sora"),
        URL("http://link.sora"),
        "name",
        "email",
        "description",
        "detailedDescription",
        Date(0),
        100,
        200,
        2,
        3,
        BigDecimal.TEN,
        true,
        false,
        mutableListOf(GalleryItem(GalleryItemType.IMAGE, "url", "preview", 0)),
        ProjectStatus.OPEN,
        Date(0),
        DiscussionLink("title", "link")
    )
    private val projectDetailsRemote = ProjectDetailsRemote(
        "id",
        "name",
        "email",
        "description",
        "detailedDescription",
        100,
        200,
        0,
        URL("http://link.sora"),
        "OPEN",
        URL("http://link.sora"),
        true,
        false,
        2,
        3,
        BigDecimal.TEN,
        mutableListOf(GalleryItemRemote(GalleryItemTypeRemote.IMAGE, "url", "preview", 0)),
        0,
        DiscussionLinkRemote("title", "link")
    )
    private val projectDetailsLocal = ProjectDetailsWithGalleryLocal(ProjectDetailsLocal(
        "id",
        URL("http://link.sora"),
        URL("http://link.sora"),
        "name",
        "email",
        "description",
        "detailedDescription",
        0,
        100,
        200,
        2,
        3,
        BigDecimal.TEN,
        true,
        false,
        ProjectStatusLocal.OPEN,
        0,
        DiscussionLinkLocal("title", "link")
    ))


    @Test
    fun `map project remote to project called`() {
        assertEquals(project, mapProjectRemoteToProject(projectRemote))
    }

    @Test
    fun `map project details remote to project called`() {
        assertEquals(projectDetails, mapProjectDetailsRemoteToProject(projectDetailsRemote))
    }

    @Test
    fun `map project to project local called`() {
        assertEquals(projectLocal, mapProjectToProjectLocal(project))
    }

    @Test
    fun `map project details local to project details called`() {
        projectDetailsLocal.gallery = mutableListOf(GalleryItemLocal(0,"id", GalleryItemTypeLocal.IMAGE, "url", "preview", 0))
        assertEquals(projectDetails, mapProjectDetailsLocalToProjectDetails(projectDetailsLocal))
    }
}