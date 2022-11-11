/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPunched
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import jp.co.soramitsu.feature_select_node_impl.R
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun CustomNodes(
    modifier: Modifier = Modifier,
    nodes: List<Node>,
    onNodeSelected: (Node) -> Unit,
    onRemove: (Node) -> Unit,
    onEdit: (Node) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier.padding(bottom = Dimens.x1),
            text = stringResource(R.string.select_node_custom_nodes),
            style = MaterialTheme.customTypography.headline4,
            color = MaterialTheme.customColors.fgSecondary
        )

        NeuCardPunched(
            radius = 32
        ) {
            Column {
                nodes.forEachIndexed { index, node ->
                    CustomNode(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNodeSelected(node) }
                            .padding(
                                start = Dimens.x2,
                                end = Dimens.x2,
                                top = if (index == 0) {
                                    Dimens.x3
                                } else {
                                    Dimens.x2
                                },
                                bottom = if (index == nodes.lastIndex) {
                                    Dimens.x3
                                } else {
                                    Dimens.x2
                                },
                            ),
                        node = node,
                        onRemove = onRemove,
                        onEdit = onEdit
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomNode(
    modifier: Modifier = Modifier,
    node: Node,
    onRemove: (Node) -> Unit,
    onEdit: (Node) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Dimens.x3),
            contentAlignment = Alignment.Center,
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
            modifier = Modifier.weight(1f)
                .padding(start = Dimens.x2)
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = node.address,
                style = MaterialTheme.customTypography.textXS,
                color = MaterialTheme.customColors.fgSecondary,
                overflow = TextOverflow.Ellipsis
            )
        }

        val expanded = remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded.value = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_neu_dots),
                    tint = MaterialTheme.customColors.fgSecondary,
                    contentDescription = null
                )
            }

            CustomNodeMenuItems(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false },
                onRemove = {
                    expanded.value = false
                    onRemove(node)
                },
                onEdit = {
                    expanded.value = false
                    onEdit(node)
                }
            )
        }
    }
}

@Composable
private fun CustomNodeMenuItems(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(
            medium = RoundedCornerShape(MaterialTheme.borderRadius.xl)
        )
    ) {
        DropdownMenu(
            modifier = Modifier.background(MaterialTheme.customColors.bgSurface),
            expanded = expanded,
            onDismissRequest = onDismissRequest
        ) {
            DropdownMenuItem(
                onClick = onEdit
            ) {
                Text(
                    stringResource(R.string.select_node_custom_node_edit_node),
                    style = MaterialTheme.customTypography.textM,
                    color = MaterialTheme.customColors.fgPrimary
                )
            }

            DropdownMenuItem(
                onClick = onRemove
            ) {
                Text(
                    stringResource(R.string.common_remove),
                    style = MaterialTheme.customTypography.textM,
                    color = MaterialTheme.customColors.statusError
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewCustomNode() {
    SoraAppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.customColors.bgPage)
        ) {
            CustomNode(
                node = Node(
                    "Sora",
                    "SORA based on SORA Parliament Ministry of Finance",
                    "wss://ws.mof.sora.org",
                    isSelected = true,
                    isDefault = false,
                ),
                onRemove = {},
                onEdit = {}
            )
        }
    }
}
