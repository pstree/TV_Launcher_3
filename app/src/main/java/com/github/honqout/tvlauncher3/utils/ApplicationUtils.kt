package com.github.honqout.tvlauncher3.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.github.honqout.tvlauncher3.data.ActivityModel

class ApplicationUtils {
    companion object {
        private const val TAG: String = "ApplicationUtils"

        enum class ApplicationType {
            UNKNOWN, SYSTEM, UPDATED_SYSTEM, USER
        }

        enum class LauncherActivityType {
            NORMAL, LEANBACK
        }

        enum class IconType {
            Icon, Banner
        }

        fun getPackageName(resolveInfo: ResolveInfo?): String {
            return resolveInfo?.activityInfo?.packageName ?: ""
        }

        fun getPackageInfo(context: Context, packageName: String?): PackageInfo? {
            if (packageName.isNullOrEmpty()) {
                Log.e(TAG, "Cannot get PackageInfo. The given packageName is null or empty.")
                return null
            }
            val pm = context.packageManager
            try {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getPackageInfo(packageName, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Cannot get PackageInfo. Package $packageName doesn't exist.", e)
                return null
            }
        }

        fun getApplicationInfo(context: Context, packageName: String?): ApplicationInfo? {
            if (packageName.isNullOrEmpty()) {
                Log.e(TAG, "Cannot get ApplicationInfo. The given packageName is null or empty.")
                return null
            }
            val pm = context.packageManager
            try {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    pm.getApplicationInfo(packageName, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Cannot get ApplicationInfo. Package $packageName doesn't exist.", e)
                return null
            }
        }

        fun getApplicationIcon(context: Context, packageName: String?): Drawable {
            val pm = context.packageManager
            if (packageName.isNullOrEmpty()) {
                Log.e(TAG, "Cannot get application icon. The given packageName is null or empty.")
                return pm.defaultActivityIcon
            }
            try {
                return pm.getApplicationIcon(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Cannot get application icon. Package $packageName doesn't exist.", e)
                return pm.defaultActivityIcon
            }
        }

        fun getApplicationBanner(context: Context, packageName: String?): Drawable {
            val pm = context.packageManager
            if (packageName.isNullOrEmpty()) {
                Log.e(TAG, "Cannot get application banner. The given packageName is null or empty.")
                return pm.defaultActivityIcon
            }
            try {
                return pm.getApplicationBanner(packageName) ?: pm.defaultActivityIcon
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Cannot get application banner. Package $packageName doesn't exist.", e)
                return pm.defaultActivityIcon
            }
        }

        fun getApplicationLabel(context: Context, packageName: String): String? {
            val applicationInfo = getApplicationInfo(context, packageName) ?: return null
            val pm = context.packageManager
            return applicationInfo.loadLabel(pm).toString()
        }

        fun getApplicationType(context: Context, packageName: String?): ApplicationType {
            val applicationInfo = getApplicationInfo(context, packageName)
            if (applicationInfo != null) {
                val flags = applicationInfo.flags
                return if (flags.and(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    ApplicationType.UPDATED_SYSTEM
                } else if (flags.and(ApplicationInfo.FLAG_SYSTEM) != 0) {
                    ApplicationType.SYSTEM
                } else {
                    ApplicationType.USER
                }
            }
            return ApplicationType.UNKNOWN
        }

        fun getActivityInfo(
            context: Context,
            packageName: String?,
            activityName: String?
        ): ActivityInfo? {
            if (packageName.isNullOrEmpty()) {
                Log.e(TAG, "Cannot get ActivityInfo. The given packageName is null or empty.")
                return null
            }
            if (activityName.isNullOrEmpty()) {
                Log.e(TAG, "Cannot get ActivityInfo. The given activityName is null or empty.")
                return null
            }
            val pm = context.packageManager
            try {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getActivityInfo(
                        ComponentName(packageName, activityName),
                        PackageManager.ComponentInfoFlags.of(0)
                    )
                } else {
                    pm.getActivityInfo(ComponentName(packageName, activityName), 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(
                    TAG,
                    "Cannot get ActivityInfo. Activity $activityName of package $packageName doesn't exist.",
                    e
                )
            }
            return null
        }

        fun getActivityIcon(
            context: Context,
            packageName: String?,
            activityName: String?
        ): Drawable {
            val pm = context.packageManager
            if (packageName.isNullOrEmpty()) {
                Log.e(TAG, "Cannot get activity icon. The given packageName is empty.")
                return pm.defaultActivityIcon
            }
            if (activityName.isNullOrEmpty()) {
                Log.e(TAG, "Cannot get activity icon. The given activityName is empty.")
                return pm.defaultActivityIcon
            }
            try {
                return pm.getActivityIcon(ComponentName(packageName, activityName))
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(
                    TAG,
                    "Cannot get activity icon. Activity $activityName of package $packageName doesn't exist.",
                    e
                )
                return pm.defaultActivityIcon
            }
        }

        /**
         * Get Pair(IconType, (Drawable) Icon) of a concrete activity of the application.
         * @return Pair(IconType.Icon, PackageManager.defaultActivityIcon) if the packageName of
         * the application cannot be obtained, Pair(IconType.Banner, banner) if the application has
         * a banner, or Pair(IconType.Icon, icon or PackageManager.defaultActivityIcon) if the
         * application doesn't have a banner.
         */
        fun getActivityIconPair(
            context: Context,
            packageName: String?,
            activityName: String?
        ): Pair<IconType, Drawable> {
            val pm = context.packageManager
            if (packageName.isNullOrEmpty()) {
                Log.e(
                    TAG,
                    "Cannot get type of activity icon and activity icon. The given packageName is null or empty."
                )
                return Pair(IconType.Icon, pm.defaultActivityIcon)
            }
            if (activityName.isNullOrEmpty()) {
                Log.e(
                    TAG,
                    "Cannot get type of activity icon and activity icon. The given activityName is null or empty."
                )
                return Pair(IconType.Icon, pm.defaultActivityIcon)
            }
            val banner = pm.getApplicationBanner(packageName)
            return if (banner != null) {
                Pair(IconType.Banner, banner)
            } else {
                Pair(IconType.Icon, getActivityIcon(context, packageName, activityName))
            }
        }

        fun getActivityName(resolveInfo: ResolveInfo?): String {
            return resolveInfo?.activityInfo?.name ?: ""
        }

        fun getActivityLabel(
            context: Context,
            packageName: String?,
            activityName: String?
        ): String {
            val pm = context.packageManager
            val activityInfo = getActivityInfo(context, packageName, activityName)
            return activityInfo?.loadLabel(pm)?.toString() ?: ""
        }

        fun getActivityLabel(context: Context, resolveInfo: ResolveInfo?): String {
            val pm = context.packageManager
            val activityInfo = resolveInfo?.activityInfo ?: return ""
            return activityInfo.loadLabel(pm).toString()
        }

        fun getVersionName(context: Context, packageName: String?): String? {
            return getPackageInfo(context, packageName)?.versionName
        }

        fun getLongVersionCode(context: Context, packageName: String?): Long? {
            val packageInfo = getPackageInfo(context, packageName)
            return if (packageInfo == null) {
                null
            } else {
                PackageInfoCompat.getLongVersionCode(packageInfo)
            }
        }

        fun getVersionCode(context: Context, packageName: String?): Int? {
            val longVersionCode = getLongVersionCode(context, packageName)
            return if (longVersionCode == null) {
                null
            } else {
                (longVersionCode and 0xFFFFFFFF).toInt()
            }
        }

        fun getVersionNameAndVersionCode(context: Context, packageName: String?): String {
            val versionName = getVersionName(context, packageName)
            val versionCode = getVersionCode(context, packageName)
            return if (versionName == null || versionCode == null) {
                ""
            } else {
                "$versionName ($versionCode)"
            }
        }

        /**
         * Get a list of ResolveInfo of launcher activity.
         *
         * @param packageName Specify which package should these activities belong to. Passing
         *                    null or empty string ("") to get all activities of all installed
         *                    packages.
         */
        fun getLauncherActivityList(
            context: Context,
            type: LauncherActivityType,
            packageName: String?
        ): List<ResolveInfo> {
            val pm: PackageManager = context.packageManager
            val intent: Intent = Intent(Intent.ACTION_MAIN).apply {
                when (type) {
                    LauncherActivityType.NORMAL -> {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }

                    LauncherActivityType.LEANBACK -> {
                        addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    }
                }

                if (!packageName.isNullOrEmpty()) {
                    setPackage(packageName)
                }
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }

        fun getLauncherActivity(
            context: Context,
            type: LauncherActivityType,
            packageName: String,
            activityName: String
        ): ResolveInfo? {
            val intentActivityList = getLauncherActivityList(context, type, packageName)
            return intentActivityList.find { resolveInfo ->
                activityName == getActivityName(resolveInfo)
            }
        }

        /**
         * Get a list of ActivityModel of all launchable activity of certain application(s).
         *
         * @param packageName Specify which package should these ActivityDtos belong to. Passing
         *                    null or empty string ("") to get all ActivityDtos of all installed
         *                    packages.
         */
        fun getActivityModelList(
            context: Context,
            type: LauncherActivityType,
            packageName: String?
        ): List<ActivityModel> {
            val intentActivityList = getLauncherActivityList(context, type, packageName)
            val activityModelList: MutableList<ActivityModel> = mutableListOf()
            intentActivityList.forEach { resolveInfo ->
                val activityModel = ActivityModel.fromResolveInfo(context, resolveInfo)
                activityModelList.add(activityModel)
            }
            return activityModelList
        }

        fun shouldShowBelongToHint(
            context: Context,
            packageName: String?,
            activityName: String?
        ): Boolean {
            if (packageName == null || activityName == null) {
                return false
            }
            if (packageName.isEmpty()) {
                return false
            }
            val applicationLabel = getApplicationLabel(context, packageName) ?: return false
            val activityLabel = getActivityLabel(context, packageName, activityName)
            return applicationLabel.compareTo(activityLabel) != 0
        }
    }
}