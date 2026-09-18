package com.github.honqout.tvlauncher3.components.button

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_LARGE
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_MEDIUM
import com.github.honqout.tvlauncher3.ui.theme.SETTINGS_ACTION_BUTTON_HEIGHT
import com.github.honqout.tvlauncher3.ui.theme.SETTINGS_ACTION_BUTTON_WIDTH

@Composable
fun SettingsIconFromResource(
    @DrawableRes drawableRes: Int,
    contentDescription: String?
) {
    Icon(
        painter = painterResource(drawableRes),
        contentDescription = contentDescription,
        modifier = Modifier
            .size(size = 30.dp),
        tint = Color.White
    )
}

@Composable
private fun SettingsActionButtonTvImpl(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    title: String,
    description: String?,
    onShortClick: () -> Unit = {}
) {
    Button(
        // tv-material 1.1.0's clickable Button only fires onClick from DPAD-enter key events
        // (SurfaceClickableUtils.handleDPadEnter); it has no pointer handler at all, so touch and
        // mouse clicks must be wired up explicitly here.
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onShortClick() }
                )
            },
        onClick = onShortClick,
        onLongClick = {},
        scale = ButtonDefaults.scale(),
        colors = ButtonDefaults.colors(
            containerColor = Color.DarkGray.copy(alpha = 0.5f),
            contentColor = Color.White,
            focusedContainerColor = Color.Gray.copy(alpha = 0.5f),
            focusedContentColor = Color.White
        ),
        tonalElevation = 12.dp,
        border = ButtonDefaults.border(),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Row(
            modifier = Modifier
                .requiredSize(
                    width = SETTINGS_ACTION_BUTTON_WIDTH,
                    height = SETTINGS_ACTION_BUTTON_HEIGHT
                ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconFromResource(
                drawableRes = iconRes,
                contentDescription = contentDescription
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = FONT_SIZE_LARGE,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )

                if (!description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = description,
                        color = Color.LightGray,
                        fontSize = FONT_SIZE_MEDIUM,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsActionButtonTv(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    @StringRes contentDescriptionRes: Int,
    @StringRes titleRes: Int,
    onShortClick: () -> Unit = {}
) {
    SettingsActionButtonTvImpl(
        modifier = modifier,
        iconRes = iconRes,
        contentDescription = stringResource(contentDescriptionRes),
        title = stringResource(titleRes),
        description = null,
        onShortClick = onShortClick
    )
}

@Composable
fun SettingsActionButtonTv(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    @StringRes contentDescriptionRes: Int,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onShortClick: () -> Unit = {}
) {
    SettingsActionButtonTvImpl(
        modifier = modifier,
        iconRes = iconRes,
        contentDescription = stringResource(contentDescriptionRes),
        title = stringResource(titleRes),
        description = stringResource(descriptionRes),
        onShortClick = onShortClick
    )
}