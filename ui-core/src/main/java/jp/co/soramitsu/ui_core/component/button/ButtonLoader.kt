package jp.co.soramitsu.ui_core.component.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.elevation
import jp.co.soramitsu.ui_core.theme.opacity

@Composable
internal fun ButtonLoader(
    size: Dp
) {
    val currentRotation = remember { mutableStateOf(0f) }
    val rotation = remember { Animatable(currentRotation.value) }
    val animationDuration = 3000
    val targetShift = 360f

    LaunchedEffect(Unit) {
        rotation.animateTo(
            targetValue = currentRotation.value + targetShift,
            animationSpec = infiniteRepeatable(
                animation = tween(animationDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        ) {
            currentRotation.value = value
        }
    }

    Box(
        modifier = Modifier.size(size)
            .clip(CircleShape)
            .background(MaterialTheme.customColors.fgPrimary.withOpacity(MaterialTheme.opacity.actionBgDisabled)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.rotate(rotation.value),
            painter = painterResource(R.drawable.ic_loading),
            contentDescription = null,
            tint = MaterialTheme.customColors.fgPrimary.withOpacity(MaterialTheme.opacity.actionFgDisabled)
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
