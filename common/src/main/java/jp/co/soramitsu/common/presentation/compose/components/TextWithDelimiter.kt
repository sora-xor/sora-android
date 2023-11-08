package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TextWithDelimiter(text1: String, text2: String, delimiter: String, color: Color, style: TextStyle) {
    Row {
        Text(
            modifier = Modifier
                .wrapContentSize(),
            text = text1,
            style = style,
            overflow = TextOverflow.Ellipsis,
            color = color,
            maxLines = 1,
        )
        Text(
            modifier = Modifier
                .wrapContentSize(),
            text = delimiter,
            style = style,
            overflow = TextOverflow.Ellipsis,
            color = color,
            maxLines = 1,
        )
        Text(
            modifier = Modifier
                .wrapContentSize(),
            text = text2,
            style = style,
            overflow = TextOverflow.Ellipsis,
            color = color,
            maxLines = 1,
        )
    }
}
