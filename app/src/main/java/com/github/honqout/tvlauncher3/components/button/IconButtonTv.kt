package com.github.honqout.tvlauncher3.components.button

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import com.github.honqout.tvlauncher3.ui.theme.ButtonContainerFocused
import com.github.honqout.tvlauncher3.ui.theme.ButtonContentDefault
import com.github.honqout.tvlauncher3.ui.theme.ButtonContentFocused
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainer

@Composable
fun IconButtonTv(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    @DrawableRes iconRes: Int,
    @StringRes contentDescriptionRes: Int,
    onShortClick: () -> Unit = {}
) {
    Button(
        onClick = onShortClick,
        // tv-material Button already handles both touch taps and DPAD-center activation via
        // onClick; no extra pointerInput gesture layer on top of it.
        modifier = modifier
            .wrapContentSize(align = Alignment.Center)
            .focusRequester(focusRequester),
        enabled = true,
        scale = ButtonDefaults.scale(),
        shape = ButtonDefaults.shape(shape = CircleShape),
        colors = ButtonDefaults.colors(
            containerColor = OnWallpaperContainer,
            contentColor = ButtonContentDefault,
            focusedContainerColor = ButtonContainerFocused,
            focusedContentColor = ButtonContentFocused,
            pressedContainerColor = ButtonContainerFocused,
            pressedContentColor = ButtonContentFocused
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(contentDescriptionRes),
            modifier = Modifier
                .size(size = 20.dp)
        )
    }
}