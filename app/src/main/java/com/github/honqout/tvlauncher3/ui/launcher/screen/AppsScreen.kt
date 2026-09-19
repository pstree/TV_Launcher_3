package com.github.honqout.tvlauncher3.ui.launcher.screen

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.honqout.tvlauncher3.components.button.ActivityButtonTv
import com.github.honqout.tvlauncher3.components.dialog.AppActionDialog
import com.github.honqout.tvlauncher3.components.grid.followGridFocus
import com.github.honqout.tvlauncher3.data.ActivityModel
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.LauncherViewModel
import com.github.honqout.tvlauncher3.ui.theme.ButtonContentDefault
import com.github.honqout.tvlauncher3.ui.theme.ButtonContentFocused
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainer
import com.github.honqout.tvlauncher3.ui.theme.PADDING_LIST_CONTENT_EDGE
import com.github.honqout.tvlauncher3.ui.theme.PADDING_SCREEN_EDGE
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_HORIZONTAL
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_VERTICAL
import com.github.honqout.tvlauncher3.utils.IntentUtils

@Composable
fun AppsScreen(
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lazyGridState = rememberLazyGridState()
    val numColumns = viewModel.numColumns
    // Resolved once instead of on every item composition: the placeholder is needed by each cell.
    val defaultIcon = remember { context.packageManager.defaultActivityIcon }
    val topBarHeight by viewModel.topBarHeight.collectAsStateWithLifecycle()
    val showAppActionDialog by viewModel.showAppActionDialog.collectAsStateWithLifecycle()
    val activityModelList by viewModel.activityModelList.collectAsStateWithLifecycle()
    val focusedItemIndex by viewModel.focusedActivityItemIndex.collectAsStateWithLifecycle()
    val activityModel: ActivityModel? by viewModel.selectedActivityModel.collectAsStateWithLifecycle()
    val autoStartPackageName by viewModel.autoStartPackageName.collectAsStateWithLifecycle()
    val autoStartActivityName by viewModel.autoStartActivityName.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ensureActivityModelListLoaded()
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
            Spacer(modifier = Modifier.height((topBarHeight + 16).dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(numColumns),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = OnWallpaperContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .weight(weight = 1.0f)
                    .followGridFocus(
                        scope = coroutineScope,
                        state = lazyGridState,
                        focusedItemIndex = { focusedItemIndex },
                        itemCount = { activityModelList.size }
                    ),
                state = lazyGridState,
                contentPadding = PaddingValues(PADDING_LIST_CONTENT_EDGE),
                verticalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_VERTICAL),
                horizontalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_HORIZONTAL),
                userScrollEnabled = true
            ) {
                itemsIndexed(activityModelList) { index, item ->
                    ActivityButtonTv(
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    viewModel.setFocusedActivityItemIndex(index)
                                }
                            },
                        activityModel = item,
                        defaultIcon = defaultIcon,
                        contentDefaultColor = ButtonContentDefault,
                        contentFocusedColor = ButtonContentFocused,
                        onShortClick = {
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
                        onLongClick = {
                            viewModel.setSelectedActivityModel(item)
                            viewModel.setShowAppActionScreen(true)
                        }
                    )
                }
            }
        }

        val selectedModel = activityModel
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