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

package jp.co.soramitsu.common.presentation.compose.neon

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.component.text.FittedLabel
import jp.co.soramitsu.ui_core.lit.litPad
import jp.co.soramitsu.ui_core.lit.litWell
import jp.co.soramitsu.ui_core.theme.customTypography

/** A choice of a few: a track let into the surface, the chosen one a raised pad with the accent's ring. */
@Composable
fun NeonSegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    // Every segment is as tall as the tallest: where one label takes two lines (a narrow screen, larger text,
    // Japanese), the chosen pad still covers its whole segment and all of them can be touched over their height.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup().litWell(radius = 16.dp, depth = 2.dp).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            // A label a little too long for its segment is set smaller before it is broken over two lines.
            FittedLabel(label,
                style = MaterialTheme.customTypography.textSBold.copy(
                    color = if (active) n.textPrimary else n.textSecondary, textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
                    .then(if (active) Modifier.litPad(radius = 12.dp, elevation = 6.dp).border(2.dp, n.cyan, RoundedCornerShape(12.dp)) else Modifier)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp, vertical = 12.dp))
        }
    }
}
