/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_api.domain.model

import android.os.Parcel
import android.os.Parcelable

data class GalleryItem(
    val type: GalleryItemType,
    val url: String,
    val preview: String,
    val duration: Int
) : Parcelable {

    constructor(parcel: Parcel) : this(
        if (parcel.readInt() == 0) GalleryItemType.IMAGE else GalleryItemType.VIDEO,
        parcel.readString(),
        parcel.readString(),
        parcel.readInt())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(if (GalleryItemType.IMAGE == type) 0 else 1)
        parcel.writeString(url)
        parcel.writeString(preview)
        parcel.writeInt(duration)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<GalleryItem> {
        override fun createFromParcel(parcel: Parcel): GalleryItem {
            return GalleryItem(parcel)
        }

        override fun newArray(size: Int): Array<GalleryItem?> {
            return arrayOfNulls(size)
        }
    }
}