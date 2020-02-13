/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.mappers

import jp.co.soramitsu.core_db.model.GalleryItemLocal
import jp.co.soramitsu.core_db.model.GalleryItemTypeLocal
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItemType
import jp.co.soramitsu.feature_project_impl.data.network.model.GalleryItemRemote
import jp.co.soramitsu.feature_project_impl.data.network.model.GalleryItemTypeRemote

fun mapGalleryRemoteToGallery(galleryItemRemote: GalleryItemRemote): GalleryItem {
    return with(galleryItemRemote) {
        GalleryItem(
            mapGalleryItemTypeRemoteToGalleryItemType(type),
            url,
            preview ?: "",
            duration ?: 0
        )
    }
}

fun mapGalleryItemTypeRemoteToGalleryItemType(galleryItemTypeRemote: GalleryItemTypeRemote): GalleryItemType {
    return when (galleryItemTypeRemote) {
        GalleryItemTypeRemote.IMAGE -> GalleryItemType.IMAGE
        GalleryItemTypeRemote.VIDEO -> GalleryItemType.VIDEO
    }
}

fun mapGalleryTypeLocalToGalleryType(galleryItemTypeLocal: GalleryItemTypeLocal): GalleryItemType {
    return when (galleryItemTypeLocal) {
        GalleryItemTypeLocal.VIDEO -> GalleryItemType.VIDEO
        GalleryItemTypeLocal.IMAGE -> GalleryItemType.IMAGE
    }
}

fun mapGalleryTypeToGalleryTypeLocal(galleryType: GalleryItemType): GalleryItemTypeLocal {
    return when (galleryType) {
        GalleryItemType.VIDEO -> GalleryItemTypeLocal.VIDEO
        GalleryItemType.IMAGE -> GalleryItemTypeLocal.IMAGE
    }
}

fun mapGalleryLocalToGallery(galleryLocal: GalleryItemLocal): GalleryItem {
    return with(galleryLocal) {
        GalleryItem(mapGalleryTypeLocalToGalleryType(type), url, preview, duration)
    }
}

fun mapGalleryToGalleryLocal(galleryItem: GalleryItem, projectId: String): GalleryItemLocal {
    return with(galleryItem) {
        GalleryItemLocal(0, projectId, mapGalleryTypeToGalleryTypeLocal(type), url, preview, duration)
    }
}