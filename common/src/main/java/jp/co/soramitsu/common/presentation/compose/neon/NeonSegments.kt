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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun NeonSegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Row(modifier.fillMaxWidth().selectableGroup().background(n.neuBg, RoundedCornerShape(20.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Text(label,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (active) n.glassSolid else n.neuBg, RoundedCornerShape(16.dp))
                    .then(if (active) Modifier.border(2.dp, n.cyan, RoundedCornerShape(16.dp)) else Modifier)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                color = if (active) n.textPrimary else n.textSecondary,
                textAlign = TextAlign.Center, style = MaterialTheme.customTypography.textSBold)
        }
    }
}
