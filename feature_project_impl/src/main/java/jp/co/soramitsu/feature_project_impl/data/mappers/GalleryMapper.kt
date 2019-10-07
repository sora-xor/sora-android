/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.mappers

import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItemType
import jp.co.soramitsu.feature_project_impl.data.network.model.GalleryItemRemote
import jp.co.soramitsu.feature_project_impl.data.network.model.GalleryItemTypeRemote

fun mapGalleryRemoteToGallery(galleryItemRemote: GalleryItemRemote): GalleryItem {
    return with(galleryItemRemote) {
        GalleryItem(
            mapGalleryItemTypeDtoToGalleryItemType(type),
            url,
            preview ?: "",
            duration ?: 0
        )
    }
}

fun mapGalleryItemTypeDtoToGalleryItemType(galleryItemTypeRemote: GalleryItemTypeRemote): GalleryItemType {
    return when (galleryItemTypeRemote) {
        GalleryItemTypeRemote.IMAGE -> GalleryItemType.IMAGE
        GalleryItemTypeRemote.VIDEO -> GalleryItemType.VIDEO
    }
}