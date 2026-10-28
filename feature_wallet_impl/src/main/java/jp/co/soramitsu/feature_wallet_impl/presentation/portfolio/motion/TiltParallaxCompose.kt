package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** The smoothed viewer deflection in [-1, 1]. Read it only in the draw phase. */
@Stable
class TiltState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

/**
 * Wires [TiltParallaxController] to a Compose [TiltState]. [enabled] is the surface flag;
 * [heroVisible] is whether the moving surface is on screen; [pinned] is the debug-only fixed
 * deflection (null in production and whenever no override is set).
 */
@Composable
fun rememberTiltParallax(
    enabled: Boolean,
    heroVisible: Boolean,
    pinned: Deflection?,
): TiltState {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { TiltState() }
    val enabledState by rememberUpdatedState(enabled)
    val controller = remember(context) {
        TiltParallaxController(context, surfaceEnabled = { enabledState }) { x, y ->
            state.x = x
            state.y = y
        }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.start()
                Lifecycle.Event.ON_STOP -> controller.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.start()
        // Battery saver and the animator scale can change while the surface is shown.
        val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = controller.refresh()
        }
        ContextCompat.registerReceiver(
            context,
            powerReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val unregisterScale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AnimatorScale.listen(controller::refresh)
        } else {
            null
        }
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(powerReceiver)
            unregisterScale?.invoke()
            controller.stop()
            controller.dispose()
        }
    }
    LaunchedEffect(controller, enabled) { controller.refresh() }
    LaunchedEffect(controller, heroVisible) { controller.setHeroVisible(heroVisible) }
    LaunchedEffect(controller, pinned) { controller.setPinnedDeflection(pinned) }
    return state
}

/** The system animator scale (Remove animations) can change while the surface is shown. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object AnimatorScale {
    fun listen(onChange: () -> Unit): () -> Unit {
        val listener = ValueAnimator.DurationScaleChangeListener { onChange() }
        ValueAnimator.registerDurationScaleChangeListener(listener)
        return { ValueAnimator.unregisterDurationScaleChangeListener(listener) }
    }
}

/**
 * One parallax layer: opposite to the input, nearer layers move more. [rotateDeg] rotates with the
 * horizontal deflection. Read in the draw phase, so no recomposition.
 */
fun Modifier.tiltLayer(
    state: TiltState,
    depthXdp: Float,
    depthYdp: Float,
    rotateDeg: Float = 0f,
): Modifier = graphicsLayer {
    val offset =
        layerOffset(state.x.toDouble(), state.y.toDouble(), depthXdp.toDouble(), depthYdp.toDouble(), 1.0)
    translationX = offset.dx.toFloat() * density
    translationY = offset.dy.toFloat() * density
    rotationZ = state.x * rotateDeg
}
