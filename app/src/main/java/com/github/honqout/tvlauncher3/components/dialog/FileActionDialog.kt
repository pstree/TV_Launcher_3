package com.github.honqout.tvlauncher3.components.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.components.button.AppActionButtonTv
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_HEADLINE
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainerDark
import com.github.honqout.tvlauncher3.ui.theme.PADDING_DIALOG_EDGE

/**
 * 文件页的"菜单键"操作面板:复制 / 粘贴 / 删除。
 *
 * [itemName] 为 null 表示当前没有选中的文件或文件夹(例如目录是空的),此时只提供粘贴,
 * 这样空了目录也能把剪贴板里的内容粘贴进去。
 */
@Composable
fun FileActionDialog(
    itemName: String?,
    canPaste: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit
) {
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .background(
                        color = OnWallpaperContainerDark,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(PADDING_DIALOG_EDGE)
                    .focusGroup(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = itemName ?: stringResource(R.string.files),
                    color = Color.White,
                    fontSize = FONT_SIZE_HEADLINE,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (itemName != null) {
                    AppActionButtonTv(
                        iconRes = R.drawable.baseline_content_copy_24,
                        labelRes = R.string.copy,
                        onShortClick = onCopy
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                AppActionButtonTv(
                    iconRes = R.drawable.baseline_content_paste_24,
                    labelRes = R.string.paste,
                    enabled = canPaste,
                    onShortClick = onPaste
                )

                if (itemName != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    AppActionButtonTv(
                        iconRes = R.drawable.baseline_delete_24,
                        labelRes = R.string.delete,
                        onShortClick = onDelete
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                AppActionButtonTv(
                    iconRes = R.drawable.baseline_close_24,
                    labelRes = R.string.cancel,
                    onShortClick = onDismissRequest
                )
            }
        }
    }
}
