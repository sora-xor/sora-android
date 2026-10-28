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

package jp.co.soramitsu.common.view

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.neon.NeumorphicSurface
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AmountCardIcon(
    testTagId: String? = null,
    @DrawableRes res: Int,
    text: String,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val n = MaterialTheme.neon
    Column(
        modifier = Modifier.widthIn(max = 112.dp)
            .then(if (testTagId != null) Modifier.testTagAsId(testTagId) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = isEnabled,
                role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeumorphicSurface(shape = androidx.compose.foundation.shape.CircleShape,
            interactionSource = interaction, modifier = Modifier.size(Size.Large)) {
            androidx.compose.material.Icon(painterResource(res), contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (!isEnabled) n.textDisabled else if (pressed) n.cyan else n.textSecondary)
        }
        Text(text = text, modifier = Modifier.padding(top = Dimens.x1),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.customTypography.textS,
            color = if (isEnabled) n.textSecondary else n.textDisabled)
    }
}
