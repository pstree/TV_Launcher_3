package com.github.honqout.tvlauncher3.ui.launcher.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.components.HomeClock
import com.github.honqout.tvlauncher3.components.button.IconButtonTv
import com.github.honqout.tvlauncher3.components.dialog.SettingsDialog
import com.github.honqout.tvlauncher3.ui.launcher.screen.FilesScreen
import com.github.honqout.tvlauncher3.ui.launcher.screen.HomeScreen
import com.github.honqout.tvlauncher3.ui.launcher.screen.WallpaperScreen
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.FilesViewModel
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.LauncherViewModel
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.LauncherViewModel.Companion.Overlay
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.WallpaperViewModel
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.WeatherViewModel
import com.github.honqout.tvlauncher3.ui.theme.TVLauncher3Theme
import com.github.honqout.tvlauncher3.utils.DisplayUtils
import com.github.honqout.tvlauncher3.utils.UIUtils
import com.github.honqout.tvlauncher3.utils.WallpaperUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** 顶部栏右侧的按钮:壁纸、文件、设置。下标用于关闭浮层后把焦点交回原按钮。 */
private const val TOP_WALLPAPER = 0
private const val TOP_FILES = 1
private const val TOP_SETTINGS = 2
private const val TOP_BUTTON_COUNT = 3

/** 文件/壁纸浮层下的压暗层,盖住壁纸,保证整屏都是这一页。 */
private val OVERLAY_SCRIM = Color.Black.copy(alpha = 0.78f)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "TVLauncher3"
    }

    private val launcherViewModel: LauncherViewModel by viewModels()
    private val filesViewModel: FilesViewModel by viewModels()
    private val wallpaperViewModel: WallpaperViewModel by viewModels()
    private val weatherViewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide status bar and navigation bar
        UIUtils.handleSystemBarsVisibility(window, false)
        // 布局后再执行一次, 避免被 setContent 里的 enableEdgeToEdge 覆盖
        window.decorView.post {
            UIUtils.handleSystemBarsVisibility(window, false)
        }

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

            TVLauncher3Theme {
                val configuration = LocalConfiguration.current

                val overlay by launcherViewModel.overlay.collectAsStateWithLifecycle()
                val showSettingsDialog by launcherViewModel.showSettingsDialog
                    .collectAsStateWithLifecycle()
                // 左上角时钟:天气数据 + 面板展开时淡出的透明度。
                val weather by weatherViewModel.weather.collectAsStateWithLifecycle()
                val drawerOpen by launcherViewModel.drawerOpen.collectAsStateWithLifecycle()
                val clockAlpha by animateFloatAsState(
                    targetValue = if (drawerOpen) 0f else 1f,
                    animationSpec = tween(durationMillis = 160),
                    label = "home-clock-alpha"
                )

                val topBarRequesters = remember {
                    List(TOP_BUTTON_COUNT) { FocusRequester() }
                }
                // 打开文件/壁纸/设置的那个按钮;关闭后焦点要回到它身上,否则会落到首页的快捷区。
                var openerIndex by remember { mutableIntStateOf(-1) }
                var topBarHeight by remember { mutableIntStateOf(0) }

                // 用户选过壁纸就用它当背景,没选过(或解码失败)回退到内置壁纸
                val backgroundVersion by wallpaperViewModel.backgroundVersion
                    .collectAsStateWithLifecycle()
                var backgroundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(backgroundVersion) {
                    val metrics = resources.displayMetrics
                    backgroundBitmap = WallpaperUtils.loadLauncherBackground(
                        this@MainActivity,
                        metrics.widthPixels,
                        metrics.heightPixels
                    )?.asImageBitmap()
                }

                // 浮层都关掉之后,焦点回到打开它的那个顶部按钮。
                LaunchedEffect(overlay, showSettingsDialog) {
                    if (overlay != Overlay.NONE || showSettingsDialog) {
                        return@LaunchedEffect
                    }
                    val index = openerIndex
                    if (index < 0) {
                        return@LaunchedEffect
                    }
                    openerIndex = -1
                    withFrameNanos { }
                    runCatching { topBarRequesters[index].requestFocus() }
                }

                LaunchedEffect(Unit) {
                    delay(100.milliseconds)
                    // Handle config changes
                    launcherViewModel.onConfigChanged(configuration)
                    // Launch the user-selected auto-start app once per boot.
                    launcherViewModel.launchAutoStartAppIfNeeded()
                }

                // 天气一小时更新一次就够了,这里半小时敲一次门,命中缓存时它就是空转。
                LaunchedEffect(Unit) {
                    while (true) {
                        weatherViewModel.refresh()
                        delay(30.minutes)
                    }
                }

                // The home screen swallows Back so it can never leave the launcher; an open overlay
                // closes first (the Files browser registers its own handler for its subdirectories).
                BackHandler {
                    if (overlay != Overlay.NONE) {
                        launcherViewModel.setOverlay(Overlay.NONE)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .background(Color.Transparent)
                ) {
                    val background = backgroundBitmap
                    if (background != null) {
                        Image(
                            bitmap = background,
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
                                topBarHeight = heightDp
                                launcherViewModel.setTopBarHeight(heightDp)
                            },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButtonTv(
                            focusRequester = topBarRequesters[TOP_WALLPAPER],
                            iconRes = R.drawable.baseline_wallpaper_24,
                            contentDescriptionRes = R.string.wallpaper,
                            onShortClick = {
                                openerIndex = TOP_WALLPAPER
                                launcherViewModel.setOverlay(Overlay.WALLPAPER)
                            }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButtonTv(
                            focusRequester = topBarRequesters[TOP_FILES],
                            iconRes = R.drawable.baseline_folder_24,
                            contentDescriptionRes = R.string.files,
                            onShortClick = {
                                openerIndex = TOP_FILES
                                launcherViewModel.setOverlay(Overlay.FILES)
                            }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButtonTv(
                            focusRequester = topBarRequesters[TOP_SETTINGS],
                            iconRes = R.drawable.baseline_settings_24,
                            contentDescriptionRes = R.string.settings,
                            onShortClick = {
                                openerIndex = TOP_SETTINGS
                                launcherViewModel.setShowSettingsScreen(true)
                            }
                        )
                    }

                    // 左上角:大号日期 + 时间 + 天气。它不参与顶部栏高度(否则会把"全部应用"面板挤下去),
                    // 所以面板展开时跟着淡出,免得被面板压掉一半。
                    HomeClock(
                        weather = weather,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer { alpha = clockAlpha }
                            .padding(start = 24.dp, top = 14.dp)
                    )

                    HomeScreen(viewModel = launcherViewModel)

                    // 文件与壁纸是整屏浮层:盖住首页和顶部栏,返回键回到桌面。
                    AnimatedVisibility(
                        visible = overlay == Overlay.FILES,
                        modifier = Modifier.fillMaxSize(),
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(OVERLAY_SCRIM)
                        ) {
                            FilesScreen(
                                viewModel = filesViewModel,
                                onBackAtTopLevel = {
                                    launcherViewModel.setOverlay(Overlay.NONE)
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = overlay == Overlay.WALLPAPER,
                        modifier = Modifier.fillMaxSize(),
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(OVERLAY_SCRIM)
                        ) {
                            WallpaperScreen(viewModel = wallpaperViewModel)
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
