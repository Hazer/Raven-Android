package io.vithor.sentry.raven

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Hazer on 2/25/16.
 */
class SentryEventBuilder() : Serializable {

    internal val event: MutableMap<String, Any>

    enum class SentryEventLevel private constructor(internal val value: String) {
        FATAL("fatal"),
        ERROR("error"),
        WARNING("warning"),
        INFO("info"),
        DEBUG("debug")
    }

    init {
        event = HashMap<String, Any>()
        event.put("event_id", UUID.randomUUID().toString().replace("-", ""))
        this.setTimestamp(System.currentTimeMillis())
    }

    constructor(t: Throwable, level: SentryEventBuilder.SentryEventLevel) : this() {

        val culprit = Sentry.getCause(t, t.message ?: t.cause?.message ?: "")

        this.setMessage(t.message ?: t.cause?.message ?: "")
                .setCulprit(culprit)
                .setLevel(level)
                .setException(t)
    }

    constructor(t: Throwable, level: SentryEventBuilder.SentryEventLevel, release: String) : this(t, level) {
        setRelease(release)
    }

    /**
     * "message": "SyntaxError: Wattttt!"

     * @param message Message
     * *
     * @return SentryEventBuilder
     */
    fun setMessage(message: String): SentryEventBuilder {
        event.put("message", message)
        return this
    }

    /**
     * "timestamp": "2011-05-02T17:41:36"

     * @param timestamp Timestamp
     * *
     * @return SentryEventBuilder
     */
    fun setTimestamp(timestamp: Long): SentryEventBuilder {
        event.put("timestamp", SentryEventBuilder.Companion.sdf.format(Date(timestamp)))
        return this
    }

    /**
     * "release": "some-code-sentry-needs"

     * @param release Release String
     * *
     * @return SentryEventBuilder
     */
    fun setRelease(release: String?): SentryEventBuilder {
        if (release != null) event.put("release", release)
        return this
    }

    /**
     * "level": "warning"

     * @param level Level
     * *
     * @return SentryEventBuilder
     */
    fun setLevel(level: SentryEventBuilder.SentryEventLevel): SentryEventBuilder {
        event.put("level", level.value)
        return this
    }

    /**
     * "logger": "my.logger.name"

     * @param logger Logger
     * *
     * @return SentryEventBuilder
     */
    fun setLogger(logger: String): SentryEventBuilder {
        event.put("logger", logger)
        return this
    }

    /**
     * "culprit": "my.module.function_name"

     * @param culprit Culprit
     * *
     * @return SentryEventBuilder
     */
    fun setCulprit(culprit: String): SentryEventBuilder {
        event.put("culprit", culprit)
        return this
    }

    /**
     * @param user User
     * *
     * @return SentryEventBuilder
     */
    fun setUser(user: Map<String, String>): SentryEventBuilder {
        setUser(JSONObject(user))
        return this
    }

    fun setUser(user: JSONObject): SentryEventBuilder {
        event.put("user", user)
        return this
    }

    val user: JSONObject
        get() {
            if (!event.containsKey("user")) {
                setTags(HashMap<String, String>())
            }

            return event["user"] as JSONObject
        }

    /**
     * @param tags Tags
     * *
     * @return SentryEventBuilder
     */
    fun setTags(tags: Map<String, String>): SentryEventBuilder {
        setTags(JSONObject(tags))
        return this
    }

    fun setTags(tags: JSONObject): SentryEventBuilder {
        event.put("tags", tags)
        return this
    }

    val tags: JSONObject
        get() {
            if (!event.containsKey("tags")) {
                setTags(HashMap<String, String>())
            }

            return event["tags"] as JSONObject
        }

    /**
     * @param serverName Server name
     * *
     * @return SentryEventBuilder
     */
    fun setServerName(serverName: String): SentryEventBuilder {
        event.put("server_name", serverName)
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
        val modules: JSONArray
        if (!event.containsKey("modules")) {
            modules = JSONArray()
            event.put("modules", modules)
        } else {
            modules = event["modules"] as JSONArray
        }

        if (name != null && version != null) {
            val module = arrayOf(name, version)
            modules.put(JSONArray(Arrays.asList(*module)))
        }

        return this
    }

    /**
     * @param extra Extra
     * *
     * @return SentryEventBuilder
     */
    fun setExtra(extra: Map<String, String>): SentryEventBuilder {
        setExtra(JSONObject(extra))
        return this
    }

    fun setExtra(extra: JSONObject): SentryEventBuilder {
        event.put("extra", extra)
        return this
    }

    val extra: JSONObject
        get() {
            if (!event.containsKey("extra")) {
                setExtra(HashMap<String, String>())
            }

            return event["extra"] as JSONObject
        }

    /**
     * @param t Throwable
     * *
     * @return SentryEventBuilder
     */
    fun setException(t: Throwable?): SentryEventBuilder {
        var t = t
        val values = JSONArray()

        while (t != null) {
            val exception = JSONObject()

            try {
                exception.put("type", t.javaClass.simpleName)
                exception.put("value", t.message)
                exception.put("module", t.javaClass.`package`.name)
                exception.put("stacktrace", SentryEventBuilder.getStackTrace(t))

                values.put(exception)
            } catch (e: JSONException) {
                Log.e(Sentry.TAG, "Failed to build sentry report for " + t, e)
            }

            t = t.cause
        }

        val exceptionReport = JSONObject()

        try {
            exceptionReport.put("values", values)
            event.put("exception", exceptionReport)
        } catch (e: JSONException) {
            Log.e(Sentry.TAG, "Unable to attach exception to event " + values, e)
        }

        return this
    }

    companion object {

        private val serialVersionUID = -8589756678369463988L

        private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

        init {
            sdf.timeZone = TimeZone.getTimeZone("GMT")
        }

        @Throws(JSONException::class)
        fun getStackTrace(t: Throwable): JSONObject {
            val frameList = JSONArray()

            for (ste in t.stackTrace) {
                val frame = JSONObject()

                val method = ste.methodName
                if (method.length != 0) {
                    frame.put("function", method)
                }

                val lineno = ste.lineNumber
                if (!ste.isNativeMethod && lineno >= 0) {
                    frame.put("lineno", lineno)
                }

                var inApp = true

                val className = ste.className
                frame.put("module", className)

                // Take out some of the system packages to improve the exception folding on the sentry server
                if (className.startsWith("android.")
                        || className.startsWith("java.")
                        || className.startsWith("dalvik.")
                        || className.startsWith("com.android.")) {

                    inApp = false
                }

                frame.put("in_app", inApp)

                frameList.put(frame)
            }

            val frameHash = JSONObject()
            frameHash.put("frames", frameList)

            return frameHash
        }
    }
}
