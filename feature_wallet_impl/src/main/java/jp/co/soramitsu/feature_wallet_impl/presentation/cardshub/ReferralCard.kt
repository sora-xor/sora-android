/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun ReferralCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // .wrapContentHeight()
            // .height(IntrinsicSize.Min)
            .background(Color.White, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = Dimens.x2)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Dimens.x1)
                .align(alignment = Alignment.TopStart)
                .wrapContentSize()
        ) {
            Text(text = "Invite friends")
            Text(text = "Get 10% of your")
            Text(text = "Start inviting")
        }
        Image(
            painter = painterResource(id = R.drawable.image_friends),
            modifier = Modifier
                .height(120.dp)
                .align(alignment = Alignment.TopEnd)
                .offset(x = (0).dp, y = (-20).dp),
            contentDescription = null
        )
    }
}
