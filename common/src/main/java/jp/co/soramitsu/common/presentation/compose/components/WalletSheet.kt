package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/** A wallet-sized modal with scrolling content and actions that remain reachable. */
@Composable
fun WalletSheet(
    title: String,
    onClose: () -> Unit,
    dismissEnabled: Boolean = true,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = { if (dismissEnabled) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = dismissEnabled, dismissOnClickOutside = false)) {
        Surface(color = MaterialTheme.customColors.bgPage,
            contentColor = MaterialTheme.customColors.fgPrimary,
            modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(title, style = MaterialTheme.customTypography.headline2,
                        modifier = Modifier.weight(1f).padding(top = 12.dp))
                    TextButton(onClick = onClose, enabled = dismissEnabled) {
                        Text(stringResource(R.string.common_close))
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = footer)
            }
        }
    }
}
