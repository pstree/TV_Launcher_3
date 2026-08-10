package com.github.honqout.tvlauncher3.components.button

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
import com.github.honqout.tvlauncher3.utils.ApplicationUtils

@Composable
fun ActivityButtonTv(
    modifier: Modifier = Modifier,
    activityModel: ActivityModel?,
    contentDefaultColor: Color = colorScheme.secondary,
    contentFocusedColor: Color = colorScheme.primary,
    onShortClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current

    val imageRequest = remember(activityModel?.getKey()) {
        if (activityModel == null) {
            ImageRequest.Builder(context)
                .data(R.drawable.baseline_add_24)
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
                .placeholder(R.drawable.baseline_add_24)
                .error(R.drawable.baseline_add_24)
                .build()
        }
    }

    RoundRectButtonTv(
        modifier = modifier,
        icon = {
            if (activityModel == null) {
                IconFromResourceTv(
                    drawableRes = R.drawable.baseline_add_24,
                    contentDescription = null
                )
            } else {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            if (activityModel.iconType == ApplicationUtils.Companion.IconType.Banner) 0.dp
                            else 10.dp
                        ),
                    model = imageRequest,
                    contentDescription = activityModel.label,
                    contentScale = if (activityModel.iconType == ApplicationUtils.Companion.IconType.Banner)
                        ContentScale.FillBounds else ContentScale.Fit,
                )
            }
        },
        label = activityModel?.label ?: stringResource(R.string.add_app),
        backgroundColor = if (activityModel == null) ButtonContainerDefault else Color(activityModel.color),
        contentDefaultColor = contentDefaultColor,
        contentFocusedColor = contentFocusedColor,
        onShortClick = onShortClick,
        onLongClick = onLongClick
    )
}