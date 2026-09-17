package com.github.honqout.tvlauncher3.components.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.components.button.ActivityButtonTv
import com.github.honqout.tvlauncher3.components.grid.followGridFocus
import com.github.honqout.tvlauncher3.data.ActivityModel
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.LauncherViewModel
import com.github.honqout.tvlauncher3.ui.theme.ButtonContentDefault
import com.github.honqout.tvlauncher3.ui.theme.ButtonContentFocused
import com.github.honqout.tvlauncher3.ui.theme.PADDING_DIALOG_EDGE
import com.github.honqout.tvlauncher3.ui.theme.PADDING_LIST_CONTENT_EDGE
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_HORIZONTAL
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_VERTICAL

@Composable
fun AppListDialog(
    viewModel: LauncherViewModel = hiltViewModel(),
    onItemChosen: (index: Int, activityModel: ActivityModel) -> Unit = { _, _ -> },
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val defaultIcon = remember { context.packageManager.defaultActivityIcon }
    val numColumns = viewModel.numColumns
    val activityModelList by viewModel.activityModelList.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val lazyGridState = rememberLazyGridState()
    var focusedItemIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.ensureActivityModelListLoaded()
    }

    BackHandler {
        onDismissRequest()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(PADDING_DIALOG_EDGE)
        ) {
            Text(
                text = stringResource(R.string.choose_an_app),
                modifier = Modifier
                    .focusable(false),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(numColumns),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color.Transparent)
                    .focusable(false)
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
                                    focusedItemIndex = index
                                }
                            },
                        activityModel = item,
                        defaultIcon = defaultIcon,
                        contentDefaultColor = ButtonContentDefault,
                        contentFocusedColor = ButtonContentFocused,
                        onShortClick = {
                            onItemChosen(index, item)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}