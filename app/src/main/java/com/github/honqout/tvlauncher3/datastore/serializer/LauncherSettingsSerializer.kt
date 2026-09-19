package com.github.honqout.tvlauncher3.datastore.serializer

import android.util.Log
import androidx.datastore.core.Serializer
import com.github.honqout.tvlauncher3.LauncherSettings
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object LauncherSettingsSerializer : Serializer<LauncherSettings> {
    const val TAG: String = "LauncherSettingsSerializer"

    override val defaultValue: LauncherSettings = LauncherSettings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LauncherSettings {
        try {
            return LauncherSettings.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            Log.e(TAG, "Cannot read proto.", e)
            return defaultValue
        }
    }

    override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
        t.writeTo(output)
    }
}
