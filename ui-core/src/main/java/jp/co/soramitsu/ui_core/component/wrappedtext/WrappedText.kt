package jp.co.soramitsu.ui_core.component.wrappedtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

@Composable
fun WrappedText(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.customColors.bgSurface,
    borderColor: Color = MaterialTheme.customColors.bgSurfaceVariant,
    borderWidth: Dp = 1.dp,
    state: WrappedTextState,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)

    Row(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .border(
                border = BorderStroke(borderWidth, borderColor),
                shape = shape
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.leadingIcon?.let {
            Icon(
                modifier = Modifier
                    .padding(start = Dimens.x2)
                    .align(Alignment.CenterVertically),
                painter = painterResource(it),
                contentDescription = null,
                tint = Color.Unspecified
            )
        }

        val startPadding = if (state.leadingIcon == null) Dimens.x2 else Dimens.x1
        val endPadding = if (state.trailingIcon == null) Dimens.x2 else Dimens.x1

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = startPadding,
                    end = endPadding,
                    top = Dimens.x1_5,
                    bottom = Dimens.x1_5
                )
        ) {
            state.title?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    color = MaterialTheme.customColors.fgSecondary,
                    style = MaterialTheme.customTypography.textXS,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = state.text,
                maxLines = 1,
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textM,
                overflow = TextOverflow.Ellipsis
            )
        }

        state.trailingIcon?.let {
            Icon(
                modifier = Modifier
                    .padding(end = Dimens.x2)
                    .align(Alignment.CenterVertically),
                painter = painterResource(it),
                contentDescription = null,
                tint = Color.Unspecified
            )
        }
    }
}

@Preview
@Composable
private fun PreviewWrappedText() {
    val state1 = remember {
        mutableStateOf(
            WrappedTextState(
                text = "polkaswap.io/#/referral/cnVyaue39dssBccnVyaue39dssBc",
                title = "Invitations Link",
                leadingIcon = R.drawable.ic_plus,
                trailingIcon = R.drawable.ic_plus,
            )
        )
    }
    val state2 = remember {
        mutableStateOf(
            WrappedTextState(
                text = "polkaswap.io/#/referral/cnVyaue39dssBc",
                title = "Invitations Link",
                trailingIcon = R.drawable.ic_plus,
            )
        )
    }
    val state3 = remember {
        mutableStateOf(
            WrappedTextState(
                text = "polkaswap.io/#/referral/cnVyaue39dssBc",
                title = "Invitations Link",
                leadingIcon = R.drawable.ic_plus,
            )
        )
    }
    val state4 = remember {
        mutableStateOf(
            WrappedTextState(
                text = "polkaswap.io/#/referral/cnVyaue39dssBcpolkaswap.io/#/referral/cnVyaue39dssB",
                leadingIcon = R.drawable.ic_plus,
                trailingIcon = R.drawable.ic_plus,
            )
        )
    }
    val state5 = remember {
        mutableStateOf(
            WrappedTextState(
                text = "polkaswap.io/#/referral/cnVyaue39dssBc",
                title = "polkaswap.io/#/referral/cnVyaue39dssBpolkaswap.io/#/referral/cnVyaue39dssB",
                leadingIcon = R.drawable.ic_plus,
                trailingIcon = R.drawable.ic_plus,
            )
        )
    }
    val state6 = remember {
        mutableStateOf(
            WrappedTextState(
                text = "polkaswap.io/#/referral/cnVyaue39dssBc",
            )
        )
    }


    Column {
        WrappedText(
            modifier = Modifier,
            state = state1.value,
            onClick = {}
        )
        WrappedText(
            modifier = Modifier.height(100.dp),
            state = state1.value,
            onClick = {}
        )
        WrappedText(
            modifier = Modifier,
            state = state2.value,
            onClick = {}
        )
        WrappedText(
            modifier = Modifier,
            state = state3.value,
            onClick = {}
        )
        WrappedText(
            modifier = Modifier,
            state = state4.value,
            onClick = {}
        )
        WrappedText(
            modifier = Modifier,
            state = state5.value,
            onClick = {}
        )
        WrappedText(
            modifier = Modifier,
            state = state6.value,
            onClick = {}
        )
    }
}
