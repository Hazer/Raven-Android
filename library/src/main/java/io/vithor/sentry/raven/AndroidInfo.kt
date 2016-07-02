package io.vithor.sentry.raven

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.view.WindowManager
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Created by Hazer on 3/5/16.
 */
object AndroidInfo {
    class Static(
            val osSDKVersion: Int = Build.VERSION.SDK_INT,
            val oSVersion: String = Build.VERSION.RELEASE,
            val deviceName: String = Build.MODEL,
            val deviceCode: String = Build.DEVICE,
            val brand: String = Build.BRAND,
            val board: String = Build.BOARD,
            val product: String = Build.PRODUCT) {

        val architecture: String by lazy { findABIS() }

        val map by lazy { createMap() }

        @Suppress("NewApi", "Deprecated", "DEPRECATION")
        private fun findABIS(): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return Build.SUPPORTED_ABIS.joinToString(separator = ", ")
            } else {
                return "ABI: [${Build.CPU_ABI}]\nABI2: [${Build.CPU_ABI2}]"
            }
        }

        private fun createMap(): Map<String, String?> {
            return mapOf(
                    "sdk version" to "${osSDKVersion}",
                    "os version" to oSVersion,
                    "device code" to deviceCode,
                    "device name" to deviceName,
                    "brand" to brand,
                    "board" to board,
                    "product" to product,
                    "architecture" to architecture
            )
        }
    }

    class Dynamic {

        private var processorCount: Int = 1

        private var currentOrientation: String = ""

        private var windowsBoundWidth: Int = 0
        private var windowsBoundHeight: Int = 0

        private var availablePhysicalMemory: Long? = null

        private var totalPhysicalMemory: Long? = null

        private var utcOffset: Long? = null

        private var locale: String = ""

        private var diskSpaceFree: Int? = null

        private var filesDir: String? = null

        val map by lazy { createMap() }

        constructor(context: Context?, withMemory: Boolean = false, withStorage: Boolean = false) {
            try {
                processorCount = Runtime.getRuntime().availableProcessors()

                context?.resources?.apply {
                    currentOrientation = when (configuration?.orientation) {
                        Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
                        Configuration.ORIENTATION_PORTRAIT -> "Portrait"
                        @Suppress("Deprecation") Configuration.ORIENTATION_SQUARE -> "Square"
                        else -> "Undefined"
                    }

                    locale = configuration?.locale.toString()
                }

                val wm = context?.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val d = wm.defaultDisplay
                windowsBoundWidth = d.width
                windowsBoundHeight = d.height

                val tz = TimeZone.getDefault()
                val now = Date()
                utcOffset = TimeUnit.SECONDS.convert(tz.getOffset(now.time).toLong(), TimeUnit.MILLISECONDS) / 3600

                if (withMemory) {
                    getMemoryInfo(context)
                }

                filesDir = context?.filesDir.toString()
                if (withStorage) {
                    getDiskSpace()
                }
            } catch (e: Exception) {
                Log.w("", "Couldn'throwable get all env data: " + e)
            }
        }

        private fun getMemoryInfo(context: Context?) {
            val mi = ActivityManager.MemoryInfo()
            val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.getMemoryInfo(mi)
            availablePhysicalMemory = mi.availMem / 0x100000

            val p = Pattern.compile("^\\D*(\\d*).*$")
            val m = p.matcher(getTotalRam())
            m.find()
            val match = m.group(1)
            totalPhysicalMemory = java.lang.Long.parseLong(match) / 0x400
        }

        private fun getDiskSpace() {
            val stat = StatFs(Environment.getDataDirectory().path)
            diskSpaceFree = stat.availableBlocks * stat.blockSize / 0x100000
        }

        @Throws(IOException::class)
        private fun getTotalRam(): String? {
            var reader: RandomAccessFile? = null
            val load: String?
            try {
                reader = RandomAccessFile("/proc/meminfo", "r")
                load = reader.readLine()
            } finally {
                reader?.close()
            }
            return load
        }

        private fun createMap(): Map<String, String?> {
            return mapOf(
                    "orientation" to currentOrientation,
                    "locale" to locale,
                    "filesDir" to filesDir,
                    "utc offset" to "${utcOffset}",
                    "windowsBoundWidth" to "${windowsBoundWidth}",
                    "windowsBoundHeight" to "${windowsBoundHeight}",
                    "processor count" to "${processorCount}",
                    "available physical memory" to "${availablePhysicalMemory}",
                    "total physical memory" to "${totalPhysicalMemory}"
            )
        }
    }
}