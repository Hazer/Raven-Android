package io.vithor.sentry.raven

import android.content.Context
import android.content.pm.PackageManager

/**
 * Created by Hazer on 3/5/16.
 */
object AndroidHelper {
    fun version(context: Context?): String? {
        return try {
            context?.packageManager?.getPackageInfo(context.packageName, 0)?.versionName
        } catch(ignore: PackageManager.NameNotFoundException) { null }
    }

    fun createStaticInfo() = AndroidInfo.Static()

    fun createDynamicInfo(context: Context?) = AndroidInfo.Dynamic(context, withMemory = false, withStorage = false)
}