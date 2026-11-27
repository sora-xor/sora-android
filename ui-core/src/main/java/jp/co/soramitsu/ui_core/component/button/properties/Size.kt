package jp.co.soramitsu.ui_core.component.button.properties

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.modifier.applyIf

object Size {

    /**
     * 56 dp
     */
    val Large: Dp
        get() = 56.dp

    /**
     * 40 dp
     */
    val Small: Dp
        get() = 40.dp

    /**
     * 32 dp
     */
    val ExtraSmall: Dp
        get() = 32.dp
}

internal fun Modifier.buttonSize(size: Dp, shape: Shape, shadow: Boolean, enabled: Boolean): Modifier = composed {
    (if (shape == CircleShape) {
        this.requiredSizeIn(minWidth = size, minHeight = size)
    } else {
        this.requiredHeightIn(min = size)
    }).applyIf(size != Size.ExtraSmall && shadow && enabled && !isSystemInDarkTheme()) {
        shadow(
            elevation = if (size == Size.Small) 16.dp else 24.dp,
            shape = shape,
            clip = false,
            ambientColor = Color(0xff000000),
            spotColor = Color(0xff777777),
        )
    }
}
