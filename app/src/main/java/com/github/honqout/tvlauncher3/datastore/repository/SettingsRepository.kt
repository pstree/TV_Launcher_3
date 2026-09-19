package com.github.honqout.tvlauncher3.datastore.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.github.honqout.tvlauncher3.LauncherSettings
import com.github.honqout.tvlauncher3.datastore.serializer.LauncherSettingsSerializer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val DATA_STORE_FILE_NAME = "launcher_settings.pb"

val Context.launcherSettingsDataStore: DataStore<LauncherSettings> by dataStore(
    fileName = DATA_STORE_FILE_NAME,
    serializer = LauncherSettingsSerializer
)

/**
 * Persists the launcher-wide settings that are not tied to the fixed home-screen shortcuts, such
 * as which application (if any) should be started automatically once the device has booted.
 */
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val TAG: String = "SettingsRepository"
    }

    private val dataStore = context.launcherSettingsDataStore

    val settingsFlow: Flow<LauncherSettings> = dataStore.data
        .catch { e ->
            Log.e(TAG, "Error reading settings.", e)
            emit(LauncherSettings.getDefaultInstance())
        }

    /**
     * Mark [packageName]/[activityName] as the app to start after boot. Passing an empty
     * packageName clears the selection.
     */
    suspend fun setAutoStartApp(packageName: String, activityName: String) {
        dataStore.updateData { currentData ->
            currentData.toBuilder()
                .setAutoStartPackageName(packageName)
                .setAutoStartActivityName(activityName)
                .build()
        }
    }

    /**
     * The app recorded for auto-start, or null when none is set.
     */
    suspend fun getAutoStartApp(): Pair<String, String>? {
        val settings = settingsFlow.first()
        return settings.autoStartPackageName
            .takeIf { it.isNotEmpty() }
            ?.let { packageName -> Pair(packageName, settings.autoStartActivityName) }
    }

    /**
     * The boot id the auto-start app was last launched for, so it is only started once per boot.
     */
    suspend fun getLastAutoStartBootId(): Long {
        return settingsFlow.first().lastAutoStartBootId
    }

    suspend fun setLastAutoStartBootId(bootId: Long) {
        dataStore.updateData { currentData ->
            currentData.toBuilder()
                .setLastAutoStartBootId(bootId)
                .build()
        }
    }
}
