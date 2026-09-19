package com.github.honqout.tvlauncher3.ui.launcher.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.data.ActivityModel
import com.github.honqout.tvlauncher3.datastore.repository.IconRepository
import com.github.honqout.tvlauncher3.datastore.repository.SettingsRepository
import com.github.honqout.tvlauncher3.utils.ApplicationUtils
import com.github.honqout.tvlauncher3.utils.ApplicationUtils.Companion.LauncherActivityType
import com.github.honqout.tvlauncher3.utils.IntentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    application: Application,
    private val iconRepository: IconRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {
    // constant
    val numColumns = IconRepository.NUM_FIXED_ACTIVITY

    // UI-related
    val tabs = listOf(
        Pair(R.drawable.baseline_home_24, R.string.home),
        Pair(R.drawable.baseline_apps_24, R.string.apps),
        Pair(R.drawable.baseline_folder_24, R.string.files),
        Pair(R.drawable.baseline_wallpaper_24, R.string.wallpaper)
    )
    private var oldConfig: Configuration? = null
    private val _topBarHeight = MutableStateFlow<Int>(0)
    val topBarHeight: StateFlow<Int> = _topBarHeight.asStateFlow()
    private val _selectedTabIndex = MutableStateFlow<Int>(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()
    private val _showSettingsDialog = MutableStateFlow<Boolean>(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()
    private val _showAppListDialog = MutableStateFlow<Boolean>(false)
    val showAppListDialog: StateFlow<Boolean> = _showAppListDialog.asStateFlow()
    private val _showAppActionDialog = MutableStateFlow<Boolean>(false)
    val showAppActionDialog: StateFlow<Boolean> = _showAppActionDialog.asStateFlow()

    // data
    private val refreshFixedIconListSignal = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply {
        tryEmit(Unit)
    }
    val fixedIconList: StateFlow<List<ActivityModel?>> =
        combine(
            iconRepository.itemsFlow,
            refreshFixedIconListSignal
        ) { originalList, _ ->
            originalList.map { item ->
                val resolveInfo = ApplicationUtils.getLauncherActivity(
                    application,
                    LauncherActivityType.NORMAL,
                    item.packageName,
                    item.activityName
                )
                resolveInfo?.let { ActivityModel.fromResolveInfo(application, resolveInfo) }
            }
        }
            // Resolving icons and labels hits PackageManager (IPC), never do that on the main thread.
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                List(IconRepository.NUM_FIXED_ACTIVITY) { null }
            )
    private val _activityModelList = MutableStateFlow<List<ActivityModel>>(emptyList())
    val activityModelList: StateFlow<List<ActivityModel>> = _activityModelList.asStateFlow()
    private var focusedFixedIconIndex = -1
    private val _focusedActivityItemIndex = MutableStateFlow<Int>(-1)
    val focusedActivityItemIndex: StateFlow<Int> = _focusedActivityItemIndex.asStateFlow()
    private val _selectedActivityModel = MutableStateFlow<ActivityModel?>(null)
    val selectedActivityModel: StateFlow<ActivityModel?> = _selectedActivityModel.asStateFlow()

    // The application selected for auto-start, empty when the feature is off.
    private val _autoStartPackageName = MutableStateFlow("")
    val autoStartPackageName: StateFlow<String> = _autoStartPackageName.asStateFlow()
    private val _autoStartActivityName = MutableStateFlow("")
    val autoStartActivityName: StateFlow<String> = _autoStartActivityName.asStateFlow()

    // broadcast receiver
    private var packageBroadcastReceiver: BroadcastReceiver? = null

    companion object {
        const val TAG: String = "LauncherViewModel"

        // Delay before the auto-start app is launched, so the launcher has time to draw its first
        // frame before another activity takes over the screen.
        private const val AUTO_START_LAUNCH_DELAY_MS = 1500L

        enum class IconListOp {
            REMOVE_AFTER_UNINSTALL, REMOVE_AFTER_UPDATE
        }

        enum class AppListOp {
            INIT, ADD, REMOVE, REPLACE
        }
    }

    // The BOOT_COUNT that was last handled, so the auto-start app is launched only once per boot.
    private var lastHandledBootId = -2L

    // Enumerating every installed launcher activity costs a PackageManager query per app, so it is
    // deferred until the Apps tab (or the app picker) is actually shown instead of running on the
    // cold start path.
    @Volatile
    private var activityModelListLoadRequested = false

    init {
        registerPackageBR()
        initializeIcons()
        observeAutoStartApp()
    }

    /**
     * Load the launchable-activity list unless that already happened. Safe to call from every
     * screen that displays it.
     */
    fun ensureActivityModelListLoaded() {
        if (activityModelListLoadRequested) {
            return
        }
        activityModelListLoadRequested = true
        updateActivityModelList(AppListOp.INIT, null)
    }

    override fun onCleared() {
        // viewModelScope is cancelled by the framework; only the broadcast receiver needs care.
        unregisterPackageBR()
        super.onCleared()
    }

    fun registerPackageBR() {
        if (packageBroadcastReceiver != null) {
            return
        }

        val context = getApplication<Application>()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        val receiverFlags = ContextCompat.RECEIVER_NOT_EXPORTED

        packageBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) {
                    Log.i(TAG, "Received intent is null.")
                    return
                }
                val action: String = intent.action ?: return
                Log.i(TAG, "Received intent action: $action")
                when (action) {
                    Intent.ACTION_PACKAGE_ADDED -> {
                        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                        Log.i(TAG, "Extra: replacing = $replacing")
                        if (!replacing) {
                            val packageName = intent.data?.schemeSpecificPart
                            if (packageName != null) {
                                updateActivityModelList(AppListOp.ADD, packageName)
                            } else {
                                Log.e(TAG, "Failed to get packageName.")
                            }
                        }
                    }

                    Intent.ACTION_PACKAGE_REMOVED -> {
                        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                        val dataRemoved =
                            intent.getBooleanExtra(Intent.EXTRA_DATA_REMOVED, false)
                        Log.i(TAG, "Extra: replacing = $replacing, data_removed = $dataRemoved")
                        if (!replacing) {
                            val packageName = intent.data?.schemeSpecificPart
                            if (packageName != null) {
                                updateActivityModelList(AppListOp.REMOVE, packageName)
                            } else {
                                Log.e(TAG, "Failed to get packageName.")
                            }
                        }
                    }

                    Intent.ACTION_PACKAGE_REPLACED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName != null) {
                            updateActivityModelList(AppListOp.REPLACE, packageName)
                        } else {
                            Log.e(TAG, "Failed to get packageName.")
                        }
                    }
                }
            }
        }

        ContextCompat.registerReceiver(context, packageBroadcastReceiver, filter, receiverFlags)
    }

    fun unregisterPackageBR() {
        packageBroadcastReceiver?.let {
            val context = getApplication<Application>()
            context.unregisterReceiver(it)
            packageBroadcastReceiver = null
        }
    }

    fun initializeIcons() {
        viewModelScope.launch {
            try {
                iconRepository.initializeIcons()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize icons.", e)
            }
        }
    }

    /**
     * Keep the in-memory auto-start selection in sync with the persisted one.
     */
    private fun observeAutoStartApp() {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _autoStartPackageName.value = settings.autoStartPackageName
                _autoStartActivityName.value = settings.autoStartActivityName
            }
        }
    }

    /**
     * Turn auto-start on for [item] when it is currently off, and off again when [item] is already
     * the selected app.
     */
    fun toggleAutoStartApp(item: ActivityModel) {
        viewModelScope.launch {
            try {
                if (isAutoStartApp(item)) {
                    settingsRepository.setAutoStartApp("", "")
                } else {
                    settingsRepository.setAutoStartApp(item.packageName, item.activityName)
                    // Mark the current boot as handled, so enabling the feature only takes effect
                    // from the next boot onwards instead of launching the app right away.
                    val bootId = readBootCount()
                    if (bootId >= 0) {
                        settingsRepository.setLastAutoStartBootId(bootId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update the auto-start app.", e)
            }
        }
    }

    /**
     * Whether [item] is the app currently selected for auto-start.
     */
    fun isAutoStartApp(item: ActivityModel): Boolean {
        return _autoStartPackageName.value == item.packageName &&
                _autoStartActivityName.value == item.activityName
    }

    /**
     * Launch the user-selected app once per boot.
     *
     * The launcher is a HOME app, so it is already brought up by the system at boot; waiting for a
     * BOOT_COMPLETED broadcast here would race with that and is unnecessary. [Settings.Global.BOOT_COUNT]
     * is used to make sure the app is only started on the first launch of a boot (a HOME app is
     * recreated whenever the user returns to the home screen or after a configuration change), and
     * it is persisted so a recreated ViewModel does not launch it again.
     */
    fun launchAutoStartAppIfNeeded() {
        viewModelScope.launch {
            try {
                val autoStartApp = settingsRepository.getAutoStartApp()
                if (autoStartApp == null) {
                    return@launch
                }
                val bootId = readBootCount()
                val alreadyHandled = if (bootId >= 0) {
                    settingsRepository.getLastAutoStartBootId() == bootId
                } else {
                    // BOOT_COUNT unavailable (pre-API 24): fall back to the in-memory guard, which
                    // at least covers recompositions within the same ViewModel.
                    lastHandledBootId == bootId
                }
                if (alreadyHandled) {
                    return@launch
                }
                lastHandledBootId = bootId
                if (bootId >= 0) {
                    settingsRepository.setLastAutoStartBootId(bootId)
                }
                // Give the system a moment to settle before stealing focus from the launcher.
                delay(AUTO_START_LAUNCH_DELAY_MS)
                val (packageName, activityName) = autoStartApp
                IntentUtils.handleLaunchActivityResult(
                    getApplication(),
                    IntentUtils.launchActivity(
                        getApplication(),
                        packageName,
                        activityName,
                        true
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch the auto-start app.", e)
            }
        }
    }

    private fun readBootCount(): Long {
        return try {
            Settings.Global.getLong(
                getApplication<Application>().contentResolver,
                Settings.Global.BOOT_COUNT
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.w(TAG, "Cannot read BOOT_COUNT, falling back to the in-memory guard.")
            -1L
        }
    }

    fun setIcon(position: Int?, item: ActivityModel?) {
        val targetPosition = position ?: focusedFixedIconIndex
        if (targetPosition !in 0..<IconRepository.NUM_FIXED_ACTIVITY) {
            Log.e(TAG, "Cannot set icon. Invalid target position: $targetPosition.")
            return
        }
        viewModelScope.launch {
            try {
                iconRepository.setIconByIndex(
                    targetPosition,
                    item?.packageName ?: "",
                    item?.activityName ?: ""
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set icon.", e)
            }
        }
    }

    fun updateFixedIconList(op: IconListOp, packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (op) {
                IconListOp.REMOVE_AFTER_UNINSTALL -> {
                    iconRepository.resetIconAfterPackageRemoved(packageName)
                }

                IconListOp.REMOVE_AFTER_UPDATE -> {
                    iconRepository.resetIconAfterPackageReplaced(getApplication(), packageName)
                }
            }
        }
    }

    fun updateActivityModelList(op: AppListOp, packageName: String?) {
        // Querying the package manager is IO bound, not CPU bound.
        viewModelScope.launch(Dispatchers.IO) {
            // Initialize the whole list
            if (op == AppListOp.INIT || _activityModelList.value.isEmpty()) {
                activityModelListLoadRequested = true
                val list = ApplicationUtils.getActivityModelList(
                    getApplication(),
                    LauncherActivityType.NORMAL,
                    null
                )
                _activityModelList.value = sortActivityModelList(list)
                return@launch
            }
            // Save focused item
            val focusedItem =
                if (_focusedActivityItemIndex.value in _activityModelList.value.indices) {
                    _activityModelList.value[_focusedActivityItemIndex.value]
                } else {
                    _activityModelList.value[0]
                }
            // Remove
            if (op == AppListOp.REMOVE || op == AppListOp.REPLACE) {
                _activityModelList.update { currentList ->
                    val mutableList = currentList.toMutableList()
                    val removeResult = mutableList.removeAll { activityModel ->
                        activityModel.packageName == packageName
                    }
                    Log.i(TAG, "Removed items from ActivityModel list: $removeResult")
                    mutableList
                }
            }
            // Add. The PackageManager query runs OUTSIDE the StateFlow update: update's lambda can
            // be re-executed on CAS failure, which would duplicate the IPC-heavy query.
            if (op == AppListOp.ADD || op == AppListOp.REPLACE) {
                val addedModels = ApplicationUtils.getActivityModelList(
                    getApplication(),
                    LauncherActivityType.NORMAL,
                    packageName
                )
                _activityModelList.update { currentList ->
                    sortActivityModelList(currentList + addedModels)
                }
            }
            // Restore focused item
            val currentIndex =
                if (op == AppListOp.ADD || op == AppListOp.REPLACE) {
                    _activityModelList.value.indexOf(focusedItem)
                } else {
                    _focusedActivityItemIndex.value
                }
            if (currentIndex in _activityModelList.value.indices) {
                setFocusedActivityItemIndex(currentIndex)
            } else {
                setFocusedActivityItemIndex(0)
            }
            // Update fixed activities
            if (op == AppListOp.REMOVE) {
                packageName?.let {
                    updateFixedIconList(IconListOp.REMOVE_AFTER_UNINSTALL, it)
                    clearAutoStartIfRemoved(it)
                }
            }
            if (op == AppListOp.REPLACE) {
                packageName?.let {
                    updateFixedIconList(IconListOp.REMOVE_AFTER_UPDATE, it)
                }
            }
        }
    }

    /**
     * Drop the auto-start selection when its package has been uninstalled. An update keeps the
     * selection, since the launcher activity name is normally left untouched.
     */
    private suspend fun clearAutoStartIfRemoved(packageName: String) {
        val selected = settingsRepository.getAutoStartApp() ?: return
        if (selected.first == packageName) {
            settingsRepository.setAutoStartApp("", "")
        }
    }

    fun sortActivityModelList(list: List<ActivityModel>): List<ActivityModel> {
        return list.sortedBy {
            it.label.lowercase()
        }
    }

    fun onConfigChanged(newConfig: Configuration) {
        oldConfig?.let {
            val diff = it.diff(newConfig)
            if ((diff and ActivityInfo.CONFIG_LOCALE) != 0) {
                // Labels have to be resolved again, but only if the list was ever loaded.
                if (activityModelListLoadRequested) {
                    updateActivityModelList(AppListOp.INIT, null)
                }
                refreshFixedIconListSignal.tryEmit(Unit)
            }
        }
        oldConfig = newConfig
    }

    fun setTopBarHeight(newValue: Int) {
        _topBarHeight.update {
            newValue
        }
    }

    fun setSelectedTabIndex(newValue: Int) {
        _selectedTabIndex.update {
            newValue
        }
    }

    fun setShowSettingsScreen(newValue: Boolean) {
        _showSettingsDialog.update {
            newValue
        }
    }

    fun setShowAppListScreen(newValue: Boolean) {
        _showAppListDialog.update {
            newValue
        }
    }

    fun setShowAppActionScreen(newValue: Boolean) {
        _showAppActionDialog.update {
            newValue
        }
    }

    fun setFocusedFixedIconIndex(newValue: Int) {
        focusedFixedIconIndex = newValue
    }

    fun setFocusedActivityItemIndex(newValue: Int) {
        _focusedActivityItemIndex.update {
            newValue
        }
    }

    fun setSelectedActivityModel(newValue: ActivityModel) {
        _selectedActivityModel.update {
            newValue
        }
    }
}