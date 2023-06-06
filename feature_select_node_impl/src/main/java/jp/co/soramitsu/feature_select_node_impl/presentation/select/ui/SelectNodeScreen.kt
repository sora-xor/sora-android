/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_select_node_impl.R
import jp.co.soramitsu.feature_select_node_impl.presentation.select.SelectNodeViewModel
import jp.co.soramitsu.feature_select_node_impl.presentation.select.model.SelectNodeState
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.item.SelectableItem
import jp.co.soramitsu.ui_core.component.menu.MenuItem
import jp.co.soramitsu.ui_core.resources.Dimens.x2
import jp.co.soramitsu.ui_core.resources.Dimens.x3
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SelectNodeScreen(
    scrollState: ScrollState,
    viewModel: SelectNodeViewModel
) {
    val state = viewModel.state

    SelectNodeContent(
        scrollState,
        state,
        onNodeSelected = viewModel::onNodeSelected,
        onAddCustomNode = viewModel::onAddCustomNode,
        onRemoveCanceled = viewModel::onRemoveNodeCanceled,
        onRemoveConfirmed = viewModel::onRemoveNodeConfirmed,
        onSwitchCanceled = viewModel::onNodeSwitchCanceled,
        onSwitchConfirmed = viewModel::onNodeSwitchConfirmed,
        onNodeRemoveClicked = viewModel::onRemoveNode,
        onNodeEditClicked = viewModel::onEditNode
    )
}

@Composable
private fun SelectNodeContent(
    scrollState: ScrollState,
    state: SelectNodeState,
    onNodeSelected: (ChainNode) -> Unit,
    onAddCustomNode: () -> Unit,
    onRemoveCanceled: () -> Unit,
    onRemoveConfirmed: () -> Unit,
    onSwitchCanceled: () -> Unit,
    onSwitchConfirmed: () -> Unit,
    onNodeRemoveClicked: (ChainNode) -> Unit,
    onNodeEditClicked: (ChainNode) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.customColors.bgPage)
                .padding(x2)
                .align(Alignment.TopCenter)
        ) {
            if (state.defaultNodes.isNotEmpty()) {
                NodesCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(id = R.string.select_node_default_nodes),
                    nodes = state.defaultNodes,
                    onNodeSelected = onNodeSelected,
                )
            }

            if (state.customNodes.isNotEmpty()) {
                NodesCard(
                    modifier = Modifier
                        .padding(top = x3)
                        .fillMaxWidth(),
                    title = stringResource(id = R.string.select_node_custom_nodes),
                    nodes = state.customNodes,
                    onNodeSelected = onNodeSelected,
                    onNodeEditClicked = onNodeEditClicked,
                    onNodeRemoveClicked = onNodeRemoveClicked
                )
            }
            BleachedButton(
                size = Size.Large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = x2),
                order = Order.PRIMARY,
                text = stringResource(id = R.string.select_node_add_custom_node),
                onClick = onAddCustomNode
            )
        }

        state.removeAlertState?.let {
            RemoveNodeAlertDialog(
                onRemoveCanceled = onRemoveCanceled,
                onRemoveConfirmed = onRemoveConfirmed
            )
        }

        state.switchNodeAlertState?.let {
            SwitchNodeAlert(
                onSwitchCanceled = onSwitchCanceled,
                onSwitchConfirmed = onSwitchConfirmed
            )
        }
    }
}

@Composable
fun NodesCard(
    modifier: Modifier = Modifier,
    title: String,
    nodes: List<ChainNode>,
    onNodeSelected: (ChainNode) -> Unit,
    onNodeEditClicked: ((ChainNode) -> Unit)? = null,
    onNodeRemoveClicked: ((ChainNode) -> Unit)? = null
) {
    ContentCard(
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(x3)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.customTypography.headline4,
                color = MaterialTheme.customColors.fgSecondary
            )

            nodes.forEach { node ->
                SelectableItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = x2,
                            bottom = x2
                        ),
                    isSelected = node.isSelected,
                    isMenuIconVisible = !node.isDefault,
                    title = node.name,
                    subtitle = node.address,
                    onClick = { onNodeSelected(node) },
                    menuItems = listOf(
                        MenuItem(
                            title = stringResource(id = R.string.select_node_custom_node_edit_node)
                        ),
                        MenuItem(
                            title = stringResource(id = R.string.common_remove),
                            color = MaterialTheme.customColors.statusError
                        )
                    )
                ) {
                    when (it) {
                        0 -> onNodeEditClicked?.invoke(node)
                        1 -> onNodeRemoveClicked?.invoke(node)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSelectNode() {
    SoraAppTheme {
        SelectNodeContent(
            scrollState = rememberScrollState(),
            state = SelectNodeState(
                defaultNodes = listOf(
                    ChainNode(
                        "Sora",
                        "SORA based on SORA Parliament Ministry of Finance",
                        "wss://ws.mof.sora.org",
                        isSelected = true,
                        isDefault = true,
                    ),
                    ChainNode(
                        "Sora",
                        "SORA based on SORA Parliament Ministry of Finance",
                        "wss://ws.mof2.sora.org",
                        isSelected = false,
                        isDefault = true,
                    ),
                    ChainNode(
                        "Sora",
                        "SORA based on SORA Parliament Ministry of Finance",
                        "wss://ws.mof3.sora.org",
                        isSelected = false,
                        isDefault = true,
                    )
                ),
                customNodes = listOf(
                    ChainNode(
                        "Sora",
                        "SORA based on SORA Parliament Ministry of Finance",
                        "wss://ws.mof.sora.org",
                        isSelected = false,
                        isDefault = true,
                    )
                ),
            ),
            onNodeSelected = {},
            onAddCustomNode = {},
            onRemoveConfirmed = {},
            onRemoveCanceled = {},
            onNodeEditClicked = { },
            onNodeRemoveClicked = { },
            onSwitchConfirmed = {},
            onSwitchCanceled = {}
        )
    }
}
