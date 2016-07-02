package io.vithor.sentry.raven

import android.util.Log
import com.google.gson.*
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Vithorio Polten on 2/25/16.
 */
class SentryEventBuilder() : Serializable {
    internal val event by lazy { createEventMap() }

    val user: JsonObject? = event["user"] as? JsonObject

    val tags: JsonObject? = event["tags"] as? JsonObject

    val extra: JsonObject? = event["extra"] as? JsonObject

    private fun createEventMap(): MutableMap<String, JsonElement?> {
        val map = mutableMapOf<String, JsonElement?>()
        putPrimitive(map, "event_id", UUID.randomUUID().toString().replace("-", ""))
        map.put("user", JsonObject())
        map.put("tags", JsonObject())
        map.put("extra", JsonObject())
        setPlatform(map, "java")
        setTimestamp(map, System.currentTimeMillis())
        return map
    }

    internal fun putPrimitive(key: String, string: String?) {
        putPrimitive(event, key, string)
    }

    internal fun putPrimitive(map: MutableMap<String, JsonElement?>, key: String, string: String?) {
        map.put(key, JsonPrimitive(string))
    }

    enum class SentryEventLevel(internal val value: String) {
        FATAL("fatal"),
        ERROR("error"),
        WARNING("warning"),
        INFO("info"),
        DEBUG("debug")
    }

    constructor(throwable: Throwable?, level: SentryEventBuilder.SentryEventLevel) : this() {

        val culprit = Sentry.getCause(throwable, throwable?.message ?: throwable?.cause?.message ?: "")

        this.setMessage(throwable?.message ?: throwable?.cause?.message ?: "")
                .setCulprit(culprit)
                .setLevel(level)
                .setException(throwable)
    }

    /**
     * "message": "SyntaxError: Wattttt!"

     * @param message Message
     * *
     * @return SentryEventBuilder
     */
    fun setMessage(message: String): SentryEventBuilder {
        putPrimitive("message", message)
        return this
    }

    /**
     * "timestamp": "2011-05-02T17:41:36"

     * @param timestamp Timestamp
     * *
     * @return SentryEventBuilder
     */
    fun setTimestamp(timestamp: Long): SentryEventBuilder {
        setTimestamp(event, timestamp)
        return this
    }

    internal fun setTimestamp(map: MutableMap<String, JsonElement?>, timestamp: Long): SentryEventBuilder {
        putPrimitive(map ,"timestamp", SentryEventBuilder.Companion.sdf.format(Date(timestamp)))
        return this
    }

    /**
     * "platform": "java"

     * @param platform Platform name String
     * *
     * @return SentryEventBuilder
     */
    fun setPlatform(platform: String): SentryEventBuilder {
        setPlatform(platform)
        return this
    }

    internal fun setPlatform(map: MutableMap<String, JsonElement?>, platform: String): SentryEventBuilder {
        putPrimitive(map, "platform", platform)
        return this
    }

    /**
     * "release": "your-app-version-code"

     * @param release Release String
     * *
     * @return SentryEventBuilder
     */
    internal fun setRelease(release: String?): SentryEventBuilder {
        if (release != null) {
            putPrimitive("release", release)
        }
        return this
    }

    /**
     * "level": "warning"

     * @param level Level
     * *
     * @return SentryEventBuilder
     */
    fun setLevel(level: SentryEventBuilder.SentryEventLevel): SentryEventBuilder {
        putPrimitive("level", level.value)
        return this
    }

    /**
     * "logger": "my.logger.name"

     * @param logger Logger
     * *
     * @return SentryEventBuilder
     */
    fun setLogger(logger: String): SentryEventBuilder {
        putPrimitive("logger", logger)
        return this
    }

    /**
     * "culprit": "my.module.function_name"

     * @param culprit Culprit
     * *
     * @return SentryEventBuilder
     */
    fun setCulprit(culprit: String): SentryEventBuilder {
        putPrimitive("culprit", culprit)
        return this
    }

//    fun putUserInfo(key: String, value: Any?): SentryEventBuilder {
//        user?.addProperty(key, gson.toJsonOrNull(value))
//        return this
//    }

    fun putUserInfo(key: String, value: String?): SentryEventBuilder {
        user?.addProperty(key, value)
        return this
    }

    fun putUserInfo(key: String, value: Number?): SentryEventBuilder {
        user?.addProperty(key, value)
        return this
    }

    fun putUserInfo(key: String, value: Boolean?): SentryEventBuilder {
        user?.addProperty(key, value)
        return this
    }

    fun putUserInfo(mapOf: Map<String, String?>): SentryEventBuilder {
        for ((key, value) in mapOf) {
            user?.addProperty(key, value)
        }
        return this
    }

