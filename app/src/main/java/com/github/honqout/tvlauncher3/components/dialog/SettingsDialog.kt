package com.github.honqout.tvlauncher3.components.dialog

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.components.button.SettingsActionButtonTv
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.LauncherViewModel
import com.github.honqout.tvlauncher3.ui.theme.OnWallpaperContainerDark
import com.github.honqout.tvlauncher3.ui.theme.PADDING_DIALOG_EDGE
import com.github.honqout.tvlauncher3.ui.theme.PADDING_LIST_CONTENT_EDGE
import com.github.honqout.tvlauncher3.ui.theme.SETTINGS_ACTION_BUTTON_WIDTH
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_HORIZONTAL
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_VERTICAL
import com.github.honqout.tvlauncher3.utils.IntentUtils
import androidx.compose.foundation.lazy.grid.items

/**
 * One entry of the settings panel. [componentPackage]/[componentActivity] target the exact OEM
 * Settings component; when they are null (or missing on the device) [fallbackAction] is used.
 */
private data class SettingsEntry(
    val iconRes: Int,
    val titleRes: Int,
    val descriptionRes: Int? = null,
    val componentPackage: String? = null,
    val componentActivity: String? = null,
    val fallbackAction: String
)

private val settingsEntries = listOf(
    SettingsEntry(
        iconRes = R.drawable.baseline_settings_24,
        titleRes = R.string.settings,
        descriptionRes = R.string.settings,
        componentPackage = "com.android.settings",
        componentActivity = "com.android.settings.Settings",
        fallbackAction = Settings.ACTION_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_settings_24,
        titleRes = R.string.tv_settings,
        descriptionRes = R.string.tv_settings,
        componentPackage = "com.android.tv.settings",
        componentActivity = "com.android.tv.settings.MainSettings",
        fallbackAction = Settings.ACTION_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_wifi_24,
        titleRes = R.string.wlan,
        descriptionRes = R.string.settings,
        fallbackAction = Settings.ACTION_WIFI_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_web_24,
        titleRes = R.string.internet,
        descriptionRes = R.string.tv_settings,
        componentPackage = "com.android.tv.settings",
        componentActivity = "com.android.tv.settings.connectivity.NetworkActivity",
        fallbackAction = Settings.ACTION_WIRELESS_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_bluetooth_24,
        titleRes = R.string.bluetooth,
        descriptionRes = R.string.settings,
        fallbackAction = Settings.ACTION_BLUETOOTH_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_settings_remote_24,
        titleRes = R.string.accessory,
        descriptionRes = R.string.tv_settings,
        componentPackage = "com.android.tv.settings",
        componentActivity = "com.android.tv.settings.accessories.AddAccessoryActivity",
        fallbackAction = Settings.ACTION_BLUETOOTH_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_speaker_24,
        titleRes = R.string.sound,
        descriptionRes = R.string.settings,
        fallbackAction = Settings.ACTION_SOUND_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_speaker_24,
        titleRes = R.string.sound,
        descriptionRes = R.string.tv_settings,
        componentPackage = "com.android.tv.settings",
        componentActivity = "com.android.tv.settings.device.sound.SoundActivity",
        fallbackAction = Settings.ACTION_SOUND_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_tv_24,
        titleRes = R.string.display,
        descriptionRes = R.string.settings,
        fallbackAction = Settings.ACTION_DISPLAY_SETTINGS
    ),
    SettingsEntry(
        iconRes = R.drawable.baseline_settings_system_daydream_24,
        titleRes = R.string.screen_saver,
        descriptionRes = R.string.tv_settings,
        componentPackage = "com.android.tv.settings",
        componentActivity = "com.android.tv.settings.device.display.daydream.DaydreamActivity",
        fallbackAction = Settings.ACTION_DREAM_SETTINGS
    )
)

@Composable
fun SettingsDialog(
    launcherViewModel: LauncherViewModel,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val columnCount = 2
    val lazyGridState = rememberLazyGridState()
    // The panel hangs below the top bar, so the launcher clock and tab row stay visible.
    val topBarHeight by launcherViewModel.topBarHeight.collectAsStateWithLifecycle()
    val gridWidth =
        2 * SETTINGS_ACTION_BUTTON_WIDTH + 2 * SPACE_LIST_CONTENT_HORIZONTAL + 4 * PADDING_LIST_CONTENT_EDGE

    // OEM builds ship different Settings components. Every entry tries the exact component first
    // and falls back to the matching system action, so it degrades gracefully instead of silently
    // doing nothing.
    val launchSettingsActivity: (String, String, String) -> Unit =
        { packageName, activityName, fallbackAction ->
            IntentUtils.handleLaunchActivityResult(
                context,
                IntentUtils.launchActivityOrAction(
                    context,
                    packageName,
                    activityName,
                    fallbackAction,
                    true
                )
            )
        }
    val launchSettingsAction: (String) -> Unit = { action ->
        IntentUtils.handleLaunchIntentResult(
            context,
            IntentUtils.launchAction(context, action, true)
        )
    }

    BackHandler {
        onDismissRequest()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            // The floating panel keeps the launcher visible behind it, so outside taps are handled
            // by the transparent backdrop below. The platform check compares the touch against the
            // content view bounds, and those spans the whole screen here.
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Transparent backdrop: tapping outside the panel closes it.
                    detectTapGestures(onTap = { onDismissRequest() })
                }
                .padding(
                    top = PADDING_DIALOG_EDGE + topBarHeight.dp,
                    end = PADDING_DIALOG_EDGE,
                    bottom = PADDING_DIALOG_EDGE
                ),
            contentAlignment = Alignment.TopEnd
        ) {
            Column(
                modifier = Modifier
                    .background(
                        color = OnWallpaperContainerDark,
                        shape = RoundedCornerShape(16.dp)
                    )
                    // Keep taps landing on the panel itself from reaching the backdrop, so tapping
                    // the padding between two buttons does not dismiss the panel.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {})
                    }
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier
                        .width(gridWidth),
                    state = lazyGridState,
                    contentPadding = PaddingValues(PADDING_LIST_CONTENT_EDGE),
                    verticalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_VERTICAL),
                    horizontalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_HORIZONTAL),
                    userScrollEnabled = true
                ) {
                    items(settingsEntries) { entry ->
                        val onShortClick = {
                            if (entry.componentPackage != null &&
                                entry.componentActivity != null
                            ) {
                                launchSettingsActivity(
                                    entry.componentPackage,
                                    entry.componentActivity,
                                    entry.fallbackAction
                                )
                            } else {
                                launchSettingsAction(entry.fallbackAction)
                            }
                        }
                        if (entry.descriptionRes != null) {
                            SettingsActionButtonTv(
                                iconRes = entry.iconRes,
                                contentDescriptionRes = entry.titleRes,
                                titleRes = entry.titleRes,
                                descriptionRes = entry.descriptionRes,
                                onShortClick = onShortClick
                            )
                        } else {
                            SettingsActionButtonTv(
                                iconRes = entry.iconRes,
                                contentDescriptionRes = entry.titleRes,
                                titleRes = entry.titleRes,
                                onShortClick = onShortClick
                            )
                        }
                    }
                }
            }
        }
    }
}
