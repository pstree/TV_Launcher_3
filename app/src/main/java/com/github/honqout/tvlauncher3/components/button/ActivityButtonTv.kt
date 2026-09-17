package com.github.honqout.tvlauncher3.components.button

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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
import com.github.honqout.tvlauncher3.utils.ApplicationUtils.Companion.IconType
import com.github.honqout.tvlauncher3.utils.DrawableUtils
import com.github.honqout.tvlauncher3.utils.IconCache
import com.github.honqout.tvlauncher3.utils.IconCache.IconEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Guard against caching oversized banner bitmaps: 200 entries of huge banners would otherwise be
// wasteful, and the button only ever renders the icon at a small tile size anyway.
private const val MAX_ICON_CACHE_DIMENSION = 512

private fun Drawable.toIconBitmap(): ImageBitmap {
    val w = intrinsicWidth.let { if (it > 0) it else 0 }
    val h = intrinsicHeight.let { if (it > 0) it else 0 }
    return if (w > 0 && h > 0) {
        val scale = if (maxOf(w, h) > MAX_ICON_CACHE_DIMENSION) {
            MAX_ICON_CACHE_DIMENSION.toFloat() / maxOf(w, h)
        } else {
            1f
        }
        toBitmap(
            width = (w * scale).toInt().coerceAtLeast(1),
            height = (h * scale).toInt().coerceAtLeast(1)
        ).asImageBitmap()
    } else {
        toBitmap().asImageBitmap()
    }
}

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

    val key = activityModel?.getKey()

    // Whether a fully-resolved entry was already cached when this composition started. When it is,
    // the cached bitmap + background color are shown on the very first frame, so re-entering the
    // "Apps" tab doesn't flash a placeholder/transparent state while the icon is re-decoded.
    val cachedOnEntry = remember(key) { key != null && IconCache.get(key) != null }

    // The shortcut background is the app icon's dominant colour and the icon bitmap itself.
    // Extracting them costs a PackageManager call plus a Palette pass (and a decode), so both are
    // derived here, for the items actually on screen, and cached across screen rebuilds.
    val iconEntry by produceState<IconEntry?>(
        initialValue = key?.let { IconCache.get(it) },
        key1 = key
    ) {
        val model = activityModel ?: return@produceState
        val modelKey = model.getKey()
        IconCache.get(modelKey)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val (_, icon) = ApplicationUtils.getActivityIconPair(
                context,
                model.packageName,
                model.activityName
            )
            val entry = IconEntry(
                bitmap = icon.toIconBitmap(),
                backgroundColor = Color(DrawableUtils.getBackgroundColorFromAppIcon(icon))
            )
            IconCache.put(modelKey, entry)
            entry
        }
    }

    val backgroundColor = when {
        activityModel == null -> ButtonContainerDefault
        iconEntry?.backgroundColor != null -> iconEntry!!.backgroundColor
        else -> Color.Transparent
    }

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
                val cachedBitmap = if (cachedOnEntry) iconEntry?.bitmap else null
                if (cachedBitmap != null) {
                    Image(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isBanner) 0.dp else 10.dp),
                        bitmap = cachedBitmap,
                        contentDescription = activityModel.label,
                        contentScale = if (isBanner) ContentScale.FillBounds else ContentScale.Fit,
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
            }
        },
        label = activityModel?.label ?: stringResource(R.string.add_app),
        backgroundColor = backgroundColor,
        contentDefaultColor = contentDefaultColor,
        contentFocusedColor = contentFocusedColor,
        onShortClick = onShortClick,
        onLongClick = onLongClick
    )
}
