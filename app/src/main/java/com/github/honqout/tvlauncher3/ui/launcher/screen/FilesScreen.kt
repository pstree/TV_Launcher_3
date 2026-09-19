package com.github.honqout.tvlauncher3.ui.launcher.screen

import android.Manifest
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.components.dialog.FileActionDialog
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.FilesViewModel
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_LARGE
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainerDark
import com.github.honqout.tvlauncher3.ui.theme.PADDING_LIST_CONTENT_EDGE
import com.github.honqout.tvlauncher3.ui.theme.PADDING_SCREEN_EDGE
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_HORIZONTAL
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_VERTICAL
import com.github.honqout.tvlauncher3.utils.IntentUtils

/** 目录内容是异步加载的,落焦点最多等这么多帧。 */
private const val FOCUS_WAIT_FRAMES = 30

@Composable
fun FilesScreen(
    viewModel: FilesViewModel = hiltViewModel(),
    onBackAtTopLevel: () -> Unit = {}
) {
    val context = LocalContext.current
    val itemFocusRequester = remember { FocusRequester() }
    val topBarHeight by viewModel.topBarHeight.collectAsStateWithLifecycle()
    val currentDir by viewModel.currentDir.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()

    // 每个目录各记一份滚动位置,返回/再次进入时原地恢复:列表不用先滚再落焦点,也就不会"动一下"
    val savedScrollPositions = remember { mutableMapOf<String, Pair<Int, Int>>() }
    val gridKey = currentDir?.absolutePath.orEmpty()
    val lazyGridState = remember(gridKey) {
        val saved = savedScrollPositions[gridKey]
        if (saved == null) {
            LazyGridState()
        } else {
            LazyGridState(
                firstVisibleItemIndex = saved.first,
                firstVisibleItemScrollOffset = saved.second
            )
        }
    }
    val saveScrollPosition = {
        savedScrollPositions[gridKey] =
            lazyGridState.firstVisibleItemIndex to lazyGridState.firstVisibleItemScrollOffset
    }

    // 待删除的文件/文件夹(按设置键弹出确认框);deleteFailed 用于提示删除失败
    var pendingDelete by remember { mutableStateOf<FilesViewModel.FileItem?>(null) }
    var deleteFailed by remember { mutableStateOf(false) }

    // itemFocusRequester 挂在第 focusIndex 项上;pendingFocusPath 是返回上级后要落焦点的那个目录
    var focusIndex by remember { mutableStateOf(0) }
    var pendingFocusPath by remember { mutableStateOf<String?>(null) }

    // 菜单键操作面板:actionTarget 为当前焦点项,为 null 表示只提供粘贴(空目录/卷列表)
    var focusedItem by remember { mutableStateOf<FilesViewModel.FileItem?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<FilesViewModel.FileItem?>(null) }

    // 卷根目录既不能删也不能复制,粘贴必须有确定的当前目录
    val canPaste = clipboard != null && currentDir != null

    // Android 6~12:点击授权后申请存储权限(低版本还需要写权限才能删除文件)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        if (!viewModel.hasAllFilesAccess()) {
            viewModel.setShowPermissionDialog(true)
        }
        viewModel.refresh()
    }

    // 换目录后的落焦点规则:
    //  · 进入目录 -> 焦点在第一项
    //  · 按返回 -> 焦点回到刚退出的那个目录项(含从卷返回顶层时的"内置存储"这一项)
    //  · 首次进入文件页的顶层(卷列表) -> 不抢焦点,焦点留在 tab 上
    LaunchedEffect(currentDir) {
        focusedItem = null
        val targetPath = pendingFocusPath
        pendingFocusPath = null
        if (currentDir == null && targetPath == null) {
            return@LaunchedEffect
        }
        val dirPath = currentDir?.absolutePath
        repeat(FOCUS_WAIT_FRAMES) {
            withFrameNanos { }
            // 等的是"当前目录的内容",items 里可能还留着上一层的列表
            val contentReady = items.isNotEmpty() &&
                if (dirPath == null) items.first().isVolume
                else items.first().path.startsWith("$dirPath/")
            if (contentReady) {
                val targetIndex = items.indexOfFirst { it.path == targetPath }
                    .takeIf { it >= 0 } ?: 0
                // 恢复出来的位置看不到目标项时才滚(懒加载列表不会组合屏幕外的项,requester 挂不上)
                if (lazyGridState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
                    lazyGridState.scrollToItem(targetIndex)
                }
                focusIndex = targetIndex
                withFrameNanos { }
                withFrameNanos { }
                runCatching { itemFocusRequester.requestFocus() }
                return@LaunchedEffect
            }
        }
    }

    BackHandler {
        if (currentDir != null) {
            // 返回上级:焦点回到刚退出的这个目录
            saveScrollPosition()
            pendingFocusPath = currentDir?.absolutePath
            viewModel.goUp()
        } else {
            // 已经在顶层(卷列表):把焦点交回上面的"文件"tab
            onBackAtTopLevel()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Transparent)
            // 遥控器菜单/设置键:对当前焦点项弹出 复制/粘贴/删除 面板。
            // 放在列表外层是因为空目录里没有条目可按,此时只能用这个入口粘贴。
            .onPreviewKeyEvent { event ->
                val action = event.nativeKeyEvent.action
                val keyCode = event.nativeKeyEvent.keyCode
                val isMenuKey = action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS)
                if (!isMenuKey) {
                    return@onPreviewKeyEvent false
                }
                val target = focusedItem?.takeUnless { it.isVolume }
                if (target == null && !canPaste) {
                    false
                } else {
                    actionTarget = target
                    showActionMenu = true
                    true
                }
            }
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
                        color = OnWallpaperContainerDark,
                        shape = RoundedCornerShape(16.dp)
                    ),
                state = lazyGridState,
                contentPadding = PaddingValues(PADDING_LIST_CONTENT_EDGE),
                verticalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_VERTICAL),
                horizontalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_HORIZONTAL),
                userScrollEnabled = true
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.path }
                ) { index, item ->
                    FileItemButton(
                        // 只有需要落焦点的那一项挂 FocusRequester
                        modifier = if (index == focusIndex) {
                            Modifier.focusRequester(itemFocusRequester)
                        } else {
                            Modifier
                        },
                        item = item,
                        onShortClick = {
                            // 进目录前先记下当前位置,返回时原地恢复
                            if (item.isDirectory) {
                                saveScrollPosition()
                            }
                            viewModel.onItemClick(item)
                        },
                        onFocused = {
                            focusedItem = item
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            IntentUtils.launchAction(
                                context,
                                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
                                true
                            )
                        } else {
                            permissionLauncher.launch(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                } else {
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                }
                            )
                        }
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
    if (showActionMenu) {
        val target = actionTarget
        FileActionDialog(
            itemName = target?.name,
            canPaste = canPaste,
            onCopy = {
                if (target != null) {
                    viewModel.copyItem(target)
                    Toast.makeText(
                        context,
                        context.getString(R.string.copy_done, target.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showActionMenu = false
            },
            onPaste = {
                val pastedName = clipboard?.name.orEmpty()
                showActionMenu = false
                viewModel.pasteItem { ok ->
                    Toast.makeText(
                        context,
                        context.getString(
                            if (ok) R.string.paste_done else R.string.paste_failed,
                            pastedName
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDelete = {
                showActionMenu = false
                if (target != null) {
                    deleteFailed = false
                    pendingDelete = target
                }
            },
            onDismissRequest = {
                showActionMenu = false
            }
        )
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = {
                pendingDelete = null
            },
            title = {
                Text(text = stringResource(R.string.delete_confirm_title))
            },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            R.string.delete_confirm_message,
                            pendingDelete?.name ?: ""
                        )
                    )
                    if (deleteFailed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.delete_failed),
                            color = Color(0xFFFF5252)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pendingDelete
                        pendingDelete = null
                        if (target != null) {
                            viewModel.deleteItem(target) { ok ->
                                if (!ok) {
                                    deleteFailed = true
                                    pendingDelete = target
                                }
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
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
    modifier: Modifier = Modifier,
    item: FilesViewModel.FileItem,
    onShortClick: () -> Unit,
    onFocused: () -> Unit
) {
    val iconRes = if (item.isDirectory) {
        R.drawable.baseline_folder_24
    } else {
        R.drawable.baseline_insert_drive_file_24
    }

    Button(
        onClick = onShortClick,
        modifier = modifier
            .fillMaxWidth()
            // 记录焦点位置,菜单键的操作对象就是它
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            },
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
