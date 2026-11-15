package jp.co.soramitsu.ui_core.component.button

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.sacred.SacredSeal
import jp.co.soramitsu.ui_core.sacred.SacredSealState
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.elevation
import jp.co.soramitsu.ui_core.theme.opacity

/**
 * What a button turns into while what it sent is in flight: the open circle of heaven turning round the square
 * of earth standing on its corner ([SacredSeal]).
 */
@Composable
internal fun ButtonLoader(
    size: Dp
) {
    Box(
        modifier = Modifier.size(size)
            .clip(CircleShape)
            .background(MaterialTheme.customColors.fgPrimary.withOpacity(MaterialTheme.opacity.actionBgDisabled)),
        contentAlignment = Alignment.Center
    ) {
        val ink = MaterialTheme.customColors.fgPrimary.withOpacity(MaterialTheme.opacity.actionFgDisabled)
        SacredSeal(
            state = SacredSealState.WORKING,
            working = ink,
            settled = ink,
            broken = ink,
            diameter = size * 0.58f,
            loops = ValueAnimator.areAnimatorsEnabled(),
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoaderWrapper(
    modifier: Modifier,
    loading: Boolean,
    loaderSize: Dp,
    buttonElevation: Dp = MaterialTheme.elevation.l,
    button: @Composable (Modifier, Dp) -> Unit
) {
    val animationDuration = 300
    val animationDelay = 150
    val shadow by animateDpAsState(
        if (loading) {
            0.dp
        } else {
            buttonElevation
        }
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = loading,
                enter = scaleIn(tween(animationDuration, animationDelay)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ButtonLoader(
                    size = loaderSize
                )
            }
        }

        AnimatedVisibility(
            visible = !loading,
            enter = scaleIn(tween(animationDuration, animationDelay)) + fadeIn(
                tween(
                    animationDuration,
                    animationDelay
                )
            ),
            exit = scaleOut(tween(animationDuration)) + fadeOut(
                tween(
                    animationDuration,
                    animationDelay
                )
            )
        ) {
            button(modifier, shadow)
        }
    }
}

@Composable
@Preview
private fun PreviewLoadingButton() {
    val loading = remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LoaderWrapper(
            modifier = Modifier.fillMaxWidth().padding(Dimens.x1),
            loading = loading.value,
            loaderSize = Size.Large
        ) { modifier, elevation ->
            BleachedButton(
                modifier = modifier,
                text = "Bleached Button",
                leftIcon = painterResource(R.drawable.ic_plus),
                rightIcon = painterResource(R.drawable.ic_plus),
                onClick = {},
                size = Size.Large,
                order = Order.SECONDARY
            )
        }

        LoaderWrapper(
            modifier = Modifier.fillMaxWidth().padding(Dimens.x1),
            loading = loading.value,
            loaderSize = Size.Large
        ) { modifier, elevation ->
            FilledButton(
                modifier = modifier.fillMaxWidth(),
                text = "Filled Button",
                leftIcon = painterResource(R.drawable.ic_plus),
                rightIcon = painterResource(R.drawable.ic_plus),
                onClick = {},
                size = Size.Large,
                order = Order.PRIMARY
            )
        }

        LoaderWrapper(
            modifier = Modifier.fillMaxWidth().padding(Dimens.x1),
            loading = loading.value,
            loaderSize = Size.Large
        ) { modifier, _ ->
            OutlinedButton(
                modifier = modifier,
                text = "Outlined Button",
                leftIcon = painterResource(R.drawable.ic_plus),
                rightIcon = painterResource(R.drawable.ic_plus),
                onClick = {},
                size = Size.Large,
                order = Order.PRIMARY
            )
        }

        LoaderWrapper(
            modifier = Modifier.fillMaxWidth().padding(Dimens.x1),
            loading = loading.value,
            loaderSize = Size.Small
        ) { modifier, _ ->
            TextButton(
                modifier = modifier,
                text = "Text Button",
                leftIcon = painterResource(R.drawable.ic_plus),
                rightIcon = painterResource(R.drawable.ic_plus),
                onClick = {},
                size = Size.Small,
                order = Order.PRIMARY,
            )
        }

        LoaderWrapper(
            modifier = Modifier.fillMaxWidth().padding(Dimens.x1),
            loading = loading.value,
            loaderSize = Size.ExtraSmall
        ) { modifier, _ ->
            TonalButton(
                modifier = modifier,
                text = "Tonal Button",
                leftIcon = painterResource(R.drawable.ic_plus),
                rightIcon = painterResource(R.drawable.ic_plus),
                onClick = {},
                size = Size.ExtraSmall,
                order = Order.PRIMARY,
            )
        }

        BleachedButton(
            modifier = Modifier.padding(Dimens.x3).fillMaxWidth(),
            text = "toggle loading state",
            onClick = { loading.value = !loading.value },
            size = Size.Small,
            order = Order.SECONDARY
        )
    }
}
