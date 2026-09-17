package com.github.honqout.tvlauncher3.ui.launcher.activity

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.components.button.IconButtonTv
import com.github.honqout.tvlauncher3.components.dialog.SettingsDialog
import com.github.honqout.tvlauncher3.constants.NumberConstants
import com.github.honqout.tvlauncher3.ui.launcher.screen.AppsScreen
import com.github.honqout.tvlauncher3.ui.launcher.screen.FilesScreen
import com.github.honqout.tvlauncher3.ui.launcher.screen.HomeScreen
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.FilesViewModel
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.LauncherViewModel
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_MEDIUM
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainer
import com.github.honqout.tvlauncher3.ui.theme.TVLauncher3Theme
import com.github.honqout.tvlauncher3.ui.theme.TabContainerColorActive
import com.github.honqout.tvlauncher3.ui.theme.TabContainerColorInactive
import com.github.honqout.tvlauncher3.ui.theme.TabContentColorActive
import com.github.honqout.tvlauncher3.ui.theme.TabContentColorHovered
import com.github.honqout.tvlauncher3.ui.theme.TabContentColorInactive
import com.github.honqout.tvlauncher3.utils.DisplayUtils
import com.github.honqout.tvlauncher3.utils.UIUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "TVLauncher3"
    }

    /**
     * A decoded system wallpaper. [id] is the id reported by the wallpaper service so repeated
     * loads can be skipped, and [bitmap] is null when the device has no readable wallpaper (the
     * built-in one is then used instead).
     */
    private data class WallpaperSnapshot(val id: Int, val bitmap: ImageBitmap?)

    /**
     * Decode the system wallpaper into a full screen bitmap off the main thread.
     *
     * @return the new snapshot, or null when the wallpaper is still [previousId], in which case the
     * caller keeps the bitmap it already has. Rasterising the wallpaper again on every ON_RESUME
     * would allocate a full screen bitmap for nothing.
     */
    private suspend fun loadWallpaperSnapshot(previousId: Int?): WallpaperSnapshot? =
        withContext(Dispatchers.IO) {
            runCatching {
                val wm = WallpaperManager.getInstance(applicationContext)
                val wallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                if (wallpaperId == previousId) {
                    return@runCatching null
                }
                WallpaperSnapshot(wallpaperId, decodeWallpaper(wm)?.asImageBitmap())
            }.onFailure {
                Log.w(TAG, "Failed to load the system wallpaper.", it)
            }.getOrNull()
        }

    /**
     * 将系统壁纸绘制为位图;无壁纸或读取失败时返回 null,由内置默认壁纸兜底
     */
    private fun decodeWallpaper(wm: WallpaperManager): Bitmap? {
        var bitmap: Bitmap? = null
        try {
            val drawable = wm.drawable
            if (drawable != null) {
                val metrics = applicationContext.resources.displayMetrics
                bitmap = createBitmap(metrics.widthPixels, metrics.heightPixels)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(canvas)
                Log.i(TAG, "wallpaper loaded from drawable")
            } else {
                Log.w(TAG, "wm.drawable is null")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "load from drawable failed", t)
        }
        if (bitmap == null) {
            // 兜底:直接读取系统壁纸文件
            try {
                val fd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                } else {
                    null
                }
                if (fd != null) {
                    fd.use { pfd ->
                        bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                    }
                    Log.i(TAG, "wallpaper loaded from file ${bitmap?.width}x${bitmap?.height}")
                } else {
                    Log.w(TAG, "wallpaper file is null")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "load from file failed", t)
            }
        }
        return bitmap
    }

    private val launcherViewModel: LauncherViewModel by viewModels()
    private val filesViewModel: FilesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide status bar and navigation bar
        UIUtils.handleSystemBarsVisibility(window, false)

        setContent {
            DisposableEffect(Unit) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT
                    ) {
                        true
                    },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT
                    ) {
                        true
                    }
                )
                onDispose {}
            }

            // The home screen swallows Back so it can never leave the launcher.
            BackHandler {
            }

            TVLauncher3Theme {
                val configuration = LocalConfiguration.current

                val tabs = launcherViewModel.tabs
                val showSettingsDialog by launcherViewModel.showSettingsDialog
                    .collectAsStateWithLifecycle()
                val selectedTabIndex by launcherViewModel.selectedTabIndex
                    .collectAsStateWithLifecycle()
                val focusRequester = remember { FocusRequester() }

                var wallpaperSnapshot by remember { mutableStateOf<WallpaperSnapshot?>(null) }
                val scope = rememberCoroutineScope()
                val loadWallpaper: () -> Unit = {
                    scope.launch {
                        // A null result means the system wallpaper did not change, in which case the
                        // already decoded bitmap is kept instead of being rebuilt.
                        val snapshot = loadWallpaperSnapshot(wallpaperSnapshot?.id)
                        if (snapshot != null) {
                            wallpaperSnapshot = snapshot
                            Log.i(TAG, "system wallpaper available: ${snapshot.bitmap != null}")
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    loadWallpaper()
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    // 每次回到桌面时检查系统壁纸,系统设置中换壁纸后立即生效(壁纸未变则复用已解码的位图)
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            loadWallpaper()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }


                LaunchedEffect(Unit) {
                    delay(100.milliseconds)
                    // Handle config changes
                    launcherViewModel.onConfigChanged(configuration)
                    // Request focus
                    focusRequester.requestFocus()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .background(Color.Transparent)
                ) {
                    // 优先展示系统壁纸;获取不到时使用内置默认壁纸
                    val systemWallpaper = wallpaperSnapshot?.bitmap
                    if (systemWallpaper != null) {
                        Image(
                            bitmap = systemWallpaper,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.wallpaper_bg),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth()
                            .padding(20.dp)
                            .onSizeChanged { intSize ->
                                val heightDp = DisplayUtils.pixelToDp(baseContext, intSize.height)
                                launcherViewModel.setTopBarHeight(heightDp)
                                filesViewModel.setTopBarHeight(heightDp)
                            },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(OnWallpaperContainer)
                                .focusRestorer()
                                .focusRequester(focusRequester),
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            indicator = { tabPositions, doesTabRowHaveFocus ->
                                TabRowDefaults.PillIndicator(
                                    currentTabPosition = tabPositions[selectedTabIndex],
                                    doesTabRowHaveFocus = doesTabRowHaveFocus,
                                    modifier = Modifier,
                                    activeColor = TabContainerColorActive,
                                    inactiveColor = TabContainerColorInactive
                                )
                            }
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val focusState = interactionSource.collectIsFocusedAsState()
                                val hoverState = interactionSource.collectIsHoveredAsState()
                                val bgColor by animateColorAsState(
                                    targetValue = if (focusState.value || hoverState.value)
                                        TabContainerColorActive
                                    else Color.Transparent,
                                    animationSpec = tween(durationMillis = NumberConstants.ANIM_DURATION_MS)
                                )
                                val contentColor by animateColorAsState(
                                    targetValue = if (selectedTabIndex == index)
                                        TabContentColorActive
                                    else if (focusState.value || hoverState.value)
                                        TabContentColorHovered
                                    else TabContentColorInactive
                                )

                                key(index) {
                                    Tab(
                                        selected = selectedTabIndex == index,
                                        onFocus = {},
                                        modifier = Modifier
                                            .background(color = bgColor, shape = CircleShape)
                                            .combinedClickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                enabled = true,
                                                role = Role.Tab,
                                                onClick = {
                                                    launcherViewModel.setSelectedTabIndex(index)
                                                }
                                            ),
                                        onClick = {
                                            launcherViewModel.setSelectedTabIndex(index)
                                        },
                                        colors = TabDefaults.pillIndicatorTabColors(
                                            contentColor = Color.White.copy(alpha = 0.7f),
                                            inactiveContentColor = Color.White.copy(alpha = 0.5f),
                                            selectedContentColor = TabContentColorActive,
                                            focusedContentColor = TabContentColorHovered,
                                            focusedSelectedContentColor = TabContentColorActive,
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                painterResource(tab.first),
                                                contentDescription = stringResource(tab.second),
                                                tint = contentColor
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Text(
                                                text = stringResource(tab.second),
                                                color = contentColor,
                                                fontSize = FONT_SIZE_MEDIUM
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButtonTv(
                            iconRes = R.drawable.baseline_settings_24,
                            contentDescriptionRes = R.string.settings,
                            onShortClick = {
                                launcherViewModel.setShowSettingsScreen(true)
                            }
                        )

                        Spacer(modifier = Modifier.width(20.dp))

                        AndroidView(
                            factory = { context ->
                                TextClock(context).apply {
                                    format12Hour = "hh:mm:ss"
                                    format24Hour = "HH:mm:ss"
                                    textSize = 20F
                                    setEnabled(false)
                                    setTextColor(android.graphics.Color.WHITE)
                                }
                            },
                            modifier = Modifier
                                .background(
                                    color = OnWallpaperContainer,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(10.dp)
                                .focusable(enabled = false)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color = Color.Transparent)
                    ) {
                        // Clamp instead of writing back to the state holder, which would mutate
                        // state during composition.
                        when (selectedTabIndex.coerceIn(0, tabs.lastIndex)) {
                            0 -> HomeScreen(viewModel = launcherViewModel)
                            1 -> AppsScreen(viewModel = launcherViewModel)
                            else -> FilesScreen(viewModel = filesViewModel)
                        }
                    }

                    AnimatedVisibility(
                        visible = showSettingsDialog,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        SettingsDialog(
                            launcherViewModel = launcherViewModel,
                            onDismissRequest = {
                                launcherViewModel.setShowSettingsScreen(false)
                            }
                        )
                    }
                }
            }
        }
    }
}