    fun putTag(key: String, value: String?): SentryEventBuilder {
        tags?.addProperty(key, value)
        return this
    }

    fun putTag(key: String, value: Boolean?): SentryEventBuilder {
        tags?.addProperty(key, value)
        return this
    }

    fun putTag(key: String, value: Number?): SentryEventBuilder {
        tags?.addProperty(key, value)
        return this
    }

    fun putTags(mapOf: Map<String, String?>): SentryEventBuilder {
        for ((key, value) in mapOf) {
            tags?.addProperty(key, value)
        }
        return this
    }

    /**
     * @param serverName Server name
     * *
     * @return SentryEventBuilder
     */
    fun setServerName(serverName: String): SentryEventBuilder {
        putPrimitive("server_name", serverName)
        return this
    }

    /**
     * @param name    Name
     * *
     * @param version Version
     * *
     * @return SentryEventBuilder
     */
    fun addModule(name: String?, version: String?): SentryEventBuilder {
        val modules: JsonArray
        if (!event.containsKey("modules")) {
            modules = JsonArray()
            event.put("modules", modules)
        } else {
            modules = event["modules"] as JsonArray
        }

        if (name != null && version != null) {
            val module = arrayOf(name, version)
            modules.add(gson.toJsonOrNull(Arrays.asList(*module)))
        }

        return this
    }

    fun putExtra(key: String, value: String?): SentryEventBuilder {
        extra?.addProperty(key, value)
        return this
    }

    fun putExtra(key: String, value: Number?): SentryEventBuilder {
        extra?.addProperty(key, value)
        return this
    }

    fun putExtra(key: String, value: Boolean?): SentryEventBuilder {
        extra?.addProperty(key, value)
        return this
    }

    fun putExtras(mapOf: Map<String, String?>): SentryEventBuilder {
        for ((key, value) in mapOf) {
            extra?.addProperty(key, value)
        }
        return this
    }

    /**
     * @param t Throwable
     * *
     * @return SentryEventBuilder
     */
    fun setException(t: Throwable?): SentryEventBuilder {
        var t = t
        val values = JsonArray()

        while (t != null) {
            val exception = JsonObject()

            try {
                exception.addProperty("type", t.javaClass.simpleName)
                exception.addProperty("value", t.message)
                exception.addProperty("module", t.javaClass.`package`.name)
                exception.add("stacktrace", SentryEventBuilder.getStackTrace(t))

                values.add(exception)
            } catch (e: JsonParseException) {
                Log.e(Sentry.TAG, "Failed to build sentry report for " + t, e)
            } catch (e: JsonSyntaxException) {
                Log.e(Sentry.TAG, "Failed to build sentry report for " + t, e)
            }

            t = t.cause
        }

        val exceptionReport = JsonObject()

        try {
            exceptionReport.add("values", values)
            event.put("exception", exceptionReport)
        } catch (e: JsonParseException) {
            Log.e(Sentry.TAG, "Unable to attach exception to event " + values, e)
        } catch (e: JsonSyntaxException) {
            Log.e(Sentry.TAG, "Unable to attach exception to event " + values, e)
        }

        return this
    }

    internal fun build(): String? {
        return gson.toJsonOrNull(event)
    }

    companion object {
        val gson by lazy { Gson() }

        private val serialVersionUID = -8589756678369463988L

        private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

        init {
            sdf.timeZone = TimeZone.getTimeZone("GMT")
        }

        fun getStackTrace(t: Throwable): JsonObject {
            val frameList = JsonArray()

            for (traceElement in t.stackTrace.reversedArray()) {
                val frame = JsonObject()

                val method = traceElement.methodName
                if (method.length != 0) {
                    frame.addProperty("function", method)
                }

                val lineNo = traceElement.lineNumber
                if (!traceElement.isNativeMethod && lineNo >= 0) {
                    frame.addProperty("lineno", lineNo)
                }

                var inApp = true

                val className = traceElement.className
                frame.addProperty("module", className)

                // Take out some of the system packages to improve the exception folding on the sentry server
                if (className.startsWith("android.")
                        || className.startsWith("java.")
                        || className.startsWith("dalvik.")
                        || className.startsWith("com.android.")) {

                    inApp = false
                }

                frame.addProperty("in_app", inApp)

                frameList.add(frame)

                if (frameList.size() > 150) {
                    break
                }
            }

            val frameHash = JsonObject()
            frameHash.add("frames", frameList)

            return frameHash
        }
    }
}

private fun Gson.toJsonOrNull(value: Any?): String? {
    return this.toJson(value)
}
