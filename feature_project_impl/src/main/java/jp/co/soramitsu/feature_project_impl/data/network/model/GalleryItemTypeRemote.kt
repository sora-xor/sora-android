/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.network.model

import com.google.gson.annotations.SerializedName

enum class GalleryItemTypeRemote {
    @SerializedName("IMAGE") IMAGE,
    @SerializedName("VIDEO") VIDEO
}