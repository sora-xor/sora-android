/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.mappers

import jp.co.soramitsu.core_db.model.GalleryItemLocal
import jp.co.soramitsu.core_db.model.GalleryItemTypeLocal
import jp.co.soramitsu.feature_votable_api.domain.model.project.GalleryItem
import jp.co.soramitsu.feature_votable_api.domain.model.project.GalleryItemType
import jp.co.soramitsu.feature_votable_impl.data.network.model.GalleryItemRemote
import jp.co.soramitsu.feature_votable_impl.data.network.model.GalleryItemTypeRemote
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class GalleryMapperTest {

    private val galleryItemRemote = GalleryItemRemote(
        GalleryItemTypeRemote.IMAGE,
        "url",
        "preview",
        0
    )
    private val galleryItemLocal = GalleryItemLocal(
        0,
        "projectId",
        GalleryItemTypeLocal.IMAGE,
        "url",
        "preview",
        0
    )
    private val galleryItem = GalleryItem(
        GalleryItemType.IMAGE,
        "url",
        "preview",
        0
    )

    @Test
    fun `map gallery item remote to gallery item called`() {
        assertEquals(galleryItem, mapGalleryRemoteToGallery(galleryItemRemote))
    }

    @Test
    fun `map gallery item local to gallery item called`() {
        assertEquals(galleryItem, mapGalleryLocalToGallery(galleryItemLocal))
    }

    @Test
    fun `map gallery item to gallery item local called`() {
        assertEquals(galleryItemLocal, mapGalleryToGalleryLocal(galleryItem, "projectId"))
    }

    @Test
    fun `map gallery item type dto to gallery item type called`() {
        assertEquals(galleryItem.type, mapGalleryItemTypeRemoteToGalleryItemType(galleryItemRemote.type))
    }

    @Test
    fun `map gallery type local to gallery type called`() {
        assertEquals(galleryItem.type, mapGalleryTypeLocalToGalleryType(galleryItemLocal.type))
    }

    @Test
    fun `map gallery type to gallery type local called`() {
        assertEquals(galleryItemLocal.type, mapGalleryTypeToGalleryTypeLocal(galleryItem.type))
    }
}