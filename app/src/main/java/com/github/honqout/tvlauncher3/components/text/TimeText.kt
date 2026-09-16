package com.github.honqout.tvlauncher3.components.text

import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.TimeViewModel
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_EXTRA_SMALL

/**
 * Format the current time with the given [DateUtils] flags and render it as a single [Text].
 */
@Composable
private fun CurrentTimeText(
    modifier: Modifier,
    viewModel: TimeViewModel,
    formatFlags: Int,
    color: Color,
    fontSize: TextUnit
) {
    val context = LocalContext.current
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()

    val formatted = remember(currentTime, formatFlags) {
        DateUtils.formatDateTime(context, currentTime, formatFlags)
    }

    Text(
        text = formatted,
        modifier = modifier,
        color = color,
        fontSize = fontSize
    )
}

@Composable
fun TimeText(
    modifier: Modifier = Modifier,
    viewModel: TimeViewModel,
    color: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = FONT_SIZE_EXTRA_SMALL
) {
    CurrentTimeText(
        modifier = modifier,
        viewModel = viewModel,
        formatFlags = DateUtils.FORMAT_SHOW_TIME,
        color = color,
        fontSize = fontSize
    )
}

@Composable
fun DateAndWeekdayText(
    modifier: Modifier = Modifier,
    viewModel: TimeViewModel,
    color: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = FONT_SIZE_EXTRA_SMALL
) {
    CurrentTimeText(
        modifier = modifier,
        viewModel = viewModel,
        formatFlags = DateUtils.FORMAT_NO_YEAR or
                DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_SHOW_WEEKDAY or
                DateUtils.FORMAT_ABBREV_WEEKDAY,
        color = color,
        fontSize = fontSize
    )
}
