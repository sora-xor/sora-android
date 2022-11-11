/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPunched
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import jp.co.soramitsu.feature_select_node_impl.R
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun DefaultNodes(
    modifier: Modifier = Modifier,
    nodes: List<Node>,
    onNodeSelected: (Node) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier.padding(bottom = Dimens.x1),
            text = stringResource(R.string.select_node_default_nodes),
            style = MaterialTheme.customTypography.headline4,
            color = MaterialTheme.customColors.fgSecondary
        )

        NeuCardPunched(
            radius = 32
        ) {
            Column {
                nodes.forEachIndexed { index, node ->
                    DefaultNode(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNodeSelected(node) }
                            .padding(
                                start = Dimens.x2,
                                end = Dimens.x2,
                                top = if (index == 0) { Dimens.x3 } else { Dimens.x2 },
                                bottom = if (index == nodes.lastIndex) { Dimens.x3 } else { Dimens.x2 },
                            ),
                        node = node,
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultNode(
    modifier: Modifier = Modifier,
    node: Node
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.x2)
    ) {
        Box(
            modifier = Modifier.size(Dimens.x3),
            contentAlignment = Alignment.Center
        ) {
            if (node.isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_checkmark),
                    tint = MaterialTheme.customColors.accentPrimary,
                    contentDescription = null
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                modifier = Modifier,
                text = node.name,
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = node.address,
                style = MaterialTheme.customTypography.textXS,
                color = MaterialTheme.customColors.fgSecondary
            )
        }
    }
}
