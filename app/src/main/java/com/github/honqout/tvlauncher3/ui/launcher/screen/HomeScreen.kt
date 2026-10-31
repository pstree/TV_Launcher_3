package com.github.honqout.tvlauncher3.ui.launcher.screen

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.components.button.ActivityButtonTv
import com.github.honqout.tvlauncher3.components.button.AppShortcutButtonTv
import com.github.honqout.tvlauncher3.components.dialog.AppActionDialog
import com.github.honqout.tvlauncher3.components.dialog.AppListDialog
import com.github.honqout.tvlauncher3.components.grid.followGridFocus
import com.github.honqout.tvlauncher3.data.ActivityModel
import com.github.honqout.tvlauncher3.datastore.repository.IconRepository
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.LauncherViewModel
import com.github.honqout.tvlauncher3.ui.theme.ButtonContentDefault
import com.github.honqout.tvlauncher3.ui.theme.ButtonContentFocused
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_LARGE
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_SMALL
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainer
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainerDark
import com.github.honqout.tvlauncher3.ui.theme.PADDING_SCREEN_EDGE
import com.github.honqout.tvlauncher3.utils.IntentUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 首页快捷区的槽位数,和持久化的槽位数保持一致。 */
private const val QUICK_SLOTS = IconRepository.NUM_FIXED_ACTIVITY

/** 全部应用网格的列数。 */
private const val ALL_APPS_COLUMNS = QUICK_SLOTS

/** 抽屉展开时压暗首页的程度,让抽屉里的白字在任何壁纸下都读得清。 */
private const val DRAWER_SCRIM_ALPHA = 0.55f

/** 抽屉面板顶部与顶部栏之间的空隙。 */
private const val DRAWER_TOP_GAP_DP = 8

/**
 * 首页:底部一行快捷应用 + 按"下"拉出的全部应用面板。
 *
 * 面板整块浮在首页之上,只做 [graphicsLayer] 的纵向位移(纯 transform,不重排、不改高度),
 * 收起时整体停在屏幕下沿之外,展开时贴着顶部栏下方升起;位移用临界阻尼附近的 spring,
 * 动画中途反向按也能顺着当前速度 retarget,不会"跳一下"。
 *
 * 面板里只有全部应用,不再重复展示首页已经配置过的快捷应用。
 */
