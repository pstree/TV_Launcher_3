package com.github.honqout.tvlauncher3.ui.launcher.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.data.ActivityModel
import com.github.honqout.tvlauncher3.datastore.repository.IconRepository
import com.github.honqout.tvlauncher3.utils.ApplicationUtils
import com.github.honqout.tvlauncher3.utils.ApplicationUtils.Companion.LauncherActivityType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
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
    private val iconRepository: IconRepository
) : AndroidViewModel(application) {
    // constant
    val numColumns = IconRepository.NUM_FIXED_ACTIVITY

    // UI-related
    val tabs = listOf(
        Pair(R.drawable.baseline_home_24, R.string.home),
        Pair(R.drawable.baseline_apps_24, R.string.apps),
        Pair(R.drawable.baseline_folder_24, R.string.files)
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

    // broadcast receiver
    private var packageBroadcastReceiver: BroadcastReceiver? = null

    companion object {
        const val TAG: String = "LauncherViewModel"

        enum class IconListOp {
            REMOVE_AFTER_UNINSTALL, REMOVE_AFTER_UPDATE
        }

        enum class AppListOp {
            INIT, ADD, REMOVE, REPLACE
        }
    }

    // Enumerating every installed launcher activity costs a PackageManager query per app, so it is
    // deferred until the Apps tab (or the app picker) is actually shown instead of running on the
    // cold start path.
    @Volatile
    private var activityModelListLoadRequested = false

    init {
        registerPackageBR()
        initializeIcons()
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
                    updateFixedIconList(IconListOp.REMOVE_AFTER_UNINSTALL, packageName)
                }
            }
            if (op == AppListOp.REPLACE) {
                packageName?.let {
                    updateFixedIconList(IconListOp.REMOVE_AFTER_UPDATE, packageName)
                }
            }
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