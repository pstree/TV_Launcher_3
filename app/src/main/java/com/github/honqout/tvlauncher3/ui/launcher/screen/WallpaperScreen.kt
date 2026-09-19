package com.github.honqout.tvlauncher3.ui.launcher.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.data.WallpaperItem
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.WallpaperViewModel
import com.github.honqout.tvlauncher3.ui.theme.ButtonContainerDefault
import com.github.honqout.tvlauncher3.ui.theme.ButtonContainerFocused
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_LARGE
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_MEDIUM
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_SMALL
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainerDark
import com.github.honqout.tvlauncher3.ui.theme.PADDING_LIST_CONTENT_EDGE
import com.github.honqout.tvlauncher3.ui.theme.PADDING_SCREEN_EDGE
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_HORIZONTAL
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_VERTICAL
import com.github.honqout.tvlauncher3.utils.WallpaperUtils

/** 网格固定 4 列,配 12 张就是一屏 4x3。 */
private const val COLUMNS = 4

@Composable
fun WallpaperScreen(
    viewModel: WallpaperViewModel = hiltViewModel(),
    topBarHeight: Int = 0
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()

    var pendingApply by remember { mutableStateOf<WallpaperItem?>(null) }

    val doApply: (WallpaperItem) -> Unit = { item ->
        viewModel.applyWallpaper(item) { result ->
            val messageRes = when (result) {
                WallpaperViewModel.ApplyResult.SUCCESS -> R.string.wallpaper_applied
                WallpaperViewModel.ApplyResult.SAVE_FAILED -> R.string.wallpaper_save_failed
            }
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
        }
    }

    // Android 10 以下写公共图片目录要写权限,先申请再落地挂起的那张
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val item = pendingApply
        pendingApply = null
        when {
            item == null -> Unit
            granted.values.all { it } -> doApply(item)
            else -> Toast.makeText(
                context,
                R.string.wallpaper_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val onTileClick: (WallpaperItem) -> Unit = { item ->
        val missingPermission = WallpaperUtils.needsStoragePermission() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (missingPermission) {
            pendingApply = item
            permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        } else {
            doApply(item)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PADDING_SCREEN_EDGE)
        ) {
            Spacer(modifier = Modifier.height((topBarHeight + 10).dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.wallpaper),
                    color = Color.White,
                    fontSize = FONT_SIZE_LARGE,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = stringResource(
                        if (applying) R.string.wallpaper_applying else R.string.wallpaper_hint
                    ),
                    color = Color.LightGray,
                    fontSize = FONT_SIZE_SMALL,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(
                        color = OnWallpaperContainerDark,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    modifier = Modifier.fillMaxSize(),
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(PADDING_LIST_CONTENT_EDGE),
                    verticalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_VERTICAL),
                    horizontalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_HORIZONTAL)
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> item.imageUrl }
                    ) { _, item ->
                        WallpaperTile(
                            modifier = Modifier.aspectRatio(16F / 9F),
                            item = item,
                            onClick = { onTileClick(item) }
                        )
                    }
                }

                if (loading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.wallpaper_loading),
                            color = Color.White,
                            fontSize = FONT_SIZE_MEDIUM,
                            maxLines = 1
                        )
                    }
                }

                if (loadFailed && !loading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.wallpaper_load_failed),
                            color = Color.White,
                            fontSize = FONT_SIZE_MEDIUM,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.load() }) {
                            Text(text = stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一张壁纸。tv-material 的可点击 Surface 只认 DPAD/回车,鼠标点击得自己接
 * (与 RoundRectButtonTv 的处理一致)。
 */
@Composable
private fun WallpaperTile(
    modifier: Modifier = Modifier,
    item: WallpaperItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { onClick() })
        },
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ButtonContainerDefault,
            contentColor = Color.White,
            focusedContainerColor = ButtonContainerFocused,
            focusedContentColor = Color.White,
            pressedContainerColor = ButtonContainerFocused,
            pressedContentColor = Color.White
        )
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = item.thumbnailUrl,
            contentDescription = item.description,
            contentScale = ContentScale.Crop
        )
    }
}