@Composable
fun HomeScreen(
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val topBarHeight by viewModel.topBarHeight.collectAsStateWithLifecycle()
    val fixedIconList by viewModel.fixedIconList.collectAsStateWithLifecycle()
    val activityModelList by viewModel.activityModelList.collectAsStateWithLifecycle()
    val showAppListDialog by viewModel.showAppListDialog.collectAsStateWithLifecycle()
    val showAppActionDialog by viewModel.showAppActionDialog.collectAsStateWithLifecycle()
    val selectedActivityModel by viewModel.selectedActivityModel.collectAsStateWithLifecycle()
    val autoStartPackageName by viewModel.autoStartPackageName.collectAsStateWithLifecycle()
    val autoStartActivityName by viewModel.autoStartActivityName.collectAsStateWithLifecycle()

    // Resolved once instead of on every item composition: the placeholder is needed by each cell.
    val defaultIcon = remember { context.packageManager.defaultActivityIcon }

    val quickRequesters = remember { List(QUICK_SLOTS) { FocusRequester() } }
    // 全部应用里"接着上次位置"用的 requester,只挂在目标那一项上。
    val appFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // 面板展开程度:0 = 完全在屏幕外,1 = 贴着顶部栏盖满整屏。
    val drawer = remember { Animatable(0f) }
    var drawerOpen by remember { mutableStateOf(false) }
    // 收起后就不再组合面板,免得首页常驻一个几十项的列表(冷启动开销)。
    var drawerLaidOut by remember { mutableStateOf(false) }
    // 当前有焦点的快捷槽位;-1 表示焦点不在快捷区(在顶部图标或全部应用里)。
    var focusedQuickSlot by remember { mutableIntStateOf(-1) }
    // 最后一个有焦点的快捷槽:面板收起后要把焦点还给它。
    var lastQuickSlot by remember { mutableIntStateOf(0) }
    // 全部应用里当前/上次有焦点的那一项。
    var focusedAppIndex by remember { mutableIntStateOf(0) }

    fun openDrawer() {
        if (drawerOpen) {
            return
        }
        drawerOpen = true
        drawerLaidOut = true
        viewModel.setDrawerOpen(true)
        viewModel.ensureActivityModelListLoaded()
        scope.launch {
            drawer.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f)
            )
        }
    }

    fun closeDrawer() {
        if (!drawerOpen) {
            return
        }
        drawerOpen = false
        viewModel.setDrawerOpen(false)
        scope.launch {
            drawer.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.95f, stiffness = 420f)
            )
            drawerLaidOut = false
            withFrameNanos { }
            val slot = lastQuickSlot.coerceIn(0, QUICK_SLOTS - 1)
            runCatching { quickRequesters[slot].requestFocus() }
        }
    }

    // 首次进入把焦点交给第一个快捷槽:它现在是首页唯一的"入口",不给的话焦点会落在顶部图标上。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (runCatching { quickRequesters.first().requestFocus() }.isFailure) {
            delay(120)
            runCatching { quickRequesters.first().requestFocus() }
        }
    }

    // 面板升起、列表就绪之后把焦点交给网格里上次停留的那一项。
    LaunchedEffect(drawerOpen, activityModelList.size) {
        if (!drawerOpen || activityModelList.isEmpty()) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (runCatching { appFocusRequester.requestFocus() }.isFailure) {
            delay(64)
            runCatching { appFocusRequester.requestFocus() }
        }
    }

    // 面板展开时按返回先收起面板,而不是退出启动器。
    BackHandler(enabled = drawerOpen) {
        closeDrawer()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer()
            // 收起状态下按"下"拉开面板;展开后焦点在最上面一行时按"上"收回。
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when {
                    event.key == Key.DirectionDown && !drawerOpen && focusedQuickSlot >= 0 -> {
                        openDrawer()
                        true
                    }

                    event.key == Key.DirectionUp && drawerOpen &&
                            focusedAppIndex < ALL_APPS_COLUMNS -> {
                        closeDrawer()
                        true
                    }

                    else -> false
                }
            }
    ) {
        // 面板压暗首页用 draw 阶段的 alpha,不参与重组。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = DRAWER_SCRIM_ALPHA * drawer.value }
                .background(Color.Black)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PADDING_SCREEN_EDGE),
            verticalArrangement = Arrangement.Bottom
        ) {
            QuickAppsRow(
                // 面板展开时快捷行被盖住,顺手淡出并让出焦点。
                modifier = Modifier.graphicsLayer { alpha = 1f - drawer.value },
                focusable = !drawerOpen,
                fixedIconList = fixedIconList,
                focusRequesters = quickRequesters,
                onFocusedSlot = { index ->
                    focusedQuickSlot = index
                    lastQuickSlot = index
                    viewModel.setFocusedFixedIconIndex(index)
                },
                onFocusChanged = { index, focused ->
                    if (focused) {
                        focusedQuickSlot = index
                        lastQuickSlot = index
                    } else if (focusedQuickSlot == index) {
                        focusedQuickSlot = -1
                    }
                },
                onMeasure = { },
                onAddItem = { index ->
                    viewModel.setFocusedFixedIconIndex(index)
                    viewModel.setShowAppListScreen(true)
                },
                onStartApp = { item ->
                    IntentUtils.handleLaunchActivityResult(
                        context,
                        IntentUtils.launchActivity(
                            context,
                            item.packageName,
                            item.activityName,
                            true
                        )
                    )
                },
                onRemoveItem = { index ->
                    viewModel.setIcon(position = index, item = null)
                },
                onReplaceItem = { index ->
                    viewModel.setFocusedFixedIconIndex(index)
                    viewModel.setShowAppListScreen(true)
                }
            )
        }

        if (drawerLaidOut) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = PADDING_SCREEN_EDGE,
                        end = PADDING_SCREEN_EDGE,
                        top = (topBarHeight + DRAWER_TOP_GAP_DP).dp,
                        bottom = PADDING_SCREEN_EDGE
                    )
            ) {
                AllAppsPanel(
                    items = activityModelList,
                    defaultIcon = defaultIcon,
                    state = gridState,
                    focusRequester = appFocusRequester,
                    focusTargetIndex = focusedAppIndex.coerceIn(
                        0,
                        activityModelList.lastIndex.coerceAtLeast(0)
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 纯位移:面板高度就是它要走的距离,收起时正好停在屏幕下沿之外。
                            translationY = (1f - drawer.value) * size.height
                            // 比位移早一点变清晰,滑上来时不会像从黑里"挖"出来
                            alpha = (drawer.value * 1.6f).coerceIn(0f, 1f)
                        },
                    onFocused = { index ->
                        focusedAppIndex = index
                        viewModel.setFocusedActivityItemIndex(index)
                    },
                    onStartApp = { item ->
                        IntentUtils.handleLaunchActivityResult(
                            context,
                            IntentUtils.launchActivity(
                                context,
                                item.packageName,
                                item.activityName,
                                true
                            )
                        )
                    },
                    onLongPress = { item ->
                        viewModel.setSelectedActivityModel(item)
                        viewModel.setShowAppActionScreen(true)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showAppListDialog,
            enter = scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
            ) + fadeIn(),
            exit = scaleOut(targetScale = 0.92f) + fadeOut()
        ) {
            AppListDialog(
                viewModel = viewModel,
                onItemChosen = { activityModel ->
                    viewModel.setIcon(position = null, item = activityModel)
                },
                onDismissRequest = {
                    viewModel.setShowAppListScreen(false)
                }
            )
        }

        val selectedModel = selectedActivityModel
        AnimatedVisibility(
            visible = showAppActionDialog && selectedModel != null,
            enter = scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
            ) + fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut()
        ) {
            if (selectedModel != null) {
                AppActionDialog(
                    item = selectedModel,
                    isAutoStart = autoStartPackageName == selectedModel.packageName &&
                            autoStartActivityName == selectedModel.activityName,
                    onToggleAutoStart = {
                        viewModel.toggleAutoStartApp(selectedModel)
                    },
                    onDismissRequest = {
                        viewModel.setShowAppActionScreen(false)
                    },
                )
            }
        }
    }
}

