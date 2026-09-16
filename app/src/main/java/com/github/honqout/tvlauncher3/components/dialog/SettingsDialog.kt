package com.github.honqout.tvlauncher3.components.dialog

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.components.button.SettingsActionButtonTv
import com.github.honqout.tvlauncher3.components.text.DateAndWeekdayText
import com.github.honqout.tvlauncher3.components.text.TimeText
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.LauncherViewModel
import com.github.honqout.tvlauncher3.ui.launcher.viewmodel.TimeViewModel
import com.github.honqout.tvlauncher3.ui.theme.FONT_SIZE_EXTRA_LARGE
import com.github.honqout.tvlauncher3.ui.theme.PADDING_DIALOG_EDGE
import com.github.honqout.tvlauncher3.ui.theme.PADDING_LIST_CONTENT_EDGE
import com.github.honqout.tvlauncher3.ui.theme.SETTINGS_ACTION_BUTTON_WIDTH
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_HORIZONTAL
import com.github.honqout.tvlauncher3.ui.theme.SPACE_LIST_CONTENT_VERTICAL
import com.github.honqout.tvlauncher3.utils.IntentUtils

@Composable
fun SettingsDialog(
    launcherViewModel: LauncherViewModel,
    timeViewModel: TimeViewModel,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val columnCount = 2
    val lazyGridState = rememberLazyGridState()
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
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight()
                    .background(Color.Transparent)
                    .align(Alignment.TopEnd)
                    .padding(PADDING_DIALOG_EDGE)
            ) {
                Row(
                    modifier = Modifier
                        .height(topBarHeight.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeText(
                        modifier = Modifier,
                        viewModel = timeViewModel,
                        color = Color.White,
                        fontSize = 30.sp
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    DateAndWeekdayText(
                        modifier = Modifier,
                        viewModel = timeViewModel,
                        color = Color.White,
                        fontSize = FONT_SIZE_EXTRA_LARGE
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier
                        .width(gridWidth)
                        .fillMaxHeight(),
                    state = lazyGridState,
                    contentPadding = PaddingValues(PADDING_LIST_CONTENT_EDGE),
                    verticalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_VERTICAL),
                    horizontalArrangement = Arrangement.spacedBy(SPACE_LIST_CONTENT_HORIZONTAL),
                    userScrollEnabled = true
                ) {
                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_settings_24,
                            contentDescriptionRes = R.string.settings,
                            titleRes = R.string.settings,
                            onShortClick = {
                                launchSettingsActivity(
                                    "com.android.settings",
                                    "com.android.settings.Settings",
                                    Settings.ACTION_SETTINGS
                                )
                            }
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_settings_24,
                            contentDescriptionRes = R.string.tv_settings,
                            titleRes = R.string.tv_settings,
                            onShortClick = {
                                launchSettingsActivity(
                                    "com.android.tv.settings",
                                    "com.android.tv.settings.MainSettings",
                                    Settings.ACTION_SETTINGS
                                )
                            }
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_wifi_24,
                            contentDescriptionRes = R.string.wlan,
                            titleRes = R.string.wlan,
                            descriptionRes = R.string.settings,
                            onShortClick = {
                                launchSettingsAction(Settings.ACTION_WIFI_SETTINGS)
                            },
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_web_24,
                            contentDescriptionRes = R.string.internet,
                            titleRes = R.string.internet,
                            descriptionRes = R.string.tv_settings,
                            onShortClick = {
                                launchSettingsActivity(
                                    "com.android.tv.settings",
                                    "com.android.tv.settings.connectivity.NetworkActivity",
                                    Settings.ACTION_WIRELESS_SETTINGS
                                )
                            }
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_bluetooth_24,
                            contentDescriptionRes = R.string.bluetooth,
                            titleRes = R.string.bluetooth,
                            descriptionRes = R.string.settings,
                            onShortClick = {
                                launchSettingsAction(Settings.ACTION_BLUETOOTH_SETTINGS)
                            }
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_settings_remote_24,
                            contentDescriptionRes = R.string.accessory,
                            titleRes = R.string.accessory,
                            descriptionRes = R.string.tv_settings,
                            onShortClick = {
                                launchSettingsActivity(
                                    "com.android.tv.settings",
                                    "com.android.tv.settings.accessories.AddAccessoryActivity",
                                    Settings.ACTION_BLUETOOTH_SETTINGS
                                )
                            }
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_speaker_24,
                            contentDescriptionRes = R.string.sound,
                            titleRes = R.string.sound,
                            descriptionRes = R.string.settings,
                            onShortClick = {
                                launchSettingsAction(Settings.ACTION_SOUND_SETTINGS)
                            }
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_speaker_24,
                            contentDescriptionRes = R.string.sound,
                            titleRes = R.string.sound,
                            descriptionRes = R.string.tv_settings,
                            onShortClick = {
                                launchSettingsActivity(
                                    "com.android.tv.settings",
                                    "com.android.tv.settings.device.sound.SoundActivity",
                                    Settings.ACTION_SOUND_SETTINGS
                                )
                            }
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_tv_24,
                            contentDescriptionRes = R.string.display,
                            titleRes = R.string.display,
                            descriptionRes = R.string.settings,
                            onShortClick = {
                                launchSettingsAction(Settings.ACTION_DISPLAY_SETTINGS)
                            }
                        )
                    }

                    item {
                        SettingsActionButtonTv(
                            iconRes = R.drawable.baseline_settings_system_daydream_24,
                            contentDescriptionRes = R.string.screen_saver,
                            titleRes = R.string.screen_saver,
                            descriptionRes = R.string.tv_settings,
                            onShortClick = {
                                launchSettingsActivity(
                                    "com.android.tv.settings",
                                    "com.android.tv.settings.device.display.daydream.DaydreamActivity",
                                    Settings.ACTION_DREAM_SETTINGS
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}