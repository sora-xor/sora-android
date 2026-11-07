package jp.co.soramitsu.ui_core.component.checkbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun CheckboxButton(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    text: String,
    onClick: () -> Unit,
    iconSelectedTint : Color = MaterialTheme.customColors.accentPrimary,
    iconUnselectedTint : Color = MaterialTheme.customColors.bgSurfaceInverted
) {
    val shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
    Row(
        modifier = modifier
            .padding(vertical = Dimens.x1_2)
            .border(
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.customColors.accentPrimary else MaterialTheme.customColors.bgSurfaceVariant
                ),
                shape = shape,
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.x1, horizontal = Dimens.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(Dimens.x3),
            painter = painterResource(id = if (isSelected) R.drawable.ic_cirlce_selected else R.drawable.ic_circle_empty),
            contentDescription = null,
            colorFilter = ColorFilter.tint( if (isSelected) iconSelectedTint else iconUnselectedTint)
        )

        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.x2),
            style = MaterialTheme.customTypography.paragraphS,
            color = MaterialTheme.customColors.fgPrimary,
            text = text
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun PreviewCheckboxButton() {
    Column(modifier = Modifier.fillMaxSize()) {
        CheckboxButton(
            isSelected = true,
            onClick = {  },
            text = "text 1",
        )
        CheckboxButton(
            isSelected = false,
            onClick = {  },
            text = "text 2",
        )
        CheckboxButton(
            isSelected = true,
            onClick = {  },
            text = "text 3",
        )
    }
}
