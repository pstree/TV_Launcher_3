package com.github.honqout.tvlauncher3.ui.launcher.screen

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.FilesViewModel
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_LARGE
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainer
import com.github.honqout.tvlauncher3.ui.theme.PADDING_LIST_CONTENT_EDGE
import com.github.honqout.tvlauncher3.ui.theme.PADDING_SCREEN_EDGE
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_HORIZONTAL
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_VERTICAL

@Composable
fun FilesScreen(
    viewModel: FilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val lazyGridState = rememberLazyGridState()
    val topBarHeight by viewModel.topBarHeight.collectAsState()
    val currentDir by viewModel.currentDir.collectAsState()
    val items by viewModel.items.collectAsState()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsState()

    LaunchedEffect(Unit) {
        if (!viewModel.hasAllFilesAccess()) {
            viewModel.setShowPermissionDialog(true)
        }
        viewModel.refresh()
    }

    BackHandler {
        if (currentDir != null) {
            viewModel.goUp()
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

            Text(
                text = currentDir?.absolutePath ?: stringResource(R.string.files),
                color = Color.White,
                fontSize = FONT_SIZE_LARGE,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .background(
                        color = OnWallpaperContainer,
                        shape = RoundedCornerShape(16.dp)
                    ),
                state = lazyGridState,
                contentPadding = PaddingValues(PADDING_LIST_CONTENT_EDGE),
                verticalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_VERTICAL),
                horizontalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_HORIZONTAL),
                userScrollEnabled = true
            ) {
                itemsIndexed(items) { _, item ->
                    FileItemButton(
                        item = item,
                        onShortClick = {
                            viewModel.onItemClick(item)
                        }
                    )
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.setShowPermissionDialog(false)
            },
            title = {
                Text(text = stringResource(R.string.files_permission_title))
            },
            text = {
                Text(text = stringResource(R.string.files_permission_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setShowPermissionDialog(false)
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.grant))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setShowPermissionDialog(false)
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun FileItemButton(
    item: FilesViewModel.FileItem,
    onShortClick: () -> Unit
) {
    val iconRes = if (item.isDirectory) {
        R.drawable.baseline_folder_24
    } else {
        R.drawable.baseline_insert_drive_file_24
    }

    Button(
        onClick = onShortClick,
        modifier = Modifier
            .fillMaxWidth(),
        enabled = true,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.DarkGray.copy(alpha = 0.5f),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(),
        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 16.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = item.name,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = item.name,
                color = Color.White,
                fontSize = FONT_SIZE_LARGE,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }
}