/**
 * 一排 [QUICK_SLOTS] 个快捷槽。空槽是"添加应用"入口,长按已配置的槽可以移除/替换。
 */
@Composable
private fun QuickAppsRow(
    fixedIconList: List<ActivityModel?>,
    focusRequesters: List<FocusRequester>,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    onFocusedSlot: (Int) -> Unit,
    onFocusChanged: (index: Int, focused: Boolean) -> Unit,
    onMeasure: (Int) -> Unit = { },
    onAddItem: (Int) -> Unit,
    onStartApp: (ActivityModel) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onReplaceItem: (Int) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasure(it.height) }
            .background(
                color = OnWallpaperContainer,
                shape = RoundedCornerShape(20.dp)
            )
            // 焦点态会把整格内容居中放大 1.1 倍,上下各外扩约 6dp;原来上下只留 6dp,
            // 聚焦时图标几乎顶到玻璃背景的上沿,所以这里把上方留白加厚。
            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        repeat(QUICK_SLOTS) { index ->
            val item = fixedIconList.getOrNull(index)
            AppShortcutButtonTv(
                modifier = Modifier.weight(1f),
                focusRequester = focusRequesters[index],
                focusable = focusable,
                activityModel = item,
                onFocused = { onFocusedSlot(index) },
                onFocusChanged = { focused -> onFocusChanged(index, focused) },
                onAddItem = { onAddItem(index) },
                onStartApp = { item?.let(onStartApp) },
                onRemoveItem = { onRemoveItem(index) },
                onReplaceItem = { onReplaceItem(index) }
            )
        }
    }
}

/**
 * 面板里的"全部应用":5 列、每项都带名称,长按出应用菜单。
 */
@Composable
private fun AllAppsPanel(
    items: List<ActivityModel>,
    defaultIcon: Drawable,
    state: LazyGridState,
    focusRequester: FocusRequester,
    focusTargetIndex: Int,
    modifier: Modifier = Modifier,
    onFocused: (Int) -> Unit,
    onStartApp: (ActivityModel) -> Unit,
    onLongPress: (ActivityModel) -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentFocusedIndex by remember { mutableIntStateOf(focusTargetIndex) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color = OnWallpaperContainerDark)
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.all_apps),
                color = ButtonContentDefault,
                fontSize = FONT_SIZE_LARGE
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (items.isEmpty()) {
                    stringResource(R.string.apps_loading)
                } else {
                    stringResource(R.string.apps_count, items.size)
                },
                color = ButtonContentDefault.copy(alpha = 0.6f),
                fontSize = FONT_SIZE_SMALL
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(ALL_APPS_COLUMNS),
            modifier = Modifier
                .fillMaxSize()
                .followGridFocus(
                    scope = scope,
                    state = state,
                    focusedItemIndex = { currentFocusedIndex },
                    itemCount = { items.size }
                ),
            state = state,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.getKey() }
            ) { index, item ->
                ActivityButtonTv(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            // 打开面板时把焦点精确落到上次停留的那一项上。
                            if (index == focusTargetIndex) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                currentFocusedIndex = index
                                onFocused(index)
                            }
                        },
                    activityModel = item,
                    defaultIcon = defaultIcon,
                    contentDefaultColor = ButtonContentDefault,
                    contentFocusedColor = ButtonContentFocused,
                    onShortClick = { onStartApp(item) },
                    onLongClick = { onLongPress(item) }
                )
            }
        }
    }
}
