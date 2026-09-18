package com.github.honqout.tvlauncher3.data

import android.content.Context
import android.content.pm.ResolveInfo
import com.github.honqout.tvlauncher3.utils.ApplicationUtils
import com.github.honqout.tvlauncher3.utils.ApplicationUtils.Companion.IconType

data class ActivityModel(
    val packageName: String,
    val activityName: String,
    val label: String = "",
    val iconType: IconType = IconType.Icon
) {
    companion object {
        /**
         * Build a model from a launcher activity. Only the fields the list itself needs are
         * resolved here: the icon drawable (and its dominant colour) is fetched by the button that
         * actually shows the item, because doing it for every installed app up front is what used
         * to make the launcher start slowly.
         */
        fun fromResolveInfo(context: Context, resolveInfo: ResolveInfo): ActivityModel {
            val packageName = ApplicationUtils.getPackageName(resolveInfo)
            return ActivityModel(
                packageName = packageName,
                activityName = ApplicationUtils.getActivityName(resolveInfo),
                label = ApplicationUtils.getActivityLabel(context, resolveInfo),
                iconType = ApplicationUtils.getIconType(context, packageName)
            )
        }
    }

    fun getKey(): String {
        return "$packageName:$activityName"
    }
}
