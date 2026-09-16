package com.github.honqout.tvlauncher3.components.button

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.size.Precision
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.coil.model.ActivityIconModel
import com.github.honqout.tvlauncher3.data.ActivityModel
import com.github.honqout.tvlauncher3.ui.theme.ButtonContainerDefault
import com.github.honqout.tvlauncher3.utils.ApplicationUtils.Companion.IconType

/**
 * A square shortcut for a launchable activity. When [activityModel] is null the button renders
 * [defaultIcon] and acts as an "add" affordance.
 */
@Composable
fun ActivityButtonTv(
    modifier: Modifier = Modifier,
    activityModel: ActivityModel?,
    defaultIcon: Drawable,
    contentDefaultColor: Color = colorScheme.secondary,
    contentFocusedColor: Color = colorScheme.primary,
    onShortClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isBanner = activityModel?.iconType == IconType.Banner

    val imageRequest = remember(activityModel?.getKey(), defaultIcon) {
        if (activityModel == null) {
            ImageRequest.Builder(context)
                .data(defaultIcon)
                .build()
        } else {
            ImageRequest.Builder(context)
                .data(
                    ActivityIconModel(
                        activityModel.packageName,
                        activityModel.activityName
                    )
                )
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .crossfade(false)
                .placeholder(defaultIcon)
                .error(defaultIcon)
                .build()
        }
    }

    RoundRectButtonTv(
        modifier = modifier,
        icon = {
            if (activityModel == null) {
                IconFromDrawableTv(
                    drawable = defaultIcon,
                    contentDescription = null
                )
            } else {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isBanner) 0.dp else 10.dp),
                    model = imageRequest,
                    contentDescription = activityModel.label,
                    contentScale = if (isBanner) ContentScale.FillBounds else ContentScale.Fit,
                )
            }
        },
        label = activityModel?.label ?: stringResource(R.string.add_app),
        backgroundColor = if (activityModel == null) ButtonContainerDefault
        else Color(activityModel.color),
        contentDefaultColor = contentDefaultColor,
        contentFocusedColor = contentFocusedColor,
        onShortClick = onShortClick,
        onLongClick = onLongClick
    )
}
