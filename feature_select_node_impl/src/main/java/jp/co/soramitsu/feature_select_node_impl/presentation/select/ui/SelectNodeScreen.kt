/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeumorphButton
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import jp.co.soramitsu.feature_select_node_impl.R
import jp.co.soramitsu.feature_select_node_impl.presentation.select.SelectNodeViewModel
import jp.co.soramitsu.feature_select_node_impl.presentation.select.model.SelectNodeState
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
        onRemoveNode = viewModel::onRemoveNode,
        onRemoveCanceled = viewModel::onRemoveNodeCanceled,
        onRemoveConfirmed = viewModel::onRemoveNodeConfirmed,
        onEditNode = viewModel::onEditNode,
        onSwitchCanceled = viewModel::onNodeSwitchCanceled,
        onSwitchConfirmed = viewModel::onNodeSwitchConfirmed
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SelectNodeContent(
    scrollState: ScrollState,
    state: SelectNodeState,
    onNodeSelected: (Node) -> Unit,
    onAddCustomNode: () -> Unit,
    onRemoveNode: (Node) -> Unit,
    onRemoveCanceled: () -> Unit,
    onRemoveConfirmed: () -> Unit,
    onEditNode: (Node) -> Unit,
    onSwitchCanceled: () -> Unit,
    onSwitchConfirmed: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(scrollState),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.customColors.bgPage)
                .padding(Dimens.x2)
                .align(Alignment.TopCenter)
        ) {
            if (state.defaultNodes.isNotEmpty()) {
                DefaultNodes(
                    modifier = Modifier.fillMaxWidth(),
                    nodes = state.defaultNodes,
                    onNodeSelected = onNodeSelected
                )
            }

            if (state.customNodes.isNotEmpty()) {
                CustomNodes(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = Dimens.x3),
                    nodes = state.customNodes,
                    onNodeSelected = onNodeSelected,
                    onRemove = onRemoveNode,
                    onEdit = onEditNode
                )
            }

            NeumorphButton(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.x2),
                label = stringResource(id = R.string.select_node_add_custom_node),
                textStyle = MaterialTheme.customTypography.buttonM,
                textColor = MaterialTheme.customColors.accentPrimary,
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

@Preview
@Composable
private fun PreviewSelectNode() {
    SoraAppTheme {
        SelectNodeContent(
            scrollState = rememberScrollState(),
            state = SelectNodeState(
                defaultNodes = listOf(
                    Node(
                        "Sora",
                        "SORA based on SORA Parliament Ministry of Finance",
                        "wss://ws.mof.sora.org",
                        isSelected = true,
                        isDefault = true,
                    ),
                    Node(
                        "Sora",
                        "SORA based on SORA Parliament Ministry of Finance",
                        "wss://ws.mof2.sora.org",
                        isSelected = false,
                        isDefault = true,
                    ),
                    Node(
                        "Sora",
                        "SORA based on SORA Parliament Ministry of Finance",
                        "wss://ws.mof3.sora.org",
                        isSelected = false,
                        isDefault = true,
                    )
                ),
                customNodes = listOf(
                    Node(
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
            onRemoveNode = {},
            onRemoveConfirmed = {},
            onRemoveCanceled = {},
            onEditNode = {},
            onSwitchConfirmed = {},
            onSwitchCanceled = {}
        )
    }
}